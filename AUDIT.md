# VulnSpace — Full Security & Code Audit Report

**Date:** 2026-09-19
**Audited commit:** `df28c24` (branch `arena/01a0ba12-vulnspace`)
**Scope:** Android app (Kotlin/Jetpack Compose, ~5,900 LOC), Supabase backend (6 SQL migrations, 4 Edge Functions), Gradle build config, repo hygiene
**Method:** Manual static review of every source file, RLS policy-by-policy analysis against the final migrated schema state, dependency advisory lookup, secret/config scanning. (Compilation not possible in this sandbox — no JDK/Android SDK present; see §6.)

---

## 1. Executive Summary

VulnSpace has a **good security foundation in intent**: RLS is enabled on every table, `SECURITY DEFINER` functions pin `search_path`, service-role keys are used only server-side, and the admin flows re-verify authorization server-side.

However, the audit found **4 high-severity and 6 medium-severity issues**, concentrated in three areas:

1. **RLS policies that can be abused for moderation bypass and queue forgery** (content self-publish, unconstrained application inserts).
2. **A completely broken invite/join enforcement chain** — zero policies on `invite_links`, no INSERT policy on `community_members`, a stub `join-community` Edge Function, and a role check in `manage-invite-link` that references a role value the migrations themselves renamed away.
3. **Credential and bootstrap weaknesses** — first platform admin is provisioned by matching a hard-coded personal Gmail address committed to the repo; temporary head passwords use `Math.random()` and are never actually delivered.

| Severity | Count |
|---|---|
| 🔴 Critical | 0 |
| 🟠 High | 4 |
| 🟡 Medium | 6 |
| 🔵 Low | 5 |
| ⚪ Informational | 4 |

---

## 2. High-Severity Findings

### H-1. Moderation pipeline bypass — members can self-publish content
**Location:** `supabase/migrations/202609191139_init.sql` (`member_edit_own_content` policy, L245)

```sql
create policy member_edit_own_content on public.content
for update using (submitted_by = auth.uid() and public.is_active_member(community_id))
with check (submitted_by = auth.uid() and public.is_active_member(community_id));
```

The USING/WITH CHECK clauses only verify authorship and membership — **no column restrictions**. Any authenticated member can use the public PostgREST endpoint to PATCH their own row and set `status = 'PUBLISHED'`, `safety_status = 'LOW_RISK'`, and `priority = 0`, bypassing the entire URL-safety / extraction pipeline (`PROCESSING` jobs, safety scoring). The content then becomes visible to the whole community via `published_content_read`.

**Impact:** arbitrary content injection into community feeds, e.g. phishing/malware links presented as vetted content.

**Remediation:** revoke direct client updates on `content`; route edits through an Edge Function that forces `status` back to `PROCESSING` on any field change, or add a trigger that resets `status`/`safety_status` when a non-head updates the row. At minimum, extend the WITH CHECK to forbid status transitions: `with check (status = 'PROCESSING' and submitted_by = auth.uid())`.

---

### H-2. Open anonymous insert on `head_applications` — forgery & spam
**Location:** `202609191700_allow_anon_head_applications.sql` → replaced by `202609191800` / `202609191900` (`head_app_insert` policy)

```sql
create policy head_app_insert on public.head_applications
for insert with check (
  length(trim(full_name)) > 0
  and length(trim(email)) > 3
  and length(trim(proposed_community_name)) > 0
);
```

The anon key ships with the app (see L-1), so anyone can insert **unlimited** rows — and the policy does not constrain any other column. An attacker can:

* Set `status = 'APPROVED'` / `reviewed_by` / `rejection_reason` directly — forged, already-"resolved" applications (the Edge Function short-circuits on `status = 'APPROVED'`, which also makes these rows immune to later rejection).
* Set `applicant_user_id` to arbitrary UUIDs, attributing applications to other users.
* Flood the admin review queue (no rate limiting, no CAPTCHA/honeypot).

**Remediation:** extend the check to `status = 'PENDING' and reviewed_by is null and rejection_reason is null`, or better, move submission behind an Edge Function protected by a CAPTCHA (Supabase supports Turnstile/hCaptcha) with per-IP throttling.

---

### H-3. Platform-admin bootstrap keyed to a hard-coded personal email
**Location:** `202609191600_fix_head_applications_permissions.sql` (§6)

```sql
insert into public.platform_admins (user_id)
select id from auth.users where lower(email) = 'hariganesh260@gmail.com'
on conflict (user_id) do nothing;
```

Two problems:

1. **Provisioning by email match.** If email confirmation is disabled (or misconfigured) in the Supabase Auth settings, *anyone* who signs up with that address is silently granted platform admin — full read/write over applications, communities, audit logs.
2. **PII / recon data committed to a public repo.** The owner's personal email is permanently in git history, advertising exactly which account to target (credential stuffing, phishing).

**Remediation:** provision the first admin by pasting the auth UUID in the SQL editor exactly once (the pattern already documented in `202609191300`), delete the statement from the migration, and scrub the email from history (or accept it as burned and rotate the account). Ensure `Confirm email` is enabled in Supabase Auth settings.

---

### H-4. Invite/join authorization chain is broken end-to-end (and unsafe once "fixed")
**Locations:**
* `202609191139_init.sql` — `invite_links`: RLS enabled, **zero policies** → every client read/write denied.
* `community_members`: **no INSERT/UPDATE/DELETE policies** → `UsernameSetupViewModel` direct insert always fails.
* `supabase/functions/join-community/index.ts` — **stub**: returns "Successfully joined community" without doing anything.
* `supabase/functions/manage-invite-link/index.ts` — head check filters `.eq('role', 'HEAD')`, but migration `202609191800` converted all roles to `COMMUNITY_HEAD` → **every legitimate head gets 403**.
* Schema drift: app + function reference `invite_links.status` and `used_count`; the table has `revoked_at`, `uses`, `max_uses` instead. `HeadConsoleViewModel` inserts without the NOT NULL `created_by` column.

**Impact:** no working invite flow today (fail-closed — good), but any patch that "makes it work" client-side will land on a design where **nothing server-side enforces** expiry, `max_uses`, revocation, or single-session binding — invite codes become bearer tokens anyone can replay or enumerate.

**Remediation (single coherent design):**
1. Keep `invite_links` and `community_members` write policies closed to clients.
2. Implement `join-community` as a service-role Edge Function that: validates the caller's JWT, looks up the active invite (not revoked, `expires_at > now()`, `uses < max_uses`), inserts the member row, and atomically increments `uses` inside one transaction.
3. Fix `manage-invite-link` to accept `COMMUNITY_HEAD` (or call `public.is_community_head()`), and align column names with the schema (`uses`, not `used_count`; drop `status`, use `revoked_at`).
4. Store a real hash of the invite token (the column is already named `token_hash`) — see M-5.

---

## 3. Medium-Severity Findings

### M-1. Weak, undelivered temporary passwords for approved heads
**Location:** `supabase/functions/manage-head-application/index.ts`

```ts
const tempPassword = `VulnHead!${Math.random().toString(36).slice(-8)}#`
```

* `Math.random()` is not cryptographically secure — the fixed prefix/suffix leave ~8 base36 chars of predictable entropy.
* **The password is never sent anywhere.** The approval email contains no credentials and no reset link ("You will be prompted to set up your password during your first login" — but there is no password to log in with). Approved heads cannot sign in at all (functional outage of the approval flow).
* `listUsers()` to find an existing user by email only scans the first page (default page size) — duplicate-account errors at scale.

**Remediation:** replace with the Supabase-native flow — `auth.admin.inviteUserByEmail()` (or `generateLink` with `type: 'recovery'`) — so credentials never pass through your code; if a temp password is unavoidable, use `crypto.getRandomValues` and deliver it out-of-band.

### M-2. Table GRANTs far exceed the policy model
**Location:** `202609191600_fix_head_applications_permissions.sql` (§3)

```sql
grant select, insert, update, delete on table public.platform_admins to authenticated;
grant select, insert, update on table public.head_applications to authenticated;
grant select, update on table public.communities to authenticated;
```

RLS currently blocks the writes because no write policies exist — but this is fragile. The moment anyone adds a permissive policy or toggles RLS for debugging, the excess grants turn it into instant privilege escalation (e.g. self-insert into `platform_admins`). **Remediation:** `revoke insert, update, delete on platform_admins from authenticated; revoke update on head_applications from authenticated;` etc. Grant only what policies actually permit.

### M-3. Vulnerable Ktor client version
**Location:** `app/build.gradle.kts` — `io.ktor:ktor-client-android:2.3.11`

`ktor-client-core` versions **before 2.3.13** are affected by a Medium "Use of Cache Containing Sensitive Information" issue (Snyk). **Remediation:** bump to `2.3.13+` (or the 3.x line after testing). All other direct dependencies (supabase-kt BOM 2.5.0, Compose BOM 2024.06.00, navigation 2.7.7, coroutines 1.8.1) have no known advisories at audit time.

### M-4. Realtime publications on PII/sensitive tables
**Location:** `202609191800` / `202609191900` — `head_applications`, `communities`, `audit_logs` added to `supabase_realtime`.

`head_applications` contains applicant names, emails, and phone numbers. RLS-respecting Realtime requires a current Supabase Realtime with authorization enabled; older/misconfigured deployments broadcast changes to any subscriber regardless of SELECT policies. **Remediation:** verify the project's Realtime version enforces RLS (or use private Broadcast channels); drop `head_applications` from the publication — polling on admin action already refreshes the dashboard.

### M-5. Invite tokens stored in plaintext; generated with `Math.random()`
**Location:** `manage-invite-link/index.ts` (`token_hash: code // ...raw makes UI easier`), 8 chars from a 32-symbol alphabet via non-CSPRNG.

~40 bits of entropy from a predictable PRNG, stored raw — any DB read (or future over-permissive policy) discloses live invite codes. **Remediation:** generate with `crypto.getRandomValues` (12+ chars), store `sha256(token)`, return the plaintext only at creation time.

### M-6. Verbose error leakage to clients
**Locations:** all 4 Edge Functions return raw `(error as Error).message` (PostgREST/DB internals, provider responses); `SignInViewModel`, `UsernameSetupViewModel`, `CommunitySetupViewModel`, `CommunityHeadLoginViewModel` surface raw `e.message` in the UI — inconsistent with the (good) sanitizer in `AdminViewModel`.

**Remediation:** map errors to stable, generic messages at the trust boundary; log details server-side only. Reuse the `sanitizeErrorMessage` pattern app-wide.

---

## 4. Low-Severity Findings

| # | Finding | Location | Note |
|---|---|---|---|
| L-1 | Supabase URL + anon JWT hard-coded in source | `SupabaseClient.kt` | Anon keys are public by design, but with a public repo the H-2 insert surface is one `curl` away. Move to `BuildConfig`/gradle properties; the real fix is H-2/H-3 hardening. |
| L-2 | Release hardening off | `app/build.gradle.kts` | `isMinifyEnabled = false`; `proguard-rules.pro` referenced but **missing** — will fail the build the day minify is turned on. Enable R8 + resource shrinking. |
| L-3 | Sensitive values in logs | `AdminViewModel.kt` (`Log.d("...UUID: $currentUserId")`), several `printStackTrace()` | Not gated behind `BuildConfig.DEBUG`. Strip or guard. |
| L-4 | `android:allowBackup="true"` | `AndroidManifest.xml` | Auth tokens/cached data included in device backups. Add `dataExtractionRules`/`fullBackupContent` excluding auth storage. |
| L-5 | No abuse controls on public endpoints | app-wide | Sign-in attempts and anonymous application inserts rely solely on Supabase defaults; add CAPTCHA + Auth rate-limit tuning. |

---

## 5. Informational / Hygiene

* **I-1 — Pervasive app↔schema drift** (beyond H-4): member `status` — app filters `SUSPENDED`, enum is `('ACTIVE','REMOVED')`; `Content` statuses (`SUBMITTED/FAILED/BLOCKED`) vs DB enum (`PROCESSING/PUBLISHED/PROCESSING_FAILED/...`); `profiles.must_change_password` added by *later* migrations while code assumes it. Single source of truth needed (generate types from the DB).
* **I-2 — Stub functions return success**: `join-community` and `submit-content-url` are TODO shells that respond 200 as if work happened — dangerous placeholder semantics if any client relies on them. All Edge Functions carry `@ts-nocheck`.
* **I-3 — Edge Function bookkeeping**: `manage-head-application` REJECT writes `approved_by/approved_at` instead of `reviewed_by/reviewed_at`; `CORS: *` on an admin endpoint (tolerable for mobile, note for web); duplicated RLS policy names across repeated migrations make the effective policy set hard to reason about — consolidate.
* **I-4 — No tests / no CI**; `minSdk 24`, `targetSdk 34`, no exported components beyond the launcher activity, no WebViews/deep-links — attack surface otherwise minimal. `.gitignore` correctly excludes keystores, `.env`, `google-services.json`.

---

## 6. Dependency & Config Audit

| Component | Version | Status |
|---|---|---|
| ktor-client-android | 2.3.11 | 🟡 Known Medium issue, fix in ≥ 2.3.13 (M-3) |
| supabase-kt BOM (postgrest/gotrue/realtime/functions) | 2.5.0 | ✅ No known advisories |
| compose-bom | 2024.06.00 | ✅ |
| navigation-compose | 2.7.7 | ✅ |
| kotlinx-coroutines | 1.8.1 | ✅ |
| kotlinx-serialization-json | 1.6.3 | ✅ |
| AGP / Kotlin | 8.5.1 / 1.9.24 | ✅ current stable line |

**Config:** manifest permissions are minimal (`INTERNET`, `ACCESS_NETWORK_STATE`); cleartext traffic defaults to off at targetSdk 34 (though the submit-URL flow accepts `http://` user input as data). Gradle wrapper files present and standard.

**Build verification:** Not executable here (no JDK/Android SDK in sandbox). Recommend CI running `./gradlew assembleDebug lint test` + Supabase `pgTAP` policy tests.

---

## 7. What's Done Well ✅

* RLS **enabled on every table** — no publicly exposed table found.
* `SECURITY DEFINER` helpers all pin `set search_path = public` (no search-path hijack).
* Service-role key never shipped to the app; admin privilege re-verified **inside** the Edge Function, not trusted from the client.
* Admin login signs the session out immediately if the account lacks the admin role; password state cleared from UI state after use.
* `AdminViewModel.sanitizeErrorMessage` deliberately strips tokens/URLs from displayed errors.
* Approve/reject flow is idempotent and guarded against double-resolution; audit-log and notification inserts are non-blocking and failure-tolerant.
* Repo hygiene: no keystores, `.env`, or Google service files committed.

---

## 8. Prioritized Remediation Plan

| Priority | Action | Addresses |
|---|---|---|
| P0 | Constrain `content` update policy / route edits via Edge Function | H-1 |
| P0 | Lock `head_app_insert` to PENDING-only fields; add CAPTCHA + rate limit via Edge Function | H-2, L-5 |
| P0 | Remove email-based admin bootstrap; provision by UUID; confirm email verification is ON | H-3 |
| P0 | Implement `join-community` for real; fix role names + schema drift in invite flow | H-4, I-1 |
| P1 | Switch head onboarding to `inviteUserByEmail`/recovery-link flow | M-1 |
| P1 | Revoke surplus table GRANTs to `authenticated` | M-2 |
| P1 | Bump Ktor to ≥ 2.3.13 | M-3 |
| P2 | Verify Realtime RLS enforcement or unpublish `head_applications` | M-4 |
| P2 | Hash invite tokens; CSPRNG generation | M-5 |
| P2 | Uniform error sanitization; strip debug logs; enable R8; add backup rules | M-6, L-2..L-4 |
| P3 | Consolidate migrations, remove `@ts-nocheck`/stubs, add CI with policy tests | I-2..I-4 |

---

*Report generated by static audit. Findings describe the state at commit `df28c24`; re-audit after remediation.*
