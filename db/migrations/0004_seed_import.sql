-- 0004_seed_import.sql — import du jeu initial (seed/03_DONNEES_INITIALES.json) pour l'utilisateur connecté.
-- Appel unique : select import_initial_dataset('<json>'::jsonb);  (refusé si un profil existe déjà)
-- Fidélité : aucune valeur inventée, NULL quand la donnée manque, provenance historical_dossier/user_report.

create or replace function coach_private.map_load_basis(p text) returns load_basis
language sql immutable as $$
  select case p when 'total' then 'total' when 'per_hand' then 'per_hand' when 'machine' then 'machine'
                when 'bodyweight' then 'bodyweight' else 'unknown' end::load_basis
$$;

create or replace function import_initial_dataset(p jsonb) returns jsonb
language plpgsql volatile security definer set search_path = public, pg_temp as $$
declare u uuid := coach_private.uid(); x jsonb; s jsonb; ex jsonb; v_id uuid; ca_id uuid; pa_id uuid;
        plan training_plans; wp jsonb := p->'weekly_plan'; i int; j int; k int; unit text;
        n_variants int := 0; n_completed int := 0; n_planned int := 0; pr jsonb := p->'profile';
begin
  if exists (select 1 from athlete_profiles where user_id = u) then
    perform coach_private.fail(409, 'already_imported', jsonb_build_object('hint', 'le jeu initial ne s''importe qu''une fois'));
  end if;

  -- profil
  insert into athlete_profiles(user_id, age_years, age_as_of, sex_reported, height_cm, current_weight_kg, timezone,
      equipment, constraints, preferences, coach_policy, extra)
  values (u, (pr->>'age_years_as_of_2026_11_03')::int, (p->>'as_of')::date, pr->>'sex_reported', (pr->>'height_cm')::numeric,
      (pr->>'current_weight_kg')::numeric, coalesce(p->>'timezone', 'Europe/Paris'), coalesce(pr->'equipment', '[]'),
      pr->>'past_injuries', coalesce(pr->'preferences', '[]'), coalesce(p->'coach_policy', '{}'),
      jsonb_strip_nulls(jsonb_build_object('sports', pr->'sports', 'weight_history_range_2026_kg_approx', pr->'weight_history_range_2026_kg_approx',
        'strength_sessions_per_week_target', pr->'strength_sessions_per_week_target',
        'football_training_usual_days', pr->'football_training_usual_days', 'coaching_principle', pr->'coaching_principle',
        'nutrition_preferences', pr->'nutrition_preferences', 'unconfirmed_questions', p->'unconfirmed_questions',
        'dataset_version', p->'dataset_version', 'provenance', p->'provenance')));

  -- objectifs
  for x in select * from jsonb_array_elements(coalesce(p->'goals', '[]')) loop
    insert into goals(user_id, key, sport, description, target, deadline_text, status, priority, source)
    values (u, x->>'id', x->>'sport', x->>'target', x - 'id' - 'sport' - 'status' - 'priority',
            coalesce(x->>'target_date_window', x->>'event_date'), coalesce(x->>'status', x->>'registration_status', 'unknown'),
            x->>'priority', 'historical_dossier');
  end loop;

  -- événements + matchs connus
  for x in select * from jsonb_array_elements(coalesce(p->'events', '[]')) loop
    insert into events(user_id, key, name, event_date, date_window, sport, category, distance_m, registration_status, strategy, extra)
    values (u, x->>'id', x->>'name', (x->>'date')::date, x->>'date_window',
            case when x->>'id' like 'hyrox%' then 'hyrox' else 'running' end, x->>'category', (x->>'distance_m')::int,
            x->>'registration_status', x->>'strategy',
            x - 'id' - 'name' - 'date' - 'date_window' - 'category' - 'distance_m' - 'registration_status' - 'strategy');
  end loop;
  for x in select * from jsonb_array_elements(coalesce(p->'football_known_match_dates', '[]')) loop
    insert into events(user_id, key, name, event_date, sport, category, registration_status, extra)
    values (u, 'football_match_' || (x #>> '{}'), 'Match de football', (x #>> '{}')::date, 'football', 'match', 'scheduled',
            jsonb_build_object('note', 'heure, intensité et temps de jeu à confirmer'));
  end loop;

  -- Garmin
  if p ? 'garmin_snapshot' then
    x := p->'garmin_snapshot';
    insert into device_metrics(user_id, recorded_on, device, max_hr, rest_hr_approx, lthr, threshold_pace_s_per_km,
        threshold_power_w, zones, method, is_estimate, caveats, source)
    values (u, (x->>'recorded_date')::date, x->>'device', (x->>'max_hr_bpm_reported')::int, (x->>'rest_hr_bpm_historic_approx')::int,
        (x#>>'{lactate_threshold_estimate,hr_bpm}')::int, (x#>>'{lactate_threshold_estimate,pace_sec_per_km}')::int,
        (x#>>'{lactate_threshold_estimate,power_w}')::int,
        jsonb_build_object('bpm', x->'zones_reported_bpm_approx', 'watch_setting_scope_confirmed', x->'watch_setting_scope_confirmed',
                           'relative_power_w_per_kg', x#>'{lactate_threshold_estimate,relative_power_w_per_kg}'),
        x->>'hr_zone_method_selected', true, coalesce(x->'caveats', '[]'), 'user_report');
  end if;

  -- variantes d'exercices + références historiques
  for x in select * from jsonb_array_elements(coalesce(p->'exercise_references', '[]')) loop
    unit := case when x#>'{reference,sets,0}' ? 'steps' then 'steps'
                 when x->'reference' ? 'sets_seconds' then 'seconds'
                 when x->'reference' ? 'round_trips' then 'round_trips'
                 else 'reps' end;
    insert into exercise_variants(user_id, key, split, name, equipment_variant, load_basis, performance_unit, notes)
    values (u, x->>'exercise_variant_id', x->>'split', x->>'name', x->>'equipment_variant',
            coach_private.map_load_basis(x->>'load_basis'), unit, x->>'notes')
    returning id into v_id;
    insert into exercise_references(user_id, variant_id, reference, reference_type, last_complete_session_date,
        future_target_not_validated, priority_symbol, notes, source)
    values (u, v_id, x->'reference', x->>'reference_type', (x->>'last_complete_session_date_if_known')::date,
            x->'future_target_not_validated', x->>'priority_symbol', x->>'notes', 'historical_dossier');
    n_variants := n_variants + 1;
  end loop;

  -- repères historiques (fractionné, anciens records, triathlon…)
  for x in select * from jsonb_array_elements(coalesce(p->'historical_benchmarks', '[]')) loop
    insert into benchmarks(user_id, key, date_text, status, payload)
    values (u, x->>'id', x->>'date', x->>'status', x - 'id' - 'date' - 'status');
  end loop;

  -- séances d'endurance réalisées
  for x in select * from jsonb_array_elements(coalesce(p->'completed_endurance_activities', '[]')) loop
    insert into completed_activities(user_id, key, activity_date, sport, subtype, rpe_10, rpe_range_reported,
        planned_session_type, actual_session_type, notes, source)
    values (u, x->>'id', (x->>'date')::date,
            case when x->>'sport' = 'running_or_walk_recorded' then 'running' else x->>'sport' end,
            case when x->>'sport' = 'running_or_walk_recorded' then 'recorded_as_walk' when x->>'sport' = 'football' then 'training' end,
            (x->>'rpe_10')::numeric,
            (select array_agg(v::numeric) from jsonb_array_elements_text(x->'rpe_range_reported') v),
            x->>'planned_session_type', x->>'actual_session_type', x->>'notes',
            coalesce(x->>'provenance', 'user_report')::provenance)
    returning id into ca_id;
    insert into endurance_metrics(completed_activity_id, user_id, distance_m, duration_s, reported_pace_s_per_km, ascent_m, avg_hr, max_hr)
    values (ca_id, u, (x->>'distance_m')::numeric, (x->>'duration_seconds')::int, (x->>'avg_pace_seconds_per_km_reported')::numeric,
            (x->>'ascent_m')::int, (x->>'avg_hr_bpm')::int, (x->>'max_hr_bpm')::int);
    n_completed := n_completed + 1;
  end loop;

  -- séances de musculation réalisées
  for x in select * from jsonb_array_elements(coalesce(p->'completed_strength_activities', '[]')) loop
    insert into completed_activities(user_id, key, activity_date, sport, subtype, rpe_10, notes, source)
    values (u, x->>'id', (x->>'date')::date, 'strength', x->>'split', (x->>'rpe_10')::numeric, x->>'notes', 'user_report')
    returning id into ca_id;
    i := 0;
    for ex in select * from jsonb_array_elements(x->'exercises') loop
      i := i + 1; j := 0;
      select id into v_id from exercise_variants where user_id = u and key = ex->>'exercise_variant_id';
      if v_id is null then perform coach_private.fail(422, 'unknown_variant', jsonb_build_object('key', ex->>'exercise_variant_id')); end if;
      for s in select * from jsonb_array_elements(ex->'sets') loop
        j := j + 1;
        insert into completed_sets(user_id, completed_activity_id, variant_id, exercise_position, set_index, is_warmup,
            load_kg, load_basis, reps, steps, seconds)
        values (u, ca_id, v_id, i, j, false, (s->>'load_kg')::numeric,
            (select load_basis from exercise_variants where id = v_id), (s->>'reps')::int, (s->>'steps')::int, (s->>'seconds')::int);
      end loop;
    end loop;
    n_completed := n_completed + 1;
  end loop;

  -- planning validé de la semaine
  if wp is not null then
    insert into training_plans(user_id, week_start, week_end, status, version, rationale, decision_note,
        user_approval_reference, source, validated_at)
    values (u, (wp->>'week_start')::date, (wp->>'week_end')::date, 'validated', 1,
        'Semaine validée dans l''ancien suivi (Conseil) — import du dossier du 03/11/2026',
        p#>>'{weekly_plan,forecast_summary,note}',
        'Planning validé par l''utilisateur avant le 03/11/2026 (dossier de transmission)', 'historical_dossier', now())
    returning * into plan;
    k := 0;
    for x in select * from jsonb_array_elements(wp->'activities') loop
      k := k + 1;
      insert into planned_activities(user_id, plan_id, key, activity_date, sport, subtype, title, sort_order, status,
          confirmation_required, provisional_time_local, target, notes, updated_by)
      values (u, plan.id, x->>'id', (x->>'date')::date, x->>'sport',
          case when x->>'sport' = 'strength' then coalesce(x#>>'{details,split}',
                    case when x->>'title' ilike 'quadriceps%' then 'quadriceps' end)
               when x->>'sport' = 'football' then case when x->>'title' ilike 'match%' then 'match' else 'training' end
               when x->>'id' like '%trail%' then 'trail' end,
          x->>'title', k, (x->>'status')::activity_status,
          x->>'status' = 'pending_confirmation',
          (x#>>'{details,provisional_time_local}')::time,
          (coalesce(x->'details', '{}') - 'actual_activity_id' - 'provisional_time_local' - 'split'
             - 'planned_distance_m' - 'planned_pace_s_per_km_range')
            || jsonb_strip_nulls(jsonb_build_object('distance_m', x#>'{details,planned_distance_m}',
                 'pace_s_per_km_range', x#>'{details,planned_pace_s_per_km_range}')),
          x->>'notes', 'import')
      returning id into pa_id;
      if x#>>'{details,actual_activity_id}' is not null then
        update completed_activities set planned_activity_id = pa_id
         where user_id = u and key = x#>>'{details,actual_activity_id}';
        if not found then
          perform coach_private.fail(422, 'unknown_completed_activity', jsonb_build_object('key', x#>>'{details,actual_activity_id}'));
        end if;
      end if;
      n_planned := n_planned + 1;
    end loop;
    perform coach_private.log(u, 'training_plan', plan.id, plan.week_start::text, 'import', null,
      jsonb_build_object('dataset_version', p->>'dataset_version'), 'import initial', null, 'import');
  end if;

  return jsonb_build_object('success', true, 'variants', n_variants, 'completed_activities', n_completed,
           'planned_activities', n_planned, 'plan_id', plan.id);
end $$;

revoke execute on function import_initial_dataset(jsonb) from public, anon;
grant execute on function import_initial_dataset(jsonb) to authenticated;
revoke all on all functions in schema coach_private from public;
