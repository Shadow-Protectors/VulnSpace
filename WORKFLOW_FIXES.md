# Workflow Fixes — What Was Broken, What Changed, How to Deploy & Verify

**Date:** 2026-09-19
**Scope:** Admin approval of head applications · Community Head first login · Invite code pages (head console + member join)

---

## 1. Why "Approve" showed *Retry*

The Retry button is `DashboardState.Error` fed by `AdminViewModel`. Two layers were hiding the real cause:

1. The app never inspected the Edge Function's HTTP status — a 4xx could be treated as success, or the exception message got swallowed.
2. The error sanitizer blacklisted any message containing the word **"token"**, so even the server's *curated* message ("Invalid or expired session...") was replaced with the generic "Approval failed. Please try again."

**And on the server**, the approval flow itself had a dead-end (see §2), so even a "successful" approval left a head who could never log in.

### Fixes applied
- `AdminViewModel.kt`
  - `approveApplication` / `rejectApplication` now explicitly check `response.status` and throw with the **server's real error message** on non-2xx.
  - `sanitizeErrorMessage` now only hides genuine secrets (Bearer material, JWTs, URLs) and lets the Edge Function's curated `{"error": "..."}` messages through — so when something fails you'll see *why*, not just "Retry".
  - On approval, if the email provider isn't configured, the function returns the applicant's **one-time password to the admin**, and the app shows it in the action banner for manual handover.
- `manage-head-application/index.ts` — see §2.

---

## 2. The serious error between approval and head login

The approval flow had **two independent credential dead-ends**:

1. **Anonymous-applicant trap.** `HeadApplicationViewModel` signs the applicant in *anonymously* before inserting the application, so `applicant_user_id` pointed to a **passwordless anonymous auth account**. On approval, everything (profile, community, head role) was bound to that account — which has no email/password. There was literally **no credential to log in with**.
2. **The OTP was never delivered.** The function generated `tempPassword` but the approval email contained **no password and no link** — just "you will be prompted to set up your password at first login," which is impossible without the first password.

A third, quieter bug: `profiles.username` is `UNIQUE`, and the function upserted the applicant's **full name** as the username. Any name collision silently killed the profile row → `must_change_password` was never set → no forced password change.

### Fixes applied (`manage-head-application/index.ts`, rewritten)
- **Anonymous applicant** → their account is *converted in place* (`admin.updateUserById`) to a permanent account with the applicant's email + a one-time password. Roles/application stay linked to the same uid.
- **Existing real account** → password untouched; the email tells them to sign in normally.
- **No account** → created with a one-time password.
- OTP now generated with `crypto.getRandomValues` (not `Math.random`).
- **The approval email now contains the one-time password** with clear first-login instructions (your `COMMUNITY_HEAD_ONE_TIME_PASSWORD` design).
- Profile username is generated unique (`name-xxxx`) so `must_change_password` is reliably set → first login lands on **Create New Password** and then into the head area.
- `listUsers()` loop now paginates (old code only scanned page 1).
- REJECT now writes `reviewed_by` / `reviewed_at` (it previously stamped `approved_by`).

---

## 3. The invite code pages (both sides were broken)

**Head console side:**
- The nav graph wired `InviteCodeManagementScreen` with **hard-coded** `emptyList()` and no-op handlers — the `HeadConsoleViewModel` was never used. The page could do nothing no matter what.
- Even if wired: `invite_links` had RLS enabled with **zero policies** (all reads/writes denied), the app used a non-existent `status` column, a `used_count` field the schema calls `uses`, and omitted the NOT NULL `created_by` column.
- The `manage-invite-link` Edge Function accepted only the legacy role `'HEAD'`, which migration `202609191800` itself renamed to `COMMUNITY_HEAD` → guaranteed 403.

**Member join side (`UsernameSetupViewModel`):**
- Did a direct `invite_links` select (denied by RLS, filtered on the non-existent `status`) → always "Invalid or expired invite code", then a direct `community_members` insert (no INSERT policy → permission denied).
- The `join-community` Edge Function was an unimplemented stub returning fake success.

### Fixes applied
- **New migration** `supabase/migrations/202609192100_fix_head_and_invite_workflows.sql`
  - `head_read_community_invites` RLS policy: heads can SELECT their own community's invites (writes stay Edge-Function-only).
  - `consume_invite(uuid)` SQL function: atomically increments `uses` while re-checking revocation/expiry/max_uses (race-safe), executable only by `service_role`.
- `manage-invite-link/index.ts`: accepts `COMMUNITY_HEAD` + legacy `HEAD`, uses the real schema (`revoked_at`, `uses`, `max_uses`), CSPRNG code generation, CORS + 401/403 handling.
- `join-community/index.ts`: **fully implemented** — validates JWT, enforces code validity/expiry/uses server-side, idempotent re-join, friendly 409 on duplicate username, atomic use consumption. This is now the *only* member-join path.
- App:
  - `InviteLink` model aligned to the real schema (`uses`, `revoked_at`, computed `isActive`).
  - `HeadConsoleViewModel` calls `manage-invite-link` for create/revoke and surfaces errors in UI state.
  - Nav graph now wires the real ViewModel into `InviteCodeManagementScreen` (with an error banner).
  - `UsernameSetupViewModel` calls `join-community` — no direct table access.
  - Bonus fix: the member-area **Apply as Head** form was a dead local placeholder; it now uses the real `HeadApplicationViewModel`.

---

## 4. Deploy steps (required — nothing works until these run)

```bash
# A. Database: run BOTH new migrations in order
#    Supabase Dashboard → SQL Editor → paste & run each:
#    1) supabase/migrations/202609192100_fix_head_and_invite_workflows.sql
#    2) supabase/migrations/202609192200_complete_member_workflows.sql

# B. Redeploy the Edge Functions (from the repo root, with the Supabase CLI linked)
supabase functions deploy manage-head-application
supabase functions deploy manage-invite-link
supabase functions deploy join-community
supabase functions deploy submit-content-url manage-member
```

**Dashboard settings:**
1. **Authentication → Providers → enable "Anonymous Sign-ins"** (needed for both head applications and invite joins).
2. **Authentication → Email → make sure "Confirm email" is ON**.
3. **Edge Functions → Secrets**: set `RESEND_API_KEY` if you want approval emails delivered. Without it, approval still works — the admin banner shows the one-time password to hand over manually.
4. Rebuild the app after pulling these changes.

---

## 5. End-to-end verification script

| # | Flow | Steps | Expected |
|---|---|---|---|
| 1 | Admin approve | Sign in as platform admin → Head Applications → **Approve** | Green success banner. If email isn't configured, banner shows the applicant's one-time password. If it fails, the banner shows the **real** server error — not a bare "Retry". |
| 2 | Head first login | Community Head Login → email + one-time password | Immediately routed to **Create New Password** → set new password → lands in Home feed with the **Console** tab. |
| 3 | Head invite codes | Console → Invite Codes → **+** | New 8-char code appears in the list (Active). Share → copies code. Revoke → chip flips to archived, Share/Revoke buttons hide. |
| 4 | Member join | Fresh install → Join Community → enter code → pick username → Continue | Lands directly in the member Home feed. Same code again → friendly "already a member" path. Wrong/revoked code → "Invalid, expired or exhausted invite code." Duplicate username → "already taken" message. |
| 5 | Apply as head (member) | Profile → Apply as Head → submit | Success toast/screen and row visible in the admin's pending list. |

---

## 6. Part 2 — Full member workflow (feed → submit → bookmarks → alerts → members)

Everything below was placeholder/simulation before; it is now wired end-to-end:

| Feature | Before | Now |
|---|---|---|
| **Home feed** | 800 ms fake delay, always empty | Real query of `content` (status `PUBLISHED`, RLS-scoped to your community), priority-ordered, bookmark flags merged, client-side search + category/type filters that previously did nothing |
| **Submit URL** | Simulated stepper, TODO | Calls the fully-implemented `submit-content-url` function: auth + membership check → URL validation → duplicate guard (409) → server-side page fetch & meta extraction (title/description/site name) → safety verdict (scheme + blocklist) → publishes to the feed; failures land on the FAILED/BLOCKED stepper states with the server message |
| **Content detail** | `content = null` always | `ContentDetailViewModel` loads the real card, bookmark toggle persists to `bookmarks`, report inserts into `reports` |
| **Bookmarks** | `emptyList()` | `BookmarksViewModel` joins your bookmarks → content, search/type filters, optimistic remove with rollback |
| **Notifications** | `emptyList()` | `NotificationsViewModel` loads RLS-visible alerts newest-first, first open marks your unread alerts read (new `notifications_mark_own_read` policy) |
| **Member management** | Never wired | `MemberManagementViewModel` lists ACTIVE members (heads first), search, removal via the new `manage-member` Edge Function (self/head removal blocked, soft-delete to `REMOVED`, audit-logged) — removed users immediately lose access via the session resolver's new ACTIVE filter |
| **Profile / Head Console** | "My Community" hard-coded | Real community name flows from the feed load |
| **Content model** | camelCase fields that never matched snake_case columns — every decode silently returned defaults | `@SerialName` mappings aligned to `public.content`; `mode` derived from `is_online`; `daysLeft` computed API-24-safely |

**Deploy for Part 2:** run migration `202609192200_complete_member_workflows.sql` and deploy `submit-content-url` + `manage-member` (commands in §4).

**Extra verification steps:**

| # | Flow | Expected |
|---|---|---|
| 6 | Member submits a real public link | Stepper runs → PUBLISHED (or NEEDS_REVIEW for plain-http links) → card appears in Home feed with extracted title |
| 7 | Feed search/category chips | Actually filter the list now |
| 8 | Tap card → bookmark → Saved tab | Detail opens, bookmark persists, Saved lists it, remove works |
| 9 | Head → Console → Members | Member list loads; removing a member kicks them out on their next app open (session re-resolve) |
| 10 | Alerts tab | Approval/community notifications appear; unread dot clears after first view |

## 7. Known remaining gaps (not part of this fix)

- Invite codes are stored in plaintext in `token_hash` (needed for the console's share-after-creation UX); hashing them is a future hardening step that requires showing the code only at creation time.
- The safety check in `submit-content-url` is heuristic (scheme + a tiny blocklist), not a real threat feed — flagged as future work; NEEDS_REVIEW items are published but visibly badged.
- Head content moderation actions (archive/remove from the console) still land on the shared feed view — the RLS policies (`head_manage_content`, `head_remove_content`) already permit them when a dedicated screen is built.

## 8. Part 3 — Reliable approval completion and Community Head notification

An approval now has an explicit completion path instead of silently removing the request from the pending list:

- The admin confirms **Approve & Notify** before the action runs.
- The server creates the community, assigns the Community Head membership, records the created `community_id` on the application, and marks the approval complete.
- Migration `202609192300_reliable_head_approval_notifications.sql` adds a database trigger. Whenever an application transitions to `APPROVED`, it creates exactly one durable **Community approved** alert for the applicant. This is independent of email delivery, so a temporary email-provider failure cannot make the approval disappear.
- The admin sees a completion card with the community name, in-app alert status, email status, one-time password when required, and a **View Community** next step.
- Retrying an already-completed approval returns the same completed result rather than reporting a confusing failure.

**Deploy this part:**

```bash
# 1. Run this migration in the Supabase SQL Editor
#    supabase/migrations/202609192300_reliable_head_approval_notifications.sql

# 2. Deploy the updated approval function
supabase functions deploy manage-head-application
```

**Verify:** submit a head application, sign in as a platform admin, open **Head Applications**, approve it, and confirm the completion card appears. Sign in as the approved Community Head; after changing the one-time password if prompted, open **Alerts** to see **Community approved**. If `RESEND_API_KEY` is configured, the same sign-in instructions are emailed; otherwise the admin safely receives the one-time password for manual delivery.
