-- Migration: Complete schema alignment for Head Application Workflow, Audit Logs, Notifications, and Email Deliveries
-- Run this in your Supabase SQL Editor

-- 1. Ensure profiles table supports must_change_password
alter table public.profiles
add column if not exists must_change_password boolean not null default false;

-- 2. Ensure head_applications table has all tracking columns & indexes
alter table public.head_applications 
add column if not exists applicant_user_id uuid references auth.users(id) on delete cascade,
add column if not exists approved_by uuid references auth.users(id),
add column if not exists approved_at timestamptz,
add column if not exists rejection_reason text,
add column if not exists updated_at timestamptz not null default now();

-- Ensure applicant_user_id is nullable for public applicants
alter table public.head_applications alter column applicant_user_id drop not null;

-- SECURITY FIX: Revoke SELECT from anon so public users cannot read sensitive applicant data
revoke select on table public.head_applications from anon;

-- Grant INSERT to anon so public applicants can submit their application
grant insert on table public.head_applications to anon;

-- RLS: Validate submitted application content
drop policy if exists head_app_insert on public.head_applications;
create policy head_app_insert on public.head_applications
for insert with check (
  length(trim(full_name)) > 0
  and length(trim(email)) > 3
  and length(trim(proposed_community_name)) > 0
);

create index if not exists head_applications_status_idx
on public.head_applications(status, created_at desc);

-- 3. Ensure notifications has recipient, application_id and read tracking
alter table public.notifications 
add column if not exists recipient_user_id uuid references auth.users(id) on delete cascade,
add column if not exists application_id uuid references public.head_applications(id) on delete cascade,
add column if not exists type text,
add column if not exists read_at timestamptz;

-- 4. Role Consistency: Convert community_members.role to text to eliminate enum transaction locks (55P04)
alter table public.community_members alter column role drop default;
alter table public.community_members alter column role type text using role::text;
alter table public.community_members alter column role set default 'MEMBER';

-- Migrate existing HEAD records to COMMUNITY_HEAD
update public.community_members
set role = 'COMMUNITY_HEAD'
where role = 'HEAD';

-- Update is_community_head to support COMMUNITY_HEAD (and legacy HEAD)
create or replace function public.is_community_head(target_community uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1 from public.community_members cm
    where cm.community_id = target_community
      and cm.user_id = auth.uid()
      and cm.role in ('COMMUNITY_HEAD', 'HEAD')
      and cm.status = 'ACTIVE'
  );
$$;

-- 5. Email Deliveries Tracking Table
create table if not exists public.email_deliveries (
    id uuid primary key default gen_random_uuid(),
    recipient_email text not null,
    recipient_user_id uuid references auth.users(id),
    application_id uuid references public.head_applications(id),
    email_type text not null,
    status text not null default 'NOT_SENT',
    provider_message_id text,
    error_message text,
    sent_at timestamptz,
    created_at timestamptz not null default now()
);

alter table public.email_deliveries enable row level security;

drop policy if exists email_deliveries_admin_read on public.email_deliveries;
create policy email_deliveries_admin_read on public.email_deliveries
for select using (public.is_platform_admin());

grant select on table public.email_deliveries to authenticated;
grant all on table public.email_deliveries to service_role;

-- 6. Row Level Security Policies
alter table public.head_applications enable row level security;
alter table public.audit_logs enable row level security;
alter table public.notifications enable row level security;

-- Only platform admins and the applicant themselves can read head applications
drop policy if exists head_app_admin_read on public.head_applications;
create policy head_app_admin_read on public.head_applications
for select using (public.is_platform_admin());

drop policy if exists head_app_applicant_read on public.head_applications;
create policy head_app_applicant_read on public.head_applications
for select using (applicant_user_id is not null and applicant_user_id = auth.uid());

-- Audit logs RLS: Platform admins and heads can read
drop policy if exists admin_audit_read on public.audit_logs;
create policy admin_audit_read on public.audit_logs
for select using (
  public.is_platform_admin() 
  or (community_id is not null and public.is_community_head(community_id))
);

-- Notifications RLS: Recipients and active members can read
drop policy if exists notifications_read on public.notifications;
create policy notifications_read on public.notifications
for select using (
  (recipient_user_id is not null and recipient_user_id = auth.uid())
  or (community_id is not null and public.is_active_member(community_id))
  or public.is_platform_admin()
);

-- 7. Enable Realtime Publications
do $$
begin
  alter publication supabase_realtime add table public.head_applications;
exception when others then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.communities;
exception when others then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.audit_logs;
exception when others then null;
end $$;
