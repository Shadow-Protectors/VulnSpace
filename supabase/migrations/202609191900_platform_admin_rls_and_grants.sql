-- Migration: Platform Admin RLS, Grants, Consistency, and Realtime Publications
-- Run this in your Supabase SQL Editor to resolve permission denied errors

-- ==============================================================================
-- 1. POSTGRESQL TABLE GRANTS (Fixes 'permission denied for table community_members')
-- ==============================================================================

-- Authenticated users need table-level privileges (RLS policies then filter rows)
grant select on table public.community_members to authenticated;
grant select on table public.communities to authenticated;
grant select on table public.head_applications to authenticated;
grant select on table public.audit_logs to authenticated;
grant select on table public.notifications to authenticated;
grant select on table public.platform_admins to authenticated;

-- Allow authenticated admins to update/insert where needed
grant update on table public.head_applications to authenticated;
grant update on table public.communities to authenticated;

-- Revoke dangerous read privileges from anonymous users
revoke select on table public.head_applications from anon;
revoke select on table public.audit_logs from anon;
revoke select on table public.notifications from anon;
revoke select on table public.community_members from anon;
revoke select on table public.communities from anon;
revoke select on table public.platform_admins from anon;

-- Allow anonymous users to submit head applications
grant insert on table public.head_applications to anon;
grant insert on table public.head_applications to authenticated;

-- Service role retains full administrative access for backend Edge Functions
grant all on table public.community_members to service_role;
grant all on table public.communities to service_role;
grant all on table public.head_applications to service_role;
grant all on table public.audit_logs to service_role;
grant all on table public.notifications to service_role;
grant all on table public.platform_admins to service_role;

-- ==============================================================================
-- 2. PLATFORM ADMIN AUTHENTICATION FUNCTION
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
-- 3. ROW LEVEL SECURITY (RLS) POLICIES FOR PLATFORM ADMIN
-- ==============================================================================

alter table public.platform_admins enable row level security;
alter table public.community_members enable row level security;
alter table public.communities enable row level security;
alter table public.head_applications enable row level security;
alter table public.audit_logs enable row level security;
alter table public.notifications enable row level security;

-- Platform Admins table policy: Admins can read records
drop policy if exists platform_admins_read on public.platform_admins;
create policy platform_admins_read
on public.platform_admins
for select
to authenticated
using (user_id = auth.uid() or public.is_platform_admin());

-- Community Members: Platform Admins can read ALL community members
drop policy if exists platform_admin_read_community_members on public.community_members;
create policy platform_admin_read_community_members
on public.community_members
for select
to authenticated
using (public.is_platform_admin());

-- Communities: Platform Admins can read ALL communities
drop policy if exists platform_admin_read_communities on public.communities;
create policy platform_admin_read_communities
on public.communities
for select
to authenticated
using (public.is_platform_admin());

-- Communities: Platform Admins can update communities (suspend / reactivate)
drop policy if exists platform_admin_update_communities on public.communities;
create policy platform_admin_update_communities
on public.communities
for update
to authenticated
using (public.is_platform_admin());

-- Head Applications: Platform Admins can read ALL applications
drop policy if exists platform_admin_read_head_applications on public.head_applications;
create policy platform_admin_read_head_applications
on public.head_applications
for select
to authenticated
using (public.is_platform_admin());

-- Head Applications: Applicants can read their own applications
drop policy if exists head_app_applicant_read on public.head_applications;
create policy head_app_applicant_read
on public.head_applications
for select
to authenticated
using (applicant_user_id is not null and applicant_user_id = auth.uid());

-- Head Applications: Insertion policy for anon and authenticated applicants
drop policy if exists head_app_insert on public.head_applications;
create policy head_app_insert
on public.head_applications
for insert
with check (
  length(trim(full_name)) > 0
  and length(trim(email)) > 3
  and length(trim(proposed_community_name)) > 0
);

-- Head Applications: Platform Admins can update (approve / reject) directly if needed
drop policy if exists platform_admin_update_head_applications on public.head_applications;
create policy platform_admin_update_head_applications
on public.head_applications
for update
to authenticated
using (public.is_platform_admin());

-- Audit Logs: Platform Admins can read all audit logs
drop policy if exists platform_admin_read_audit_logs on public.audit_logs;
create policy platform_admin_read_audit_logs
on public.audit_logs
for select
to authenticated
using (public.is_platform_admin());

-- Notifications: Platform Admins can read notifications
drop policy if exists platform_admin_read_notifications on public.notifications;
create policy platform_admin_read_notifications
on public.notifications
for select
to authenticated
using (public.is_platform_admin());

-- ==============================================================================
-- 4. REALTIME PUBLICATION CONFIGURATION
-- ==============================================================================

do $$
begin
  alter publication supabase_realtime add table public.head_applications;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.communities;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.audit_logs;
exception when duplicate_object then null;
end $$;
