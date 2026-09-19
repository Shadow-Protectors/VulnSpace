-- Migration: Comprehensive idempotent schema fix for head_applications and platform_admins
-- Run this script in your Supabase SQL Editor

-- 1. Ensure platform_admins table exists
create table if not exists public.platform_admins (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references auth.users(id) on delete cascade,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);

-- 2. Ensure head_applications table and required columns exist
create table if not exists public.head_applications (
  id uuid primary key default gen_random_uuid(),
  full_name text not null,
  email text not null,
  organization text not null,
  proposed_community_name text not null,
  proposed_description text not null,
  reason text not null,
  created_at timestamptz not null default now()
);

-- If user_id was previously used instead of applicant_user_id, rename it
do $$
begin
  if exists (
    select 1 from information_schema.columns 
    where table_schema = 'public' and table_name = 'head_applications' and column_name = 'user_id'
  ) and not exists (
    select 1 from information_schema.columns 
    where table_schema = 'public' and table_name = 'head_applications' and column_name = 'applicant_user_id'
  ) then
    alter table public.head_applications rename column user_id to applicant_user_id;
  end if;
end $$;

-- Ensure applicant_user_id column exists
alter table public.head_applications 
add column if not exists applicant_user_id uuid references auth.users(id) on delete cascade;

-- Ensure phone, status, and review columns exist
alter table public.head_applications 
add column if not exists phone text,
add column if not exists status text not null default 'PENDING',
add column if not exists reviewed_by uuid references auth.users(id),
add column if not exists reviewed_at timestamptz,
add column if not exists rejection_reason text,
add column if not exists updated_at timestamptz not null default now();

-- 3. Schema & Table Grants
grant usage on schema public to authenticated, anon;

grant select, insert, update on table public.head_applications to authenticated;
grant all on table public.head_applications to service_role;

grant select, insert, update, delete on table public.platform_admins to authenticated;
grant all on table public.platform_admins to service_role;

grant select, update on table public.communities to authenticated;
grant select on table public.audit_logs to authenticated;

-- 4. Helper function: is_platform_admin
create or replace function public.is_platform_admin()
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1 from public.platform_admins
    where user_id = auth.uid()
  );
$$;

grant execute on function public.is_platform_admin() to authenticated, anon;

-- 5. Row Level Security verification and policies
alter table public.platform_admins enable row level security;
alter table public.head_applications enable row level security;

-- Platform Admins RLS
drop policy if exists platform_admins_read on public.platform_admins;
create policy platform_admins_read on public.platform_admins
for select using (user_id = auth.uid() or public.is_platform_admin());

-- Head Applications RLS
drop policy if exists head_app_applicant_read on public.head_applications;
create policy head_app_applicant_read on public.head_applications
for select using (applicant_user_id = auth.uid());

drop policy if exists head_app_admin_read on public.head_applications;
create policy head_app_admin_read on public.head_applications
for select using (public.is_platform_admin());

drop policy if exists head_app_insert on public.head_applications;
create policy head_app_insert on public.head_applications
for insert with check (applicant_user_id = auth.uid());

drop policy if exists head_app_admin_update on public.head_applications;
create policy head_app_admin_update on public.head_applications
for update using (public.is_platform_admin());

-- 6. Register Platform Administrator
insert into public.platform_admins (user_id)
select id from auth.users where lower(email) = 'hariganesh260@gmail.com'
on conflict (user_id) do nothing;
