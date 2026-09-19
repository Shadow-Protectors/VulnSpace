-- Migration: Complete Platform Admin RLS, Grants, Schema Consistency, and Realtime Publications
-- Run this in your Supabase SQL Editor to resolve all permission and schema errors.

-- ==============================================================================
-- 1. SCHEMA ALIGNMENT (Fixes 55P04 enum locks and ensures required columns exist)
-- ==============================================================================

-- 1.1 Convert community_members.role to varchar to allow COMMUNITY_HEAD without enum lock
do $$
begin
  alter table public.community_members alter column role drop default;
  alter table public.community_members alter column role type varchar(50) using role::varchar;
  alter table public.community_members alter column role set default 'MEMBER';
exception when others then null;
end $$;

-- 1.2 Ensure profiles supports must_change_password
alter table public.profiles
add column if not exists must_change_password boolean not null default false;

-- 1.3 Ensure head_applications has all tracking columns & indexes
alter table public.head_applications 
add column if not exists applicant_user_id uuid references auth.users(id) on delete cascade,
add column if not exists approved_by uuid references auth.users(id),
add column if not exists approved_at timestamptz,
add column if not exists rejection_reason text,
add column if not exists updated_at timestamptz not null default now();

alter table public.head_applications alter column applicant_user_id drop not null;

create index if not exists head_applications_status_idx
on public.head_applications(status, created_at desc);

-- 1.4 Ensure notifications has recipient tracking
alter table public.notifications 
add column if not exists recipient_user_id uuid references auth.users(id) on delete cascade,
add column if not exists application_id uuid references public.head_applications(id) on delete cascade,
add column if not exists type text,
add column if not exists read_at timestamptz;

-- 1.5 Create email_deliveries table if missing
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

-- ==============================================================================
-- 2. POSTGRESQL TABLE GRANTS (Fixes 'permission denied for table community_members')
-- ==============================================================================

grant select on table public.community_members to authenticated;
grant select on table public.communities to authenticated;
grant select on table public.head_applications to authenticated;
grant select on table public.audit_logs to authenticated;
grant select on table public.notifications to authenticated;
grant select on table public.platform_admins to authenticated;
grant select on table public.email_deliveries to authenticated;

grant update on table public.head_applications to authenticated;
grant update on table public.communities to authenticated;

revoke select on table public.head_applications from anon;
revoke select on table public.audit_logs from anon;
revoke select on table public.notifications from anon;
revoke select on table public.community_members from anon;
revoke select on table public.communities from anon;
revoke select on table public.platform_admins from anon;

grant insert on table public.head_applications to anon;
grant insert on table public.head_applications to authenticated;

grant all on table public.community_members to service_role;
grant all on table public.communities to service_role;
grant all on table public.head_applications to service_role;
grant all on table public.audit_logs to service_role;
grant all on table public.notifications to service_role;
grant all on table public.platform_admins to service_role;
grant all on table public.email_deliveries to service_role;

-- ==============================================================================
-- 3. PLATFORM ADMIN AUTHENTICATION FUNCTION
-- ==============================================================================

create or replace function public.is_platform_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.platform_admins
    where user_id = auth.uid()
  );
$$;

grant execute on function public.is_platform_admin() to authenticated;
grant execute on function public.is_platform_admin() to anon;

-- ==============================================================================
-- 4. ROW LEVEL SECURITY (RLS) POLICIES
-- ==============================================================================

alter table public.platform_admins enable row level security;
alter table public.community_members enable row level security;
alter table public.communities enable row level security;
alter table public.head_applications enable row level security;
alter table public.audit_logs enable row level security;
alter table public.notifications enable row level security;
alter table public.email_deliveries enable row level security;

-- Platform Admins table
drop policy if exists platform_admins_read on public.platform_admins;
create policy platform_admins_read
on public.platform_admins
for select
to authenticated
using (user_id = auth.uid() or public.is_platform_admin());

-- Community Members: Admins can read all members
drop policy if exists platform_admin_read_community_members on public.community_members;
create policy platform_admin_read_community_members
on public.community_members
for select
to authenticated
using (public.is_platform_admin());

-- Communities: Admins can read and update
drop policy if exists platform_admin_read_communities on public.communities;
create policy platform_admin_read_communities
on public.communities
for select
to authenticated
using (public.is_platform_admin());

drop policy if exists platform_admin_update_communities on public.communities;
create policy platform_admin_update_communities
on public.communities
for update
to authenticated
using (public.is_platform_admin());

-- Head Applications: Admins can read and update all applications
drop policy if exists platform_admin_read_head_applications on public.head_applications;
create policy platform_admin_read_head_applications
on public.head_applications
for select
to authenticated
using (public.is_platform_admin());

drop policy if exists head_app_applicant_read on public.head_applications;
create policy head_app_applicant_read
on public.head_applications
for select
to authenticated
using (applicant_user_id is not null and applicant_user_id = auth.uid());

drop policy if exists head_app_insert on public.head_applications;
create policy head_app_insert
on public.head_applications
for insert
with check (
  length(trim(full_name)) > 0
  and length(trim(email)) > 3
  and length(trim(proposed_community_name)) > 0
);

drop policy if exists platform_admin_update_head_applications on public.head_applications;
create policy platform_admin_update_head_applications
on public.head_applications
for update
to authenticated
using (public.is_platform_admin());

-- Audit Logs
drop policy if exists platform_admin_read_audit_logs on public.audit_logs;
create policy platform_admin_read_audit_logs
on public.audit_logs
for select
to authenticated
using (public.is_platform_admin());

-- Notifications
drop policy if exists platform_admin_read_notifications on public.notifications;
create policy platform_admin_read_notifications
on public.notifications
for select
to authenticated
using (public.is_platform_admin());

-- Email Deliveries
drop policy if exists email_deliveries_admin_read on public.email_deliveries;
create policy email_deliveries_admin_read
on public.email_deliveries
for select
to authenticated
using (public.is_platform_admin());

-- ==============================================================================
-- 5. REALTIME PUBLICATIONS
-- ==============================================================================

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
