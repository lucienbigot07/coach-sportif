-- Reproduction minimale de ce que Supabase fournit déjà (NE PAS appliquer sur Supabase).
-- Rôles anon/authenticated, schéma auth, auth.users, auth.uid() identique à Supabase.
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon nologin noinherit; end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin noinherit; end if;
  if not exists (select 1 from pg_roles where rolname = 'coach_owner') then create role coach_owner nologin; end if; -- ni superuser ni bypassrls
end $$;
create schema if not exists auth;
create table if not exists auth.users (id uuid primary key, email text unique);
create or replace function auth.uid() returns uuid language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claim.sub', true), ''),
                  (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'))::uuid
$$;
grant usage on schema auth to anon, authenticated, coach_owner;
grant execute on function auth.uid() to anon, authenticated, coach_owner;
grant references, select on auth.users to coach_owner;
grant usage on schema public to anon, authenticated;
