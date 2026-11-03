-- 0001_schema.sql — Coach Sportif : schéma de données
-- Cible : Supabase Postgres (schéma public, auth.users géré par Supabase).
-- Règles clés :
--   * planned_activities (prescription) et completed_activities (réalisé) sont des tables distinctes ;
--   * NULL = inconnu, jamais 0 ;
--   * chaque ligne porte user_id, une provenance et des horodatages ;
--   * version optimiste sur les plans, activités prévues et activités réalisées.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- types
do $$ begin
  create type activity_status as enum (
    'proposed',              -- brouillon du coach, non validé
    'planned',               -- validé par l'utilisateur, à faire
    'pending_confirmation',  -- envisagé / à confirmer (foot non confirmé, trail envisagé)
    'in_progress',
    'completed',             -- uniquement via complete_activity (résultat déclaré)
    'cancelled',
    'skipped',
    'superseded'             -- remplacé par une nouvelle version de plan
  );
exception when duplicate_object then null; end $$;

do $$ begin
  create type plan_status as enum ('draft', 'validated', 'superseded');
exception when duplicate_object then null; end $$;

do $$ begin
  create type provenance as enum ('historical_dossier', 'user_report', 'claude_planned', 'app_logged', 'garmin_import');
exception when duplicate_object then null; end $$;

do $$ begin
  create type load_basis as enum ('total', 'per_hand', 'machine', 'bodyweight', 'unknown');
exception when duplicate_object then null; end $$;

-- ---------------------------------------------------------------- profil & objectifs
create table if not exists athlete_profiles (
  user_id        uuid primary key references auth.users(id) on delete cascade,
  age_years      int check (age_years between 10 and 100),
  age_as_of      date,
  sex_reported   text,
  height_cm      numeric(5,1) check (height_cm between 100 and 250),
  current_weight_kg numeric(5,1),               -- NULL tant qu'aucune pesée récente
  timezone       text not null default 'Europe/Paris',
  equipment      jsonb not null default '[]',
  constraints    text,
  preferences    jsonb not null default '[]',
  coach_policy   jsonb not null default '{}',
  extra          jsonb not null default '{}',
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

create table if not exists goals (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  key         text not null,
  sport       text not null,
  description text,
  target      jsonb not null default '{}',
  deadline_text text,
  status      text not null,
  priority    text,
  source      provenance not null default 'historical_dossier',
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (user_id, key)
);

create table if not exists events (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  key         text not null,
  name        text not null,
  event_date  date,                 -- NULL si date non fixée
  date_window text,
  sport       text,
  category    text,
  distance_m  int,
  registration_status text not null,  -- confirmed | envisaged_unconfirmed | ...
  strategy    text,
  extra       jsonb not null default '{}',
  source      provenance not null default 'historical_dossier',
  version     int not null default 1,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (user_id, key)
);

-- ---------------------------------------------------------------- exercices & variantes
create table if not exists exercise_variants (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  key         text not null,             -- ex. vertical_row_machine_66 ≠ vertical_row_machine_120
  split       text,
  name        text not null,
  equipment_variant text,
  location    text,
  load_basis  load_basis not null default 'unknown',
  performance_unit text not null default 'reps' check (performance_unit in ('reps','steps','seconds','distance_m','round_trips')),
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (user_id, key)
);

-- Références historiques documentées (avant l'app) ; séparées des performances app_logged.
create table if not exists exercise_references (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  variant_id  uuid not null references exercise_variants(id) on delete cascade,
  reference   jsonb not null,
  reference_type text not null,          -- reference_confirmed | best_set_only
  last_complete_session_date date,
  future_target_not_validated jsonb,     -- objectif, JAMAIS une performance
  priority_symbol text,
  notes       text,
  source      provenance not null default 'historical_dossier',
  created_at  timestamptz not null default now()
);

-- ---------------------------------------------------------------- plans
create table if not exists training_plans (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  week_start  date not null check (extract(isodow from week_start) = 1),  -- lundi
  week_end    date not null,
  status      plan_status not null default 'draft',
  version     int not null default 1,
  rationale   text,
  decision_note text,
  user_approval_reference text,          -- citation/accord explicite de l'utilisateur
  source      provenance not null default 'claude_planned',
  validated_at timestamptz,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  check (week_end = week_start + 6)
);
-- un seul plan validé par semaine
create unique index if not exists training_plans_one_validated
  on training_plans(user_id, week_start) where status = 'validated';

create table if not exists planned_activities (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  plan_id     uuid not null references training_plans(id) on delete cascade,
  key         text not null,             -- ex. 20261104_run
  activity_date date not null,
  sport       text not null check (sport in ('running','swimming','strength','football','hyrox','cycling','walking','mobility','other')),
  subtype     text,
  title       text not null,
  sort_order  int,
  status      activity_status not null,
  confirmation_required boolean not null default false,  -- publié en pending_confirmation
  confirmed_time_local time,             -- seulement si confirmé
  provisional_time_local time,           -- heure envisagée, non garantie
  target      jsonb not null default '{}',  -- prescription (distance, allure, RIR…)
  notes       text,
  version     int not null default 1,
  updated_by  text not null default 'system',
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
-- clé unique parmi les activités "vivantes" des plans validés
-- (superseded = historique ; proposed = brouillon pouvant réutiliser la clé)
create unique index if not exists planned_activities_live_key
  on planned_activities(user_id, key) where status not in ('superseded', 'proposed');
create index if not exists planned_activities_date on planned_activities(user_id, activity_date);

create table if not exists exercise_targets (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  planned_activity_id uuid not null references planned_activities(id) on delete cascade,
  variant_id  uuid not null references exercise_variants(id),
  position    int not null,
  sets        int check (sets between 1 and 10),
  reps        int,
  steps       int,
  seconds     int,
  load_kg     numeric(6,2),
  rir_min     int,
  rir_max     int,
  is_priority boolean not null default false,
  notes       text,
  unique (planned_activity_id, position)
);

-- ---------------------------------------------------------------- réalisé
create table if not exists completed_activities (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  key         text,                      -- clé historique éventuelle (run_2026_11_02)
  planned_activity_id uuid references planned_activities(id) on delete set null,
  activity_date date not null,
  sport       text not null,
  subtype     text,
  rpe_10      numeric(3,1) check (rpe_10 between 1 and 10),
  rpe_range_reported numeric[],          -- ex. {7,8} quand l'utilisateur a donné une fourchette
  planned_session_type text,
  actual_session_type text,
  notes       text,
  started_at  timestamptz,
  ended_at    timestamptz,
  source      provenance not null,
  version     int not null default 1,
  idempotency_key text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (user_id, key)
);
create unique index if not exists completed_one_per_planned
  on completed_activities(planned_activity_id) where planned_activity_id is not null;
create index if not exists completed_by_date on completed_activities(user_id, activity_date desc);

create table if not exists completed_sets (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  completed_activity_id uuid not null references completed_activities(id) on delete cascade,
  variant_id  uuid not null references exercise_variants(id),
  exercise_position int,
  set_index   int not null check (set_index >= 1),
  is_warmup   boolean not null default false,
  load_kg     numeric(6,2),
  load_basis  load_basis,
  reps        int check (reps >= 0),
  steps       int check (steps >= 0),
  seconds     int check (seconds >= 0),
  distance_m  numeric(8,1),
  rir         int,
  notes       text,
  check (reps is not null or steps is not null or seconds is not null or distance_m is not null),
  unique (completed_activity_id, variant_id, set_index, is_warmup)
);
create index if not exists completed_sets_variant on completed_sets(user_id, variant_id);

create table if not exists endurance_metrics (
  completed_activity_id uuid primary key references completed_activities(id) on delete cascade,
  user_id     uuid not null references auth.users(id) on delete cascade,
  distance_m  numeric(9,1),
  duration_s  int,
  pace_s_per_km numeric(7,1) generated always as (
    case when distance_m > 0 and duration_s is not null then round(duration_s / (distance_m / 1000.0), 1) end
  ) stored,
  reported_pace_s_per_km numeric(7,1),   -- allure déclarée quand la durée exacte manque
  ascent_m    int,
  avg_hr      int check (avg_hr between 30 and 230),
  max_hr      int check (max_hr between 30 and 230),
  effort_type text,                       -- facile | tempo | fractionné | course | match …
  surface     text,
  pool_length_m int,
  extra       jsonb not null default '{}'
);

create table if not exists benchmarks (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  key         text not null,
  date_text   text,
  status      text not null,
  payload     jsonb not null,
  source      provenance not null default 'historical_dossier',
  unique (user_id, key)
);

create table if not exists wellness_checks (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  check_date  date not null,
  completed_activity_id uuid references completed_activities(id) on delete set null,
  fatigue_10  int check (fatigue_10 between 1 and 10),
  pain        text,
  sleep_quality_10 int check (sleep_quality_10 between 1 and 10),
  energy_10   int check (energy_10 between 1 and 10),
  could_continue text,
  answers     jsonb not null default '{}',
  source      provenance not null default 'app_logged',
  created_at  timestamptz not null default now()
);

create table if not exists device_metrics (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  recorded_on date not null,
  device      text,
  max_hr      int,
  rest_hr_approx int,
  lthr        int,
  threshold_pace_s_per_km int,
  threshold_power_w int,
  zones       jsonb,
  method      text,
  is_estimate boolean not null default true,
  caveats     jsonb not null default '[]',
  source      provenance not null default 'user_report'
);

-- ---------------------------------------------------------------- audit & synchro
create table if not exists change_log (
  id          bigint generated always as identity primary key,
  user_id     uuid not null references auth.users(id) on delete cascade,
  entity      text not null,             -- training_plan | planned_activity | completed_activity | event
  entity_id   uuid not null,
  entity_key  text,
  action      text not null,
  old_value   jsonb,
  new_value   jsonb,
  reason      text,
  user_approval_reference text,
  actor       text not null,             -- claude_mcp | android_app | import
  created_at  timestamptz not null default now()
);
create index if not exists change_log_entity on change_log(user_id, entity, entity_id);

create table if not exists sync_operations (
  id          bigint generated always as identity primary key,
  user_id     uuid not null references auth.users(id) on delete cascade,
  idempotency_key text not null,
  operation   text not null,
  request_hash text not null,
  response    jsonb not null,
  created_at  timestamptz not null default now(),
  unique (user_id, idempotency_key)
);

-- ---------------------------------------------------------------- updated_at
create or replace function touch_updated_at() returns trigger language plpgsql as $$
begin new.updated_at := now(); return new; end $$;

do $$ declare t text; begin
  foreach t in array array['athlete_profiles','goals','events','exercise_variants','training_plans',
                           'planned_activities','completed_activities'] loop
    execute format('drop trigger if exists trg_touch on %I', t);
    execute format('create trigger trg_touch before update on %I for each row execute function touch_updated_at()', t);
  end loop;
end $$;
