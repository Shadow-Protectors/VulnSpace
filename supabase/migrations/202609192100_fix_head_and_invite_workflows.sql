-- Migration: Fix head-approval credential flow support + invite-link enforcement
-- Run this in your Supabase SQL Editor AFTER the earlier migrations.
--
-- What this fixes:
--   1. invite_links had RLS enabled but ZERO policies -> the Head Console invite
--      code page could never list codes. Heads now get read access to their own
--      community's invites (writes stay Edge-Function-only via service role).
--   2. New consume_invite() helper lets join-community increment usage
--      atomically while enforcing revocation, expiry and max_uses in one UPDATE.

-- =============================================================================
-- 1. invite_links: head-scoped read, client writes stay closed
-- =============================================================================

alter table public.invite_links enable row level security;

grant select on table public.invite_links to authenticated;
-- Belt-and-braces: anons must never read invite codes
revoke select on table public.invite_links from anon;

drop policy if exists head_read_community_invites on public.invite_links;
create policy head_read_community_invites
on public.invite_links
for select
to authenticated
using (public.is_community_head(community_id));

-- No INSERT/UPDATE/DELETE policies on purpose: invites are created, revoked and
-- consumed exclusively by Edge Functions holding the service role key.

-- =============================================================================
-- 2. Atomic invite consumption (revocation + expiry + max_uses enforced)
-- =============================================================================

create or replace function public.consume_invite(p_invite_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.invite_links
  set uses = uses + 1
  where id = p_invite_id
    and revoked_at is null
    and (expires_at is null or expires_at > now())
    and (max_uses is null or uses < max_uses);
  return found; -- false -> invite already invalid/expired/exhausted
end;
$$;

revoke all on function public.consume_invite(uuid) from public, anon, authenticated;
grant execute on function public.consume_invite(uuid) to service_role;
