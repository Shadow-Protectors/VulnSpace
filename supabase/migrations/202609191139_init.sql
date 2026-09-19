  -- Cyber Community Mobile Application
  -- PostgreSQL / Supabase schema

  create extension if not exists pgcrypto;

  create type public.member_role as enum ('MEMBER', 'HEAD');
  create type public.member_status as enum ('ACTIVE', 'REMOVED');
  create type public.content_type as enum ('EVENT', 'RESOURCE');
  create type public.content_category as enum (
    'CTF',
    'HACKATHON',
    'INTERNSHIP',
    'CONFERENCE',
    'WORKSHOP',
    'STUDY_MATERIAL',
    'TOOL',
    'WRITEUP',
    'COURSE',
    'DOCUMENTATION',
    'OTHER_RESOURCE'
  );
  create type public.content_status as enum (
    'PROCESSING',
    'PUBLISHED',
    'PROCESSING_FAILED',
    'EXPIRED',
    'ARCHIVED',
    'REMOVED'
  );
  create type public.safety_status as enum ('LOW_RISK', 'NEEDS_REVIEW', 'BLOCKED', 'UNKNOWN');

  create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text not null unique,
    created_at timestamptz not null default now()
  );

  create table public.communities (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    description text,
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
  );

  create table public.community_members (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.communities(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    username text not null,
    role public.member_role not null default 'MEMBER',
    status public.member_status not null default 'ACTIVE',
    joined_at timestamptz not null default now(),
    unique (community_id, user_id),
    unique (community_id, username)
  );

  create table public.invite_links (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.communities(id) on delete cascade,
    token_hash text not null unique,
    created_by uuid not null references auth.users(id),
    expires_at timestamptz,
    max_uses integer,
    uses integer not null default 0,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
  );

  create table public.content (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.communities(id) on delete cascade,
    submitted_by uuid not null references auth.users(id),
    title text not null,
    description text,
    source_url text not null,
    final_url text,
    source_domain text,
    content_type public.content_type not null,
    category public.content_category not null,
    status public.content_status not null default 'PROCESSING',
    safety_status public.safety_status not null default 'UNKNOWN',
    safety_reason text,
    organizer text,
    registration_url text,
    rules_url text,
    start_at timestamptz,
    end_at timestamptz,
    registration_deadline timestamptz,
    location text,
    is_online boolean,
    team_size text,
    tags text[] not null default '{}',
    poster_url text,
    priority integer not null default 100,
    extraction_confidence numeric(5,4),
    extracted_data jsonb not null default '{}'::jsonb,
    last_checked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint source_url_not_empty check (length(trim(source_url)) > 0),
    constraint event_dates_check check (
      content_type = 'RESOURCE'
      or (start_at is not null or end_at is not null or registration_deadline is not null)
    )
  );

  create unique index content_unique_source_per_community
  on public.content (community_id, md5(source_url))
  where status <> 'REMOVED';

  create index content_feed_index
  on public.content (community_id, status, content_type, priority, registration_deadline);

  create table public.processing_jobs (
    id uuid primary key default gen_random_uuid(),
    content_id uuid references public.content(id) on delete cascade,
    community_id uuid not null references public.communities(id) on delete cascade,
    submitted_by uuid not null references auth.users(id),
    source_url text not null,
    state text not null default 'QUEUED',
    error_message text,
    attempts integer not null default 0,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz
  );

  create table public.bookmarks (
    user_id uuid not null references auth.users(id) on delete cascade,
    content_id uuid not null references public.content(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, content_id)
  );

  create table public.reports (
    id uuid primary key default gen_random_uuid(),
    content_id uuid not null references public.content(id) on delete cascade,
    reported_by uuid not null references auth.users(id),
    reason text not null,
    created_at timestamptz not null default now(),
    resolved_at timestamptz,
    resolved_by uuid references auth.users(id)
  );

  create table public.notifications (
    id uuid primary key default gen_random_uuid(),
    community_id uuid references public.communities(id) on delete cascade,
    content_id uuid references public.content(id) on delete cascade,
    title text not null,
    body text not null,
    created_at timestamptz not null default now()
  );

  create table public.device_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    fcm_token text not null unique,
    platform text not null default 'ANDROID',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
  );

  create table public.audit_logs (
    id uuid primary key default gen_random_uuid(),
    community_id uuid references public.communities(id) on delete cascade,
    actor_id uuid references auth.users(id),
    action text not null,
    target_type text,
    target_id uuid,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
  );

  -- Helper functions
  create or replace function public.is_active_member(target_community uuid)
  returns boolean
  language sql stable security definer set search_path = public
  as $$
    select exists (
      select 1 from public.community_members cm
      where cm.community_id = target_community
        and cm.user_id = auth.uid()
        and cm.status = 'ACTIVE'
    );
  $$;

  create or replace function public.is_community_head(target_community uuid)
  returns boolean
  language sql stable security definer set search_path = public
  as $$
    select exists (
      select 1 from public.community_members cm
      where cm.community_id = target_community
        and cm.user_id = auth.uid()
        and cm.role = 'HEAD'
        and cm.status = 'ACTIVE'
    );
  $$;

  alter table public.profiles enable row level security;
  alter table public.communities enable row level security;
  alter table public.community_members enable row level security;
  alter table public.invite_links enable row level security;
  alter table public.content enable row level security;
  alter table public.processing_jobs enable row level security;
  alter table public.bookmarks enable row level security;
  alter table public.reports enable row level security;
  alter table public.notifications enable row level security;
  alter table public.device_tokens enable row level security;
  alter table public.audit_logs enable row level security;

  -- Profiles
  create policy profiles_self_read on public.profiles
  for select using (id = auth.uid());

  create policy profiles_self_update on public.profiles
  for update using (id = auth.uid());

  -- Communities
  create policy community_member_read on public.communities
  for select using (public.is_active_member(id));

  create policy community_head_update on public.communities
  for update using (public.is_community_head(id));

  -- Members can see members of their own community; heads manage members through an Edge Function.
  create policy members_same_community_read on public.community_members
  for select using (public.is_active_member(community_id));

  -- Published content is readable by active members.
  create policy published_content_read on public.content
  for select using (
    public.is_active_member(community_id)
    and (status = 'PUBLISHED' or status in ('EXPIRED', 'ARCHIVED'))
  );

  create policy member_submit_content on public.content
  for insert with check (
    submitted_by = auth.uid()
    and public.is_active_member(community_id)
  );

  create policy member_edit_own_content on public.content
  for update using (
    submitted_by = auth.uid()
    and public.is_active_member(community_id)
  )
  with check (
    submitted_by = auth.uid()
    and public.is_active_member(community_id)
  );

  create policy head_manage_content on public.content
  for update using (public.is_community_head(community_id));

  create policy head_remove_content on public.content
  for delete using (public.is_community_head(community_id));

  create policy member_bookmarks on public.bookmarks
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

  create policy member_reports on public.reports
  for insert with check (
    reported_by = auth.uid()
    and public.is_active_member((select community_id from public.content where id = content_id))
  );

  create policy member_notifications_read on public.notifications
  for select using (public.is_active_member(community_id));

  create policy device_tokens_self on public.device_tokens
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

  create policy head_audit_read on public.audit_logs
  for select using (public.is_community_head(community_id));

  -- Important:
  -- Invite validation, member creation, URL processing, scheduled updates,
  -- service-role operations, and FCM sending should be performed by protected
  -- Edge Functions or scheduled backend jobs, not by exposing service credentials.
