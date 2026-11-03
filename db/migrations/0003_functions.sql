-- 0003_functions.sql — Fonctions métier (API RPC, exposées par PostgREST /rest/v1/rpc/<nom>)
--
-- Utilisées à l'identique par l'application Android et par le serveur MCP.
-- Erreurs : errcode 'PTnnn' → PostgREST renvoie le statut HTTP nnn (PT409 = 409 Conflict…),
--           message = code machine (version_conflict, approval_required…), detail = JSON.

create schema if not exists coach_private;
revoke all on schema coach_private from public;

-- ================================================================ helpers privés
create or replace function coach_private.uid() returns uuid
language plpgsql stable as $$
declare v uuid := auth.uid();
begin
  if v is null then
    raise exception using errcode = 'PT401', message = 'not_authenticated';
  end if;
  return v;
end $$;

create or replace function coach_private.fail(p_status int, p_code text, p_detail jsonb default '{}'::jsonb)
returns void language plpgsql as $$
begin
  raise exception using errcode = 'PT' || p_status::text, message = p_code, detail = p_detail::text;
end $$;

create or replace function coach_private.today(p_uid uuid) returns date
language sql stable as $$
  select (now() at time zone coalesce((select timezone from athlete_profiles where user_id = p_uid), 'Europe/Paris'))::date
$$;

create or replace function coach_private.week_start_of(p_date date) returns date
language sql immutable as $$ select p_date - (extract(isodow from p_date)::int - 1) $$;

-- Idempotence : rejoue la réponse stockée si (clé, opération, contenu) identiques.
create or replace function coach_private.idem_lookup(p_uid uuid, p_key text, p_op text, p_request jsonb)
returns jsonb language plpgsql as $$
declare r sync_operations;
begin
  if p_key is null or length(p_key) < 8 then
    perform coach_private.fail(400, 'idempotency_key_required', jsonb_build_object('hint', 'clé unique ≥ 8 caractères par opération'));
  end if;
  select * into r from sync_operations where user_id = p_uid and idempotency_key = p_key;
  if not found then return null; end if;
  if r.operation <> p_op or r.request_hash <> md5(p_request::text) then
    perform coach_private.fail(409, 'idempotency_key_reused',
      jsonb_build_object('operation', r.operation, 'hint', 'la même clé a déjà servi pour une requête différente'));
  end if;
  return r.response || jsonb_build_object('replayed', true);
end $$;

create or replace function coach_private.idem_store(p_uid uuid, p_key text, p_op text, p_request jsonb, p_response jsonb)
returns jsonb language plpgsql as $$
begin
  insert into sync_operations(user_id, idempotency_key, operation, request_hash, response)
  values (p_uid, p_key, p_op, md5(p_request::text), p_response);
  return p_response || jsonb_build_object('replayed', false);
end $$;

create or replace function coach_private.log(p_uid uuid, p_entity text, p_id uuid, p_key text, p_action text,
  p_old jsonb, p_new jsonb, p_reason text, p_approval text, p_actor text)
returns void language sql as $$
  insert into change_log(user_id, entity, entity_id, entity_key, action, old_value, new_value, reason, user_approval_reference, actor)
  values (p_uid, p_entity, p_id, p_key, p_action, p_old, p_new, p_reason, p_approval, coalesce(p_actor, 'unknown'));
$$;

create or replace function coach_private.require_approval(p_approval text) returns void
language plpgsql as $$
begin
  if p_approval is null or length(btrim(p_approval)) < 3 then
    perform coach_private.fail(422, 'approval_required', jsonb_build_object(
      'hint', 'citer l''accord explicite de l''utilisateur (ex. « Je valide »). Un brainstorming ne se publie pas.'));
  end if;
end $$;

-- Référence d'une variante : dernière séance journalisée + référence historique + objectif non validé.
create or replace function coach_private.variant_reference(p_uid uuid, p_variant uuid) returns jsonb
language sql stable as $$
  select jsonb_build_object(
    'last_logged', (
      select jsonb_build_object(
        'date', ca.activity_date, 'source', ca.source, 'completed_activity_id', ca.id,
        'sets', (select jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
                    'set_index', s.set_index, 'load_kg', s.load_kg, 'load_basis', s.load_basis,
                    'reps', s.reps, 'steps', s.steps, 'seconds', s.seconds, 'distance_m', s.distance_m, 'rir', s.rir))
                  order by s.set_index)
                 from completed_sets s
                 where s.completed_activity_id = ca.id and s.variant_id = p_variant and not s.is_warmup))
      from completed_activities ca
      where ca.user_id = p_uid
        and exists (select 1 from completed_sets s where s.completed_activity_id = ca.id
                    and s.variant_id = p_variant and not s.is_warmup)
      order by ca.activity_date desc, ca.created_at desc limit 1),
    'historical_reference', (
      select jsonb_strip_nulls(jsonb_build_object('reference', r.reference, 'reference_type', r.reference_type,
               'last_complete_session_date', r.last_complete_session_date, 'notes', r.notes, 'source', r.source))
      from exercise_references r where r.user_id = p_uid and r.variant_id = p_variant
      order by r.created_at desc limit 1),
    'future_target_not_validated', (
      select r.future_target_not_validated from exercise_references r
      where r.user_id = p_uid and r.variant_id = p_variant order by r.created_at desc limit 1)
  )
$$;

create or replace function coach_private.variant_json(p_uid uuid, v exercise_variants) returns jsonb
language sql stable as $$
  select jsonb_strip_nulls(jsonb_build_object(
    'variant_key', v.key, 'name', v.name, 'split', v.split, 'equipment_variant', v.equipment_variant,
    'location', v.location, 'load_basis', v.load_basis, 'performance_unit', v.performance_unit, 'notes', v.notes))
$$;

create or replace function coach_private.completed_json(p_uid uuid, p_id uuid) returns jsonb
language sql stable as $$
  select jsonb_strip_nulls(jsonb_build_object(
    'completed_activity_id', ca.id, 'key', ca.key, 'date', ca.activity_date, 'sport', ca.sport, 'subtype', ca.subtype,
    'status', 'completed', 'rpe_10', ca.rpe_10, 'rpe_range_reported', to_jsonb(ca.rpe_range_reported),
    'planned_session_type', ca.planned_session_type, 'actual_session_type', ca.actual_session_type,
    'notes', ca.notes, 'source', ca.source, 'version', ca.version,
    'started_at', ca.started_at, 'ended_at', ca.ended_at, 'logged_at', ca.created_at,
    'planned_session_key', pa.key, 'planned_target', pa.target,
    'endurance', (select jsonb_strip_nulls(jsonb_build_object(
        'distance_m', em.distance_m, 'duration_s', em.duration_s, 'pace_s_per_km', coalesce(em.pace_s_per_km, em.reported_pace_s_per_km),
        'pace_is_reported_only', case when em.pace_s_per_km is null and em.reported_pace_s_per_km is not null then true end,
        'ascent_m', em.ascent_m, 'avg_hr', em.avg_hr, 'max_hr', em.max_hr, 'effort_type', em.effort_type,
        'surface', em.surface, 'pool_length_m', em.pool_length_m, 'extra', nullif(em.extra, '{}'::jsonb)))
      from endurance_metrics em where em.completed_activity_id = ca.id),
    'exercises', (
      select jsonb_agg(ex order by pos) from (
        select min(coalesce(s.exercise_position, 999)) as pos,
          coach_private.variant_json(p_uid, v) || jsonb_build_object(
            'work_sets', coalesce(jsonb_agg(jsonb_strip_nulls(jsonb_build_object('set_index', s.set_index, 'load_kg', s.load_kg,
                 'load_basis', s.load_basis, 'reps', s.reps, 'steps', s.steps, 'seconds', s.seconds,
                 'distance_m', s.distance_m, 'rir', s.rir, 'notes', s.notes)) order by s.set_index) filter (where not s.is_warmup), '[]'::jsonb),
            'warmup_sets', coalesce(jsonb_agg(jsonb_strip_nulls(jsonb_build_object('set_index', s.set_index, 'load_kg', s.load_kg,
                 'reps', s.reps, 'steps', s.steps, 'seconds', s.seconds)) order by s.set_index) filter (where s.is_warmup), '[]'::jsonb)
          ) as ex
        from completed_sets s join exercise_variants v on v.id = s.variant_id
        where s.completed_activity_id = ca.id
        group by v.id) q),
    'work_sets_count', (select nullif(count(*), 0) from completed_sets s where s.completed_activity_id = ca.id and not s.is_warmup)
  ))
  from completed_activities ca
  left join planned_activities pa on pa.id = ca.planned_activity_id
  where ca.id = p_id and ca.user_id = p_uid
$$;

create or replace function coach_private.activity_json(p_uid uuid, p_id uuid) returns jsonb
language sql stable as $$
  select jsonb_strip_nulls(jsonb_build_object(
    'session_key', pa.key, 'id', pa.id, 'plan_id', pa.plan_id, 'date', pa.activity_date, 'sport', pa.sport,
    'subtype', pa.subtype, 'title', pa.title, 'sort_order', pa.sort_order, 'status', pa.status,
    'confirmation_required', pa.confirmation_required,
    'confirmed_time_local', to_char(pa.confirmed_time_local, 'HH24:MI'),
    'provisional_time_local', to_char(pa.provisional_time_local, 'HH24:MI'),
    'target', pa.target, 'notes', pa.notes, 'version', pa.version, 'updated_by', pa.updated_by, 'updated_at', pa.updated_at,
    'exercises', (
      select jsonb_agg(coach_private.variant_json(p_uid, v) || jsonb_strip_nulls(jsonb_build_object(
          'position', et.position, 'is_priority', et.is_priority, 'target_sets', et.sets, 'target_reps', et.reps,
          'target_steps', et.steps, 'target_seconds', et.seconds, 'target_load_kg', et.load_kg,
          'rir_min', et.rir_min, 'rir_max', et.rir_max, 'notes', et.notes))
        || jsonb_build_object('reference', coach_private.variant_reference(p_uid, v.id))
        order by et.position)
      from exercise_targets et join exercise_variants v on v.id = et.variant_id
      where et.planned_activity_id = pa.id),
    -- Muscu publiée sans exercices détaillés : on expose les références de la même série
    -- (variante par variante), sans fabriquer de séance.
    'split_references', case when pa.sport = 'strength'
        and not exists (select 1 from exercise_targets et where et.planned_activity_id = pa.id) then (
      select jsonb_agg(coach_private.variant_json(p_uid, v)
               || jsonb_build_object('reference', coach_private.variant_reference(p_uid, v.id)) order by v.name, v.key)
      from exercise_variants v where v.user_id = p_uid and v.split = pa.subtype) end,
    'result', (select coach_private.completed_json(p_uid, ca.id) from completed_activities ca
               where ca.planned_activity_id = pa.id and ca.user_id = p_uid),
    'last_change', (select jsonb_build_object('at', cl.created_at, 'actor', cl.actor, 'action', cl.action,
                      'reason', cl.reason, 'previous', cl.old_value)
                    from change_log cl where cl.user_id = p_uid and cl.entity = 'planned_activity'
                      and cl.entity_id = pa.id and cl.action in ('revise', 'confirm')
                    order by cl.id desc limit 1)
  ))
  from planned_activities pa where pa.id = p_id and pa.user_id = p_uid
$$;

create or replace function coach_private.find_variant(p_uid uuid, p_ex jsonb) returns uuid
language plpgsql as $$
declare v_id uuid; nv jsonb := p_ex->'new_variant';
begin
  if nv is not null then
    insert into exercise_variants(user_id, key, split, name, equipment_variant, location, load_basis, performance_unit, notes)
    values (p_uid, nv->>'key', nv->>'split', nv->>'name', nv->>'equipment_variant', nv->>'location',
            coalesce(nv->>'load_basis', 'unknown')::load_basis, coalesce(nv->>'performance_unit', 'reps'), nv->>'notes')
    on conflict (user_id, key) do nothing;
    select id into v_id from exercise_variants where user_id = p_uid and key = nv->>'key';
    return v_id;
  end if;
  select id into v_id from exercise_variants where user_id = p_uid and key = p_ex->>'variant_key';
  if v_id is null then
    perform coach_private.fail(422, 'unknown_variant', jsonb_build_object('variant_key', p_ex->>'variant_key',
      'hint', 'utiliser une variante existante (machine précise) ou fournir new_variant'));
  end if;
  return v_id;
end $$;

create or replace function coach_private.insert_targets(p_uid uuid, p_activity uuid, p_exercises jsonb) returns void
language plpgsql as $$
declare ex jsonb; i int := 0;
begin
  delete from exercise_targets where planned_activity_id = p_activity and user_id = p_uid;
  for ex in select * from jsonb_array_elements(coalesce(p_exercises, '[]'::jsonb)) loop
    i := i + 1;
    insert into exercise_targets(user_id, planned_activity_id, variant_id, position, sets, reps, steps, seconds,
                                 load_kg, rir_min, rir_max, is_priority, notes)
    values (p_uid, p_activity, coach_private.find_variant(p_uid, ex), coalesce((ex->>'position')::int, i),
            (ex->>'sets')::int, (ex->>'reps')::int, (ex->>'steps')::int, (ex->>'seconds')::int,
            (ex->>'load_kg')::numeric, (ex->>'rir_min')::int, (ex->>'rir_max')::int,
            coalesce((ex->>'is_priority')::boolean, false), ex->>'notes');
  end loop;
end $$;

-- Insère une activité de brouillon (status proposed) dans un plan.
create or replace function coach_private.insert_draft_activity(p_uid uuid, p_plan training_plans, a jsonb, p_idx int)
returns uuid language plpgsql as $$
declare v_id uuid; v_date date := (a->>'date')::date; v_key text;
begin
  if v_date is null or v_date < p_plan.week_start or v_date > p_plan.week_end then
    perform coach_private.fail(422, 'activity_date_outside_week', jsonb_build_object('date', a->>'date',
      'week_start', p_plan.week_start));
  end if;
  if coalesce(a->>'status', 'planned') not in ('planned', 'pending_confirmation') then
    perform coach_private.fail(422, 'invalid_initial_status', jsonb_build_object('status', a->>'status',
      'hint', 'planned ou pending_confirmation uniquement ; un résultat se déclare via complete_activity'));
  end if;
  if a->>'title' is null or a->>'sport' is null then
    perform coach_private.fail(422, 'activity_incomplete', jsonb_build_object('activity', a));
  end if;
  v_key := coalesce(a->>'key', to_char(v_date, 'YYYYMMDD') || '_' || (a->>'sport') || coalesce('_' || (a->>'subtype'), ''));
  insert into planned_activities(user_id, plan_id, key, activity_date, sport, subtype, title, sort_order, status,
      confirmation_required, confirmed_time_local, provisional_time_local, target, notes, updated_by)
  values (p_uid, p_plan.id, v_key, v_date, a->>'sport', a->>'subtype', a->>'title',
      coalesce((a->>'sort_order')::int, p_idx), 'proposed',
      coalesce(a->>'status', 'planned') = 'pending_confirmation',
      (a->>'confirmed_time_local')::time, (a->>'provisional_time_local')::time,
      coalesce(a->'target', '{}'::jsonb), a->>'notes', 'claude_mcp')
  returning id into v_id;
  perform coach_private.insert_targets(p_uid, v_id, a->'exercises');
  return v_id;
end $$;

-- ================================================================ LECTURE
create or replace function get_profile() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid();
begin
  return jsonb_build_object(
    'profile', (select to_jsonb(p) - 'user_id' from athlete_profiles p where p.user_id = u),
    'today_local', coach_private.today(u),
    'goals', (select coalesce(jsonb_agg(to_jsonb(g) - 'user_id' - 'id' order by g.created_at), '[]') from goals g where g.user_id = u),
    'events', (select coalesce(jsonb_agg(to_jsonb(e) - 'user_id' order by e.event_date nulls last), '[]') from events e where e.user_id = u),
    'device_metrics', (select to_jsonb(d) - 'user_id' from device_metrics d where d.user_id = u order by d.recorded_on desc limit 1)
  );
end $$;

create or replace function get_day_plan(p_date date) returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); v_plan training_plans; v_acts jsonb;
begin
  select * into v_plan from training_plans
   where user_id = u and status = 'validated' and p_date between week_start and week_end;
  select coalesce(jsonb_agg(coach_private.activity_json(u, pa.id)
           order by pa.sort_order nulls last, pa.confirmed_time_local nulls last, pa.created_at), '[]')
    into v_acts
    from planned_activities pa
   where pa.user_id = u and pa.activity_date = p_date and pa.plan_id = v_plan.id and pa.status <> 'superseded';
  -- séances réalisées sans prescription (ex. séance spontanée) ce jour-là
  return jsonb_strip_nulls(jsonb_build_object(
    'date', p_date,
    'timezone', (select timezone from athlete_profiles where user_id = u),
    'plan_id', v_plan.id, 'plan_version', v_plan.version, 'plan_status', v_plan.status,
    'activities', v_acts,
    'unplanned_completed', (select jsonb_agg(coach_private.completed_json(u, ca.id))
                              from completed_activities ca
                             where ca.user_id = u and ca.activity_date = p_date and ca.planned_activity_id is null),
    'message', case when jsonb_array_length(v_acts) = 0 then 'Aucune séance publiée pour ce jour' end
  ));
end $$;

create or replace function list_week_plan(p_week_start date, p_include_drafts boolean default false) returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); ws date := coach_private.week_start_of(p_week_start); v_plan training_plans;
begin
  select * into v_plan from training_plans where user_id = u and week_start = ws and status = 'validated';
  return jsonb_strip_nulls(jsonb_build_object(
    'week_start', ws, 'week_end', ws + 6,
    'plan_id', v_plan.id, 'plan_version', v_plan.version, 'plan_status', v_plan.status,
    'rationale', v_plan.rationale, 'decision_note', v_plan.decision_note,
    'validated_at', v_plan.validated_at, 'updated_at', v_plan.updated_at,
    'days', (select jsonb_agg(jsonb_build_object(
                'date', d::date, 'isodow', extract(isodow from d)::int,
                'activities', (select coalesce(jsonb_agg(coach_private.activity_json(u, pa.id)
                                 order by pa.sort_order nulls last, pa.created_at), '[]')
                                 from planned_activities pa
                                where pa.user_id = u and pa.plan_id = v_plan.id and pa.activity_date = d::date
                                  and pa.status <> 'superseded'),
                'unplanned_completed', (select coalesce(jsonb_agg(coach_private.completed_json(u, ca.id)), '[]')
                                          from completed_activities ca
                                         where ca.user_id = u and ca.activity_date = d::date and ca.planned_activity_id is null))
              order by d)
              from generate_series(ws, ws + 6, interval '1 day') d),
    'drafts', case when p_include_drafts then (
       select jsonb_agg(jsonb_build_object('plan_id', tp.id, 'version', tp.version, 'rationale', tp.rationale,
                 'created_at', tp.created_at,
                 'activities', (select jsonb_agg(coach_private.activity_json(u, pa.id) order by pa.activity_date, pa.sort_order)
                                  from planned_activities pa where pa.plan_id = tp.id)))
         from training_plans tp where tp.user_id = u and tp.week_start = ws and tp.status = 'draft') end,
    'events', (select jsonb_agg(to_jsonb(e) - 'user_id' order by e.event_date) from events e
                where e.user_id = u and e.event_date between ws and ws + 6)
  ));
end $$;

create or replace function get_exercise_history(p_variant_key text, p_limit int default 10) returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); v exercise_variants;
begin
  select * into v from exercise_variants where user_id = u and key = p_variant_key;
  if not found then
    perform coach_private.fail(404, 'unknown_variant', jsonb_build_object('variant_key', p_variant_key,
      'available', (select jsonb_agg(key order by key) from exercise_variants where user_id = u)));
  end if;
  return jsonb_build_object(
    'variant', coach_private.variant_json(u, v),
    'reference', coach_private.variant_reference(u, v.id),
    'same_name_other_variants', (select coalesce(jsonb_agg(o.key), '[]') from exercise_variants o
                                  where o.user_id = u and o.name = v.name and o.id <> v.id),
    'sessions', (select coalesce(jsonb_agg(x order by (x->>'date') desc), '[]') from (
        select jsonb_strip_nulls(jsonb_build_object(
                 'date', ca.activity_date, 'completed_activity_id', ca.id, 'source', ca.source,
                 'session_rpe_10', ca.rpe_10, 'session_notes', ca.notes, 'planned_session_key', pa.key,
                 'work_sets', (select jsonb_agg(jsonb_strip_nulls(jsonb_build_object('set_index', s.set_index,
                                 'load_kg', s.load_kg, 'load_basis', s.load_basis, 'reps', s.reps, 'steps', s.steps,
                                 'seconds', s.seconds, 'rir', s.rir, 'notes', s.notes)) order by s.set_index)
                               from completed_sets s where s.completed_activity_id = ca.id and s.variant_id = v.id and not s.is_warmup),
                 'warmup_sets_count', (select nullif(count(*), 0) from completed_sets s
                                        where s.completed_activity_id = ca.id and s.variant_id = v.id and s.is_warmup),
                 'target_at_the_time', (select jsonb_strip_nulls(jsonb_build_object('sets', et.sets, 'reps', et.reps,
                                          'steps', et.steps, 'seconds', et.seconds, 'load_kg', et.load_kg))
                                        from exercise_targets et where et.planned_activity_id = pa.id and et.variant_id = v.id limit 1)
               )) as x
          from completed_activities ca left join planned_activities pa on pa.id = ca.planned_activity_id
         where ca.user_id = u and exists (select 1 from completed_sets s where s.completed_activity_id = ca.id and s.variant_id = v.id)
         order by ca.activity_date desc, ca.created_at desc
         limit greatest(1, least(coalesce(p_limit, 10), 100))) q)
  );
end $$;

create or replace function get_recent_completed_sessions(p_from_date date default null, p_limit int default 10) returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid();
begin
  return jsonb_build_object(
    'from_date', p_from_date,
    'sessions', (select coalesce(jsonb_agg(coach_private.completed_json(u, q.id) order by q.activity_date desc, q.created_at desc), '[]')
                   from (select ca.id, ca.activity_date, ca.created_at from completed_activities ca
                          where ca.user_id = u and (p_from_date is null or ca.activity_date >= p_from_date)
                          order by ca.activity_date desc, ca.created_at desc
                          limit greatest(1, least(coalesce(p_limit, 10), 100))) q),
    'note', 'Seules les séances réellement déclarées (app_logged, user_report, historical_dossier) figurent ici.'
  );
end $$;

create or replace function get_week_summary(p_week_start date) returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); ws date := coach_private.week_start_of(p_week_start); v_plan training_plans;
begin
  select * into v_plan from training_plans where user_id = u and week_start = ws and status = 'validated';
  return jsonb_build_object(
    'week_start', ws, 'week_end', ws + 6, 'plan_id', v_plan.id, 'plan_version', v_plan.version,
    'planned', (select jsonb_build_object(
        'running_km_firm', round(coalesce(sum((pa.target->>'distance_m')::numeric) filter (
              where pa.sport = 'running' and pa.status in ('planned','in_progress','completed') and not pa.confirmation_required), 0) / 1000, 2),
        'running_km_pending_confirmation', round(coalesce(sum((pa.target->>'distance_m')::numeric) filter (
              where pa.sport = 'running' and (pa.status = 'pending_confirmation' or pa.confirmation_required)
                and pa.status not in ('cancelled','skipped')), 0) / 1000, 2),
        'running_sessions_without_fixed_distance', count(*) filter (where pa.sport = 'running' and pa.target->>'distance_m' is null),
        'strength_sessions', count(*) filter (where pa.sport = 'strength' and pa.status not in ('cancelled','skipped')),
        'swim_sessions', count(*) filter (where pa.sport = 'swimming' and pa.status not in ('cancelled','skipped')),
        'football_firm', count(*) filter (where pa.sport = 'football' and pa.status in ('planned','completed','in_progress') and not pa.confirmation_required),
        'football_pending_confirmation', count(*) filter (where pa.sport = 'football' and pa.status = 'pending_confirmation'),
        'cancelled_or_skipped', count(*) filter (where pa.status in ('cancelled','skipped')))
      from planned_activities pa where pa.user_id = u and pa.plan_id = v_plan.id and pa.status <> 'superseded'),
    'actual', (select jsonb_build_object(
        'sessions_completed', count(*),
        'running_km', round(coalesce(sum(em.distance_m) filter (where ca.sport = 'running'), 0) / 1000, 2),
        'running_duration_s_known', sum(em.duration_s) filter (where ca.sport = 'running'),
        'running_sessions_missing_duration', count(*) filter (where ca.sport = 'running' and em.duration_s is null),
        'swim_m', sum(em.distance_m) filter (where ca.sport = 'swimming'),
        'strength_sessions', count(*) filter (where ca.sport = 'strength'),
        'strength_work_sets', (select count(*) from completed_sets s join completed_activities c2 on c2.id = s.completed_activity_id
                                where c2.user_id = u and c2.activity_date between ws and ws + 6 and not s.is_warmup),
        'football_sessions', count(*) filter (where ca.sport = 'football'))
      from completed_activities ca left join endurance_metrics em on em.completed_activity_id = ca.id
      where ca.user_id = u and ca.activity_date between ws and ws + 6),
    'note', 'Prévu ≠ réalisé. Les volumes « pending_confirmation » ne sont pas comptés dans le prévu ferme.'
  );
end $$;

create or replace function get_training_context(p_from date default null, p_to date default null) returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); t date := coach_private.today(u);
        f date := coalesce(p_from, t - 14); e date := coalesce(p_to, t + 14);
begin
  return jsonb_build_object(
    'today_local', t, 'range', jsonb_build_object('from', f, 'to', e),
    'profile', (select jsonb_strip_nulls(jsonb_build_object('age_years', age_years, 'height_cm', height_cm,
                  'current_weight_kg', current_weight_kg, 'timezone', timezone, 'constraints', constraints,
                  'coach_policy', coach_policy)) from athlete_profiles where user_id = u),
    'goals', (select coalesce(jsonb_agg(jsonb_build_object('key', key, 'sport', sport, 'target', target,
                 'deadline', deadline_text, 'status', status, 'priority', priority)), '[]') from goals where user_id = u),
    'upcoming_events', (select coalesce(jsonb_agg(to_jsonb(ev) - 'user_id' - 'id' order by ev.event_date nulls last), '[]')
                          from events ev where ev.user_id = u and (ev.event_date is null or ev.event_date >= t)),
    'planned_in_range', (select coalesce(jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
                            'session_key', pa.key, 'date', pa.activity_date, 'sport', pa.sport, 'subtype', pa.subtype,
                            'title', pa.title, 'status', pa.status, 'version', pa.version, 'target', pa.target,
                            'provisional_time_local', to_char(pa.provisional_time_local, 'HH24:MI')))
                            order by pa.activity_date, pa.sort_order), '[]')
                          from planned_activities pa join training_plans tp on tp.id = pa.plan_id and tp.status = 'validated'
                         where pa.user_id = u and pa.activity_date between f and e and pa.status <> 'superseded'),
    'completed_in_range', (select coalesce(jsonb_agg(coach_private.completed_json(u, ca.id) order by ca.activity_date desc), '[]')
                             from completed_activities ca where ca.user_id = u and ca.activity_date between f and e),
    'awaiting_confirmation', (select coalesce(jsonb_agg(jsonb_build_object('session_key', pa.key, 'date', pa.activity_date,
                                 'title', pa.title)), '[]')
                                from planned_activities pa join training_plans tp on tp.id = pa.plan_id and tp.status = 'validated'
                               where pa.user_id = u and pa.status = 'pending_confirmation'),
    'past_planned_without_result', (select coalesce(jsonb_agg(jsonb_build_object('session_key', pa.key, 'date', pa.activity_date,
                                       'title', pa.title, 'status', pa.status) order by pa.activity_date), '[]')
                                      from planned_activities pa join training_plans tp on tp.id = pa.plan_id and tp.status = 'validated'
                                     where pa.user_id = u and pa.activity_date < t and pa.activity_date >= f
                                       and pa.status in ('planned','pending_confirmation','in_progress')),
    'latest_device_metrics', (select to_jsonb(d) - 'user_id' - 'id' from device_metrics d where d.user_id = u order by recorded_on desc limit 1),
    'reminder', 'Une séance prévue n''est pas réalisée. Demander les résultats manquants plutôt que les supposer.'
  );
end $$;

-- ================================================================ ÉCRITURE — COACH (Claude via MCP)
create or replace function create_plan_draft(p_week_start date, p_activities jsonb, p_rationale text,
  p_idempotency_key text, p_actor text default 'claude_mcp') returns jsonb
language plpgsql volatile security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); ws date := coach_private.week_start_of(p_week_start);
        req jsonb := jsonb_build_object('w', ws, 'a', p_activities, 'r', p_rationale);
        prev jsonb; v_plan training_plans; a jsonb; i int := 0; res jsonb;
begin
  prev := coach_private.idem_lookup(u, p_idempotency_key, 'create_plan_draft', req);
  if prev is not null then return prev; end if;
  if jsonb_typeof(p_activities) <> 'array' or jsonb_array_length(p_activities) = 0 then
    perform coach_private.fail(422, 'activities_required');
  end if;
  insert into training_plans(user_id, week_start, week_end, status, rationale, source)
  values (u, ws, ws + 6, 'draft', p_rationale, 'claude_planned') returning * into v_plan;
  for a in select * from jsonb_array_elements(p_activities) loop
    i := i + 1;
    perform coach_private.insert_draft_activity(u, v_plan, a, i);
  end loop;
  perform coach_private.log(u, 'training_plan', v_plan.id, ws::text, 'create_draft', null, req, p_rationale, null, p_actor);
  res := jsonb_build_object('success', true, 'plan_id', v_plan.id, 'version', v_plan.version, 'status', 'draft',
           'week_start', ws, 'activities_count', i,
           'note', 'Brouillon NON visible dans l''application. Publier avec publish_validated_plan après accord explicite.');
  return coach_private.idem_store(u, p_idempotency_key, 'create_plan_draft', req, res);
end $$;

create or replace function publish_validated_plan(p_plan_id uuid, p_expected_version int, p_activities jsonb default null,
  p_user_approval_reference text default null, p_idempotency_key text default null, p_actor text default 'claude_mcp') returns jsonb
language plpgsql volatile security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid();
        req jsonb := jsonb_build_object('p', p_plan_id, 'v', p_expected_version, 'a', p_activities, 'ap', p_user_approval_reference);
        prev jsonb; d training_plans; v_old training_plans; a jsonb; i int := 0; r record;
        superseded jsonb := '[]'; preserved jsonb := '[]'; affected jsonb; res jsonb;
begin
  prev := coach_private.idem_lookup(u, p_idempotency_key, 'publish_validated_plan', req);
  if prev is not null then return prev; end if;
  perform coach_private.require_approval(p_user_approval_reference);

  select * into d from training_plans where id = p_plan_id and user_id = u for update;
  if not found then perform coach_private.fail(404, 'plan_not_found', jsonb_build_object('plan_id', p_plan_id)); end if;
  if d.status <> 'draft' then
    perform coach_private.fail(409, 'plan_not_draft', jsonb_build_object('status', d.status, 'version', d.version));
  end if;
  if d.version <> p_expected_version then
    perform coach_private.fail(409, 'version_conflict', jsonb_build_object('expected', p_expected_version, 'current', d.version));
  end if;

  if p_activities is not null then   -- version finale ajustée pendant la discussion
    delete from planned_activities where plan_id = d.id and user_id = u;
    for a in select * from jsonb_array_elements(p_activities) loop
      i := i + 1; perform coach_private.insert_draft_activity(u, d, a, i);
    end loop;
  end if;

  select * into v_old from training_plans where user_id = u and week_start = d.week_start and status = 'validated' for update;
  if found then
    -- 1) une activité terminée/en cours ne peut pas être remplacée par une proposition
    for r in select pa.key, pa.status from planned_activities pa
              join planned_activities nd on nd.plan_id = d.id and nd.key = pa.key
             where pa.plan_id = v_old.id and pa.status in ('completed', 'in_progress') loop
      perform coach_private.fail(409, 'completed_activity_protected', jsonb_build_object('session_key', r.key, 'status', r.status,
        'hint', 'retirer cette activité du plan publié ; un résultat se corrige via correct_completed_activity'));
    end loop;
    -- 2) on conserve dans le nouveau plan ce que l'utilisateur a déclaré : terminé / en cours, et aussi
    --    « pas fait » (skipped) ou annulé, sauf si le nouveau plan reprogramme explicitement la même clé
    for r in update planned_activities pa set plan_id = d.id
              where pa.plan_id = v_old.id and pa.user_id = u
                and (pa.status in ('completed', 'in_progress')
                     or (pa.status in ('skipped', 'cancelled')
                         and not exists (select 1 from planned_activities nd where nd.plan_id = d.id and nd.key = pa.key)))
              returning pa.key loop
      preserved := preserved || to_jsonb(r.key);
    end loop;
    -- 3) le reste de l'ancien plan est remplacé (historique conservé)
    for r in update planned_activities set status = 'superseded', version = version + 1, updated_by = p_actor
              where plan_id = v_old.id and user_id = u and status <> 'superseded' returning id, key loop
      superseded := superseded || to_jsonb(r.key);
      perform coach_private.log(u, 'planned_activity', r.id, r.key, 'supersede', null, null,
        'remplacé par le plan ' || d.id, p_user_approval_reference, p_actor);
    end loop;
    update training_plans set status = 'superseded' where id = v_old.id;
  end if;

  update planned_activities
     set status = case when confirmation_required then 'pending_confirmation'::activity_status else 'planned'::activity_status end,
         updated_by = p_actor
   where plan_id = d.id and user_id = u and status = 'proposed';

  update training_plans
     set status = 'validated', version = greatest(d.version, coalesce(v_old.version, 0)) + 1,
         user_approval_reference = p_user_approval_reference, validated_at = now()
   where id = d.id returning * into d;

  perform coach_private.log(u, 'training_plan', d.id, d.week_start::text, 'publish',
    case when v_old.id is not null then jsonb_build_object('previous_plan_id', v_old.id, 'previous_version', v_old.version) end,
    jsonb_build_object('version', d.version), d.rationale, p_user_approval_reference, p_actor);

  select coalesce(jsonb_agg(jsonb_build_object('session_key', key, 'date', activity_date, 'status', status, 'version', version)
            order by activity_date, sort_order), '[]') into affected
    from planned_activities where plan_id = d.id and user_id = u;
  res := jsonb_build_object('success', true, 'plan_id', d.id, 'version', d.version, 'status', 'validated',
           'week_start', d.week_start, 'updated_at', d.updated_at, 'affected_sessions', affected,
           'superseded_sessions', superseded, 'preserved_completed_sessions', preserved);
  return coach_private.idem_store(u, p_idempotency_key, 'publish_validated_plan', req, res);
end $$;

create or replace function revise_session(p_session_key text, p_expected_version int, p_patch jsonb, p_reason text,
  p_user_approval_reference text, p_idempotency_key text, p_actor text default 'claude_mcp') returns jsonb
language plpgsql volatile security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid();
        req jsonb := jsonb_build_object('k', p_session_key, 'v', p_expected_version, 'p', p_patch, 'r', p_reason, 'ap', p_user_approval_reference);
        prev jsonb; pa planned_activities; tp training_plans; before jsonb; after jsonb; k text; t jsonb; res jsonb;
        allowed text[] := array['title','target','target_replace','notes','activity_date','sort_order',
                                'provisional_time_local','confirmed_time_local','status','exercises'];
begin
  prev := coach_private.idem_lookup(u, p_idempotency_key, 'revise_session', req);
  if prev is not null then return prev; end if;
  perform coach_private.require_approval(p_user_approval_reference);
  if p_reason is null or length(btrim(p_reason)) < 3 then perform coach_private.fail(422, 'reason_required'); end if;
  for k in select jsonb_object_keys(p_patch) loop
    if not k = any(allowed) then
      perform coach_private.fail(422, 'patch_field_not_allowed', jsonb_build_object('field', k, 'allowed', to_jsonb(allowed)));
    end if;
  end loop;

  select pa2.* into pa from planned_activities pa2 join training_plans tp2 on tp2.id = pa2.plan_id and tp2.status = 'validated'
   where pa2.user_id = u and pa2.key = p_session_key and pa2.status not in ('superseded','proposed')
   for update of pa2;
  if not found then
    perform coach_private.fail(404, 'session_not_found', jsonb_build_object('session_key', p_session_key));
  end if;
  if pa.status in ('completed', 'in_progress') then
    perform coach_private.fail(409, 'completed_activity_protected', jsonb_build_object('session_key', pa.key, 'status', pa.status,
      'hint', 'une séance réalisée ne se replanifie pas ; corriger le résultat via correct_completed_activity'));
  end if;
  if pa.version <> p_expected_version then
    perform coach_private.fail(409, 'version_conflict', jsonb_build_object('expected', p_expected_version,
      'current', pa.version, 'current_state', coach_private.activity_json(u, pa.id)));
  end if;
  if p_patch ? 'status' and p_patch->>'status' not in ('planned','pending_confirmation','cancelled','skipped') then
    perform coach_private.fail(422, 'status_not_allowed', jsonb_build_object('status', p_patch->>'status',
      'hint', 'completed uniquement via un résultat déclaré'));
  end if;
  select * into tp from training_plans where id = pa.plan_id for update;
  if p_patch ? 'activity_date' and ((p_patch->>'activity_date')::date < tp.week_start or (p_patch->>'activity_date')::date > tp.week_end) then
    perform coach_private.fail(422, 'activity_date_outside_week');
  end if;

  before := coach_private.activity_json(u, pa.id);
  -- fusion de target : les clés à null sont supprimées
  t := case when p_patch ? 'target_replace' then p_patch->'target_replace'
            when p_patch ? 'target' then jsonb_strip_nulls(pa.target || (p_patch->'target'))
            else pa.target end;
  update planned_activities set
      title = coalesce(p_patch->>'title', title),
      target = t,
      notes = case when p_patch ? 'notes' then p_patch->>'notes' else notes end,
      activity_date = coalesce((p_patch->>'activity_date')::date, activity_date),
      sort_order = coalesce((p_patch->>'sort_order')::int, sort_order),
      provisional_time_local = case when p_patch ? 'provisional_time_local' then (p_patch->>'provisional_time_local')::time else provisional_time_local end,
      confirmed_time_local = case when p_patch ? 'confirmed_time_local' then (p_patch->>'confirmed_time_local')::time else confirmed_time_local end,
      status = coalesce((p_patch->>'status')::activity_status, status),
      version = version + 1, updated_by = p_actor
   where id = pa.id returning * into pa;
  if p_patch ? 'exercises' then perform coach_private.insert_targets(u, pa.id, p_patch->'exercises'); end if;
  update training_plans set version = version + 1 where id = tp.id returning * into tp;
  after := coach_private.activity_json(u, pa.id);
  perform coach_private.log(u, 'planned_activity', pa.id, pa.key, 'revise', before, after, p_reason, p_user_approval_reference, p_actor);
  res := jsonb_build_object('success', true, 'session_key', pa.key, 'version', pa.version, 'plan_id', tp.id,
           'plan_version', tp.version, 'updated_at', pa.updated_at,
           'previous', jsonb_build_object('title', before->>'title', 'target', before->'target', 'status', before->>'status', 'date', before->>'date'),
           'current', jsonb_build_object('title', after->>'title', 'target', after->'target', 'status', after->>'status', 'date', after->>'date'));
  return coach_private.idem_store(u, p_idempotency_key, 'revise_session', req, res);
end $$;

create or replace function mark_event_confirmation(p_ref_key text, p_confirmed_status text, p_user_approval_reference text,
  p_idempotency_key text, p_confirmed_time_local text default null, p_actor text default 'claude_mcp') returns jsonb
language plpgsql volatile security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid();
        req jsonb := jsonb_build_object('k', p_ref_key, 's', p_confirmed_status, 'ap', p_user_approval_reference, 't', p_confirmed_time_local);
        prev jsonb; pa planned_activities; ev events; res jsonb; new_status activity_status;
begin
  prev := coach_private.idem_lookup(u, p_idempotency_key, 'mark_event_confirmation', req);
  if prev is not null then return prev; end if;
  perform coach_private.require_approval(p_user_approval_reference);
  if p_confirmed_status not in ('confirmed', 'declined', 'cancelled') then
    perform coach_private.fail(422, 'invalid_confirmation_status', jsonb_build_object('allowed', jsonb_build_array('confirmed','declined','cancelled'),
      'hint', 'une confirmation ne vaut jamais réalisation'));
  end if;

  select pa2.* into pa from planned_activities pa2 join training_plans tp on tp.id = pa2.plan_id and tp.status = 'validated'
   where pa2.user_id = u and pa2.key = p_ref_key and pa2.status not in ('superseded','proposed') for update of pa2;
  if found then
    if pa.status in ('completed','in_progress') then
      perform coach_private.fail(409, 'completed_activity_protected', jsonb_build_object('session_key', pa.key));
    end if;
    new_status := case when p_confirmed_status = 'confirmed' then 'planned' else 'cancelled' end;
    update planned_activities set status = new_status, confirmation_required = false,
           confirmed_time_local = coalesce(p_confirmed_time_local::time, confirmed_time_local),
           version = version + 1, updated_by = p_actor
     where id = pa.id;
    perform coach_private.log(u, 'planned_activity', pa.id, pa.key, 'confirm', jsonb_build_object('status', pa.status),
      jsonb_build_object('status', new_status), 'confirmation: ' || p_confirmed_status, p_user_approval_reference, p_actor);
    select * into pa from planned_activities where id = pa.id;
    res := jsonb_build_object('success', true, 'kind', 'planned_activity', 'session_key', pa.key, 'status', pa.status, 'version', pa.version);
  else
    select * into ev from events where user_id = u and key = p_ref_key for update;
    if not found then
      perform coach_private.fail(404, 'reference_not_found', jsonb_build_object('ref_key', p_ref_key));
    end if;
    update events set registration_status = p_confirmed_status, version = version + 1 where id = ev.id;
    perform coach_private.log(u, 'event', ev.id, ev.key, 'confirm', jsonb_build_object('registration_status', ev.registration_status),
      jsonb_build_object('registration_status', p_confirmed_status), null, p_user_approval_reference, p_actor);
    res := jsonb_build_object('success', true, 'kind', 'event', 'event_key', ev.key, 'registration_status', p_confirmed_status,
             'version', ev.version + 1);
  end if;
  return coach_private.idem_store(u, p_idempotency_key, 'mark_event_confirmation', req, res);
end $$;

-- ================================================================ ÉCRITURE — RÉSULTATS (Android, ou déclaration en conversation)
create or replace function coach_private.write_result_details(u uuid, ca_id uuid, p jsonb) returns void
language plpgsql as $$
declare s jsonb; e jsonb := p->'endurance'; v_id uuid; v_basis load_basis;
begin
  delete from completed_sets where completed_activity_id = ca_id and user_id = u;
  for s in select * from jsonb_array_elements(coalesce(p->'sets', '[]'::jsonb)) loop
    v_id := coach_private.find_variant(u, s);
    select load_basis into v_basis from exercise_variants where id = v_id;
    insert into completed_sets(user_id, completed_activity_id, variant_id, exercise_position, set_index, is_warmup,
        load_kg, load_basis, reps, steps, seconds, distance_m, rir, notes)
    values (u, ca_id, v_id, (s->>'exercise_position')::int, (s->>'set_index')::int, coalesce((s->>'is_warmup')::boolean, false),
        (s->>'load_kg')::numeric, coalesce((s->>'load_basis')::load_basis, v_basis), (s->>'reps')::int, (s->>'steps')::int,
        (s->>'seconds')::int, (s->>'distance_m')::numeric, (s->>'rir')::int, s->>'notes');
  end loop;
  delete from endurance_metrics where completed_activity_id = ca_id and user_id = u;
  if e is not null and e <> '{}'::jsonb then
    insert into endurance_metrics(completed_activity_id, user_id, distance_m, duration_s, reported_pace_s_per_km, ascent_m,
        avg_hr, max_hr, effort_type, surface, pool_length_m, extra)
    values (ca_id, u, (e->>'distance_m')::numeric, (e->>'duration_s')::int, (e->>'reported_pace_s_per_km')::numeric,
        (e->>'ascent_m')::int, (e->>'avg_hr')::int, (e->>'max_hr')::int, e->>'effort_type', e->>'surface',
        (e->>'pool_length_m')::int, coalesce(e->'extra', '{}'::jsonb));
  end if;
end $$;

create or replace function complete_activity(p_payload jsonb, p_idempotency_key text, p_actor text default 'android_app') returns jsonb
language plpgsql volatile security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); prev jsonb; pa planned_activities; ca completed_activities; res jsonb;
        v_src provenance := case when p_actor = 'claude_mcp' then 'user_report'::provenance else 'app_logged'::provenance end;
        v_sport text; v_date date;
begin
  prev := coach_private.idem_lookup(u, p_idempotency_key, 'complete_activity', p_payload);
  if prev is not null then return prev; end if;

  if p_payload ? 'planned_session_key' then
    select pa2.* into pa from planned_activities pa2 join training_plans tp on tp.id = pa2.plan_id and tp.status = 'validated'
     where pa2.user_id = u and pa2.key = p_payload->>'planned_session_key' and pa2.status not in ('superseded','proposed')
     for update of pa2;
    if not found then
      perform coach_private.fail(404, 'session_not_found', jsonb_build_object('session_key', p_payload->>'planned_session_key'));
    end if;
    if pa.status = 'completed' then
      perform coach_private.fail(409, 'already_completed', jsonb_build_object('session_key', pa.key,
        'hint', 'corriger via correct_completed_activity (avec raison) au lieu de re-terminer'));
    end if;
  end if;
  v_sport := coalesce(p_payload->>'sport', pa.sport);
  v_date := coalesce((p_payload->>'date')::date, pa.activity_date);
  if v_sport is null or v_date is null then perform coach_private.fail(422, 'sport_and_date_required'); end if;
  if v_sport = 'strength' and not exists (select 1 from jsonb_array_elements(coalesce(p_payload->'sets','[]')) s
                                           where not coalesce((s->>'is_warmup')::boolean, false)) then
    perform coach_private.fail(422, 'work_sets_required', jsonb_build_object('hint', 'au moins une série de travail (l''échauffement ne compte pas)'));
  end if;
  if v_sport <> 'strength' and p_payload->'endurance' is null and p_payload->'rpe_10' is null and p_payload->'notes' is null then
    perform coach_private.fail(422, 'result_required');
  end if;

  insert into completed_activities(user_id, planned_activity_id, activity_date, sport, subtype, rpe_10,
      planned_session_type, actual_session_type, notes, started_at, ended_at, source, idempotency_key)
  values (u, pa.id, v_date, v_sport, coalesce(p_payload->>'subtype', pa.subtype), (p_payload->>'rpe_10')::numeric,
      coalesce(p_payload->>'planned_session_type', pa.target->>'intensity'), p_payload->>'actual_session_type', p_payload->>'notes',
      (p_payload->>'started_at')::timestamptz, (p_payload->>'ended_at')::timestamptz, v_src, p_idempotency_key)
  returning * into ca;
  perform coach_private.write_result_details(u, ca.id, p_payload);

  if pa.id is not null then
    update planned_activities set status = 'completed', version = version + 1, updated_by = p_actor where id = pa.id;
    perform coach_private.log(u, 'planned_activity', pa.id, pa.key, 'complete', jsonb_build_object('status', pa.status),
      jsonb_build_object('status', 'completed', 'completed_activity_id', ca.id), null, null, p_actor);
  end if;
  perform coach_private.log(u, 'completed_activity', ca.id, pa.key, 'create', null, p_payload, null, null, p_actor);
  res := jsonb_build_object('success', true, 'completed_activity_id', ca.id, 'planned_session_key', pa.key,
           'source', v_src, 'version', ca.version, 'result', coach_private.completed_json(u, ca.id));
  return coach_private.idem_store(u, p_idempotency_key, 'complete_activity', p_payload, res);
end $$;

create or replace function correct_completed_activity(p_completed_activity_id uuid, p_expected_version int, p_patch jsonb,
  p_reason text, p_idempotency_key text, p_actor text default 'android_app') returns jsonb
language plpgsql volatile security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid();
        req jsonb := jsonb_build_object('id', p_completed_activity_id, 'v', p_expected_version, 'p', p_patch, 'r', p_reason);
        prev jsonb; ca completed_activities; before jsonb; after jsonb; res jsonb; merged jsonb;
begin
  prev := coach_private.idem_lookup(u, p_idempotency_key, 'correct_completed_activity', req);
  if prev is not null then return prev; end if;
  if p_reason is null or length(btrim(p_reason)) < 3 then perform coach_private.fail(422, 'reason_required'); end if;
  select * into ca from completed_activities where id = p_completed_activity_id and user_id = u for update;
  if not found then perform coach_private.fail(404, 'completed_activity_not_found'); end if;
  if ca.version <> p_expected_version then
    perform coach_private.fail(409, 'version_conflict', jsonb_build_object('expected', p_expected_version, 'current', ca.version,
      'current_state', coach_private.completed_json(u, ca.id)));
  end if;
  before := coach_private.completed_json(u, ca.id);
  update completed_activities set
      rpe_10 = case when p_patch ? 'rpe_10' then (p_patch->>'rpe_10')::numeric else rpe_10 end,
      notes = case when p_patch ? 'notes' then p_patch->>'notes' else notes end,
      actual_session_type = case when p_patch ? 'actual_session_type' then p_patch->>'actual_session_type' else actual_session_type end,
      activity_date = coalesce((p_patch->>'date')::date, activity_date),
      version = version + 1
   where id = ca.id returning * into ca;
  if p_patch ? 'sets' or p_patch ? 'endurance' then
    -- remplacement explicite : on reconstruit à partir de l'état précédent + patch
    merged := jsonb_build_object(
      'sets', case when p_patch ? 'sets' then p_patch->'sets' else (
          select coalesce(jsonb_agg(jsonb_build_object('variant_key', v.key, 'exercise_position', s.exercise_position,
                   'set_index', s.set_index, 'is_warmup', s.is_warmup, 'load_kg', s.load_kg, 'load_basis', s.load_basis,
                   'reps', s.reps, 'steps', s.steps, 'seconds', s.seconds, 'distance_m', s.distance_m, 'rir', s.rir, 'notes', s.notes)), '[]')
          from completed_sets s join exercise_variants v on v.id = s.variant_id where s.completed_activity_id = ca.id) end,
      'endurance', case when p_patch ? 'endurance' then
          coalesce((select to_jsonb(em) - 'completed_activity_id' - 'user_id' - 'pace_s_per_km'
                    from endurance_metrics em where em.completed_activity_id = ca.id), '{}'::jsonb) || (p_patch->'endurance')
        else (select to_jsonb(em) - 'completed_activity_id' - 'user_id' - 'pace_s_per_km'
              from endurance_metrics em where em.completed_activity_id = ca.id) end);
    perform coach_private.write_result_details(u, ca.id, merged);
  end if;
  after := coach_private.completed_json(u, ca.id);
  perform coach_private.log(u, 'completed_activity', ca.id, ca.key, 'correct', before, after, p_reason, null, p_actor);
  res := jsonb_build_object('success', true, 'completed_activity_id', ca.id, 'version', ca.version, 'result', after);
  return coach_private.idem_store(u, p_idempotency_key, 'correct_completed_activity', req, res);
end $$;

create or replace function save_wellness_check(p_payload jsonb, p_idempotency_key text) returns jsonb
language plpgsql volatile security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); prev jsonb; w wellness_checks; res jsonb;
begin
  prev := coach_private.idem_lookup(u, p_idempotency_key, 'save_wellness_check', p_payload);
  if prev is not null then return prev; end if;
  insert into wellness_checks(user_id, check_date, completed_activity_id, fatigue_10, pain, sleep_quality_10, energy_10, could_continue, answers)
  values (u, coalesce((p_payload->>'date')::date, coach_private.today(u)), (p_payload->>'completed_activity_id')::uuid,
          (p_payload->>'fatigue_10')::int, p_payload->>'pain', (p_payload->>'sleep_quality_10')::int, (p_payload->>'energy_10')::int,
          p_payload->>'could_continue', coalesce(p_payload->'answers', '{}'::jsonb))
  returning * into w;
  res := jsonb_build_object('success', true, 'wellness_check_id', w.id);
  return coach_private.idem_store(u, p_idempotency_key, 'save_wellness_check', p_payload, res);
end $$;

-- ================================================================ RGPD : export & suppression
create or replace function export_my_data() returns jsonb
language plpgsql stable security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); res jsonb := jsonb_build_object('exported_at', now(), 'format_version', 1); t text; r jsonb;
begin
  foreach t in array array['athlete_profiles','goals','events','exercise_variants','exercise_references','training_plans',
      'planned_activities','exercise_targets','completed_activities','completed_sets','endurance_metrics','benchmarks',
      'wellness_checks','device_metrics','change_log'] loop
    execute format('select coalesce(jsonb_agg(to_jsonb(x) - ''user_id''), ''[]'') from %I x where x.user_id = $1', t) using u into r;
    res := res || jsonb_build_object(t, r);
  end loop;
  return res;
end $$;

-- Supprime toutes les données sportives de l'utilisateur. La suppression du compte d'authentification
-- elle-même se fait côté Supabase Auth (voir docs/DEPLOIEMENT.md) ; ON DELETE CASCADE couvre alors le reste.
create or replace function delete_my_data(p_confirmation text) returns jsonb
language plpgsql volatile security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); t text; n bigint; total bigint := 0;
begin
  if p_confirmation is distinct from 'SUPPRIMER TOUTES MES DONNEES' then
    perform coach_private.fail(422, 'confirmation_required', jsonb_build_object('expected', 'SUPPRIMER TOUTES MES DONNEES'));
  end if;
  foreach t in array array['sync_operations','change_log','wellness_checks','completed_sets','endurance_metrics',
      'completed_activities','exercise_targets','planned_activities','training_plans','exercise_references','exercise_variants',
      'benchmarks','device_metrics','events','goals','athlete_profiles'] loop
    execute format('delete from %I where user_id = $1', t) using u;
    get diagnostics n = row_count; total := total + n;
  end loop;
  return jsonb_build_object('success', true, 'rows_deleted', total);
end $$;

-- ================================================================ droits
revoke all on all functions in schema coach_private from public;
revoke execute on function
  get_profile(), get_day_plan(date), list_week_plan(date, boolean), get_exercise_history(text, int),
  get_recent_completed_sessions(date, int), get_week_summary(date), get_training_context(date, date),
  create_plan_draft(date, jsonb, text, text, text),
  publish_validated_plan(uuid, int, jsonb, text, text, text),
  revise_session(text, int, jsonb, text, text, text, text),
  mark_event_confirmation(text, text, text, text, text, text),
  complete_activity(jsonb, text, text), correct_completed_activity(uuid, int, jsonb, text, text, text),
  save_wellness_check(jsonb, text), export_my_data(), delete_my_data(text), touch_updated_at()
from public, anon;
grant execute on function
  get_profile(), get_day_plan(date), list_week_plan(date, boolean), get_exercise_history(text, int),
  get_recent_completed_sessions(date, int), get_week_summary(date), get_training_context(date, date),
  create_plan_draft(date, jsonb, text, text, text),
  publish_validated_plan(uuid, int, jsonb, text, text, text),
  revise_session(text, int, jsonb, text, text, text, text),
  mark_event_confirmation(text, text, text, text, text, text),
  complete_activity(jsonb, text, text), correct_completed_activity(uuid, int, jsonb, text, text, text),
  save_wellness_check(jsonb, text), export_my_data(), delete_my_data(text)
to authenticated;
