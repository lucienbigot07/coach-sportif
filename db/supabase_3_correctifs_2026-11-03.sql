-- 0005 : une republication de la semaine conserve aussi les séances marquées « pas fait » / annulées.

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

-- ---------------------------------------------------------------- corrections du 3 novembre
select set_config('request.jwt.claims', json_build_object('sub', (select id from auth.users limit 1), 'role', 'authenticated')::text, false);

-- Piscine et foot « pas faits » : on les remet visibles dans le planning en cours
with cur as (select id from training_plans where status = 'validated' and week_start = '2026-11-02'),
last as (select distinct on (key) id, key from planned_activities
         where key in ('20261103_swim', '20261103_football') and notes like 'Pas fait%'
         order by key, updated_at desc)
update planned_activities pa set plan_id = (select id from cur), status = 'skipped'
from last where pa.id = last.id;

select 'correctifs appliqués' as resultat;
