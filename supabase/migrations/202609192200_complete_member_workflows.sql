-- Migration: Complete member workflows (feed, bookmarks, notifications, reports)
-- Run this in your Supabase SQL Editor AFTER 202609192100_fix_head_and_invite_workflows.sql
--
--   1. Grants for the member-facing tables the app now reads/writes directly
--      (RLS policies already exist for these — grants were the missing half).
--   2. Notifications: recipients may mark their own alerts as read.
--   3. Drop event_dates_check: real-world event pages rarely expose structured
--      dates, and the extraction pipeline publishes best-effort data instead
--      of rejecting the submission outright.
--   4. Heads can read ALL content in their community for moderation
--      (members keep the published-only policy).

-- =============================================================================
-- 1. Table grants (RLS still governs row access)
-- =============================================================================

grant select, insert, update on table public.content to authenticated;
grant select, insert, delete on table public.bookmarks to authenticated;
grant insert on table public.reports to authenticated;
grant update on table public.notifications to authenticated;

grant all on table public.content to service_role;
grant all on table public.processing_jobs to service_role;
grant all on table public.bookmarks to service_role;
grant all on table public.reports to service_role;
grant all on table public.invite_links to service_role;

-- =============================================================================
-- 2. Notifications: recipients can mark their own alerts as read
-- =============================================================================

drop policy if exists notifications_mark_own_read on public.notifications;
create policy notifications_mark_own_read
on public.notifications
for update
to authenticated
using (recipient_user_id is not null and recipient_user_id = auth.uid())
with check (recipient_user_id is not null and recipient_user_id = auth.uid());

-- =============================================================================
-- 3. Relax the event-dates constraint (enforced later inside the pipeline)
-- =============================================================================

alter table public.content drop constraint if exists event_dates_check;

-- =============================================================================
-- 4. Head moderation read over all community content
-- =============================================================================

drop policy if exists head_read_all_content on public.content;
create policy head_read_all_content
on public.content
for select
to authenticated
using (public.is_community_head(community_id));
