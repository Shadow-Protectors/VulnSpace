-- Migration: Admin Hierarchy and Community Head Onboarding
-- Description: Adds PLATFORM_ADMIN, COMMUNITY_HEAD roles, head_applications, and updates RLS.

-- ==========================================
-- BOOTSTRAP SCRIPT (RUN THIS MANUALLY ONCE)
-- ==========================================
/*
  To become the first Platform Admin:
  1. Open your Supabase Authentication dashboard and copy your User UUID.
  2. Paste it below and run JUST this insert statement in your SQL Editor:
  
  INSERT INTO public.platform_admins (user_id) 
  VALUES ('PASTE-YOUR-AUTH-UUID-HERE');
*/

-- ==========================================
-- 1. ENUMS & TYPES
-- ==========================================
create type public.head_application_status as enum ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED');
create type public.community_status as enum ('ACTIVE', 'SUSPENDED', 'ARCHIVED');

-- Update communities table to use the new status
alter table public.communities add column status public.community_status not null default 'ACTIVE';

-- Update member_role to include PLATFORM_ADMIN just in case, though we use a separate table
-- alter type public.member_role add value 'PLATFORM_ADMIN'; (Skipping this, keeping platform admin separate)

-- ==========================================
-- 2. NEW TABLES
-- ==========================================

-- Platform Admins
create table public.platform_admins (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references auth.users(id) on delete cascade,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);

-- Community Head Applications
create table public.head_applications (
  id uuid primary key default gen_random_uuid(),
  applicant_user_id uuid not null references auth.users(id) on delete cascade,
  full_name text not null,
  email text not null,
  phone text,
  organization text not null,
  proposed_community_name text not null,
  proposed_description text not null,
  reason text not null,
  status public.head_application_status not null default 'PENDING',
  reviewed_by uuid references auth.users(id),
  reviewed_at timestamptz,
  rejection_reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- ==========================================
-- 3. HELPER FUNCTIONS
-- ==========================================

create or replace function public.is_platform_admin()
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1 from public.platform_admins
    where user_id = auth.uid()
  );
$$;

-- ==========================================
-- 4. ROW LEVEL SECURITY (RLS) POLICIES
-- ==========================================

alter table public.platform_admins enable row level security;
alter table public.head_applications enable row level security;

-- Platform Admins RLS
create policy platform_admins_read on public.platform_admins
for select using (user_id = auth.uid());

-- Head Applications RLS
-- Applicants can read their own applications
create policy head_app_applicant_read on public.head_applications
for select using (applicant_user_id = auth.uid());

-- Platform Admins can read all applications
create policy head_app_admin_read on public.head_applications
for select using (public.is_platform_admin());

-- Applicants can submit new applications
create policy head_app_insert on public.head_applications
for insert with check (applicant_user_id = auth.uid());

-- ONLY Platform Admins can update applications (approve/reject)
create policy head_app_admin_update on public.head_applications
for update using (public.is_platform_admin());

-- Update existing communities RLS
-- Platform Admins can read and update all communities
create policy community_admin_read on public.communities
for select using (public.is_platform_admin());

create policy community_admin_update on public.communities
for update using (public.is_platform_admin());

-- ==========================================
-- 5. AUDIT LOGS FIXES
-- ==========================================
-- Allow Platform Admins to read all audit logs
create policy admin_audit_read on public.audit_logs
for select using (public.is_platform_admin());
