-- Allow public/anonymous applicants to submit community applications
-- Run this in your Supabase SQL Editor

-- 1. Grant INSERT to anon role on head_applications, but revoke SELECT to protect applicant data
revoke select on table public.head_applications from anon;
grant insert on table public.head_applications to anon;

-- 2. Make applicant_user_id nullable so users without an active session can submit
alter table public.head_applications alter column applicant_user_id drop not null;

-- 3. Update RLS policy to allow application submission
drop policy if exists head_app_insert on public.head_applications;
create policy head_app_insert on public.head_applications
for insert with check (true);
