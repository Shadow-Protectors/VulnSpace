-- Migration: Make community-head approval completion and notification durable.
--
-- Apply after 202609192200_complete_member_workflows.sql and deploy the matching
-- manage-head-application Edge Function. An approval is no longer only an Edge
-- Function side effect: the database records the created community and creates
-- the Community Head's in-app notification when the application becomes APPROVED.

-- =============================================================================
-- 1. Approval completion metadata
-- =============================================================================

alter table public.head_applications
  add column if not exists community_id uuid references public.communities(id) on delete set null,
  add column if not exists approval_completed_at timestamptz,
  add column if not exists approval_notification_created_at timestamptz,
  add column if not exists reviewed_by uuid references auth.users(id),
  add column if not exists reviewed_at timestamptz,
  add column if not exists approved_by uuid references auth.users(id),
  add column if not exists approved_at timestamptz;

create index if not exists head_applications_community_id_idx
  on public.head_applications (community_id)
  where community_id is not null;

-- Existing deployments may contain duplicate best-effort alerts from earlier
-- versions of the Edge Function. Keep the earliest one before enforcing one
-- decision alert per application and decision type.
with ranked_decision_notifications as (
  select
    id,
    row_number() over (
      partition by application_id, type
      order by created_at asc, id asc
    ) as row_number
  from public.notifications
  where application_id is not null
    and type in ('HEAD_APPLICATION_APPROVED', 'HEAD_APPLICATION_REJECTED')
)
delete from public.notifications n
using ranked_decision_notifications r
where n.id = r.id
  and r.row_number > 1;

create unique index if not exists notifications_one_head_application_decision_idx
  on public.notifications (application_id, type)
  where application_id is not null
    and type in ('HEAD_APPLICATION_APPROVED', 'HEAD_APPLICATION_REJECTED');

-- =============================================================================
-- 2. Database-owned decision notification
-- =============================================================================

create or replace function public.notify_head_application_decision()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  -- There is nobody to notify for legacy/imported applications that were not
  -- linked to an auth account. The Edge Function always links new approvals.
  if new.applicant_user_id is null or old.status is not distinct from new.status then
    return new;
  end if;

  if new.status = 'APPROVED' then
    insert into public.notifications (
      recipient_user_id,
      application_id,
      community_id,
      title,
      body,
      type
    ) values (
      new.applicant_user_id,
      new.id,
      new.community_id,
      'Community approved',
      format(
        'Your community ''%s'' is ready. Sign in as Community Head to continue.',
        new.proposed_community_name
      ),
      'HEAD_APPLICATION_APPROVED'
    ) on conflict do nothing;

    update public.head_applications
    set approval_notification_created_at = coalesce(approval_notification_created_at, now())
    where id = new.id;

  elsif new.status = 'REJECTED' then
    insert into public.notifications (
      recipient_user_id,
      application_id,
      title,
      body,
      type
    ) values (
      new.applicant_user_id,
      new.id,
      'Community application update',
      format(
        'Your community application was not approved: %s',
        coalesce(nullif(new.rejection_reason, ''), 'Does not meet community guidelines')
      ),
      'HEAD_APPLICATION_REJECTED'
    ) on conflict do nothing;
  end if;

  return new;
end;
$$;

revoke all on function public.notify_head_application_decision() from public, anon, authenticated;

drop trigger if exists head_application_decision_notification on public.head_applications;
create trigger head_application_decision_notification
after update of status on public.head_applications
for each row
when (old.status is distinct from new.status)
execute function public.notify_head_application_decision();

-- Backfill an alert for heads whose applications were approved by an earlier
-- function version. This only fills missing decision alerts; it never creates a
-- second alert for an application that is already represented in the inbox.
insert into public.notifications (
  recipient_user_id,
  application_id,
  community_id,
  title,
  body,
  type
)
select
  h.applicant_user_id,
  h.id,
  h.community_id,
  'Community approved',
  format(
    'Your community ''%s'' is ready. Sign in as Community Head to continue.',
    h.proposed_community_name
  ),
  'HEAD_APPLICATION_APPROVED'
from public.head_applications h
where h.status = 'APPROVED'
  and h.applicant_user_id is not null
  and not exists (
    select 1
    from public.notifications n
    where n.application_id = h.id
      and n.type = 'HEAD_APPLICATION_APPROVED'
  )
on conflict do nothing;

update public.head_applications h
set approval_notification_created_at = coalesce(h.approval_notification_created_at, now())
where h.status = 'APPROVED'
  and h.applicant_user_id is not null
  and exists (
    select 1
    from public.notifications n
    where n.application_id = h.id
      and n.type = 'HEAD_APPLICATION_APPROVED'
  );

-- The normal recipient policy from the earlier migration is retained. These
-- explicit grants make the desired permissions clear for fresh projects while
-- RLS continues to restrict reads and updates to the intended recipient.
grant select, update on table public.notifications to authenticated;
grant all on table public.notifications to service_role;
