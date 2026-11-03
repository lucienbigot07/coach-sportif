-- 0002_rls.sql — Row Level Security : chaque utilisateur ne voit/écrit que ses lignes.
-- Les écritures métier passent UNIQUEMENT par les fonctions RPC (0003), en SECURITY DEFINER :
--   * elles filtrent explicitement chaque requête sur auth.uid() ;
--   * FORCE ROW LEVEL SECURITY applique aussi les politiques au propriétaire des tables
--     (défense en profondeur, tant que ce rôle n'a pas BYPASSRLS) ;
--   * auth.uid() provient du JWT de l'utilisateur, y compris pour les appels du serveur MCP.

do $$ declare t text; begin
  foreach t in array array['athlete_profiles','goals','events','exercise_variants','exercise_references',
                           'training_plans','planned_activities','exercise_targets','completed_activities',
                           'completed_sets','endurance_metrics','benchmarks','wellness_checks','device_metrics',
                           'change_log','sync_operations'] loop
    execute format('alter table %I enable row level security', t);
    execute format('alter table %I force row level security', t);
    execute format('drop policy if exists owner_all on %I', t);
    execute format($p$create policy owner_all on %I for all to public
                     using (user_id = auth.uid()) with check (user_id = auth.uid())$p$, t);
    execute format('revoke all on %I from anon, public', t);
  end loop;
end $$;

-- Lecture directe autorisée (le client Android lit ses données) ; écritures directes limitées.
grant select on athlete_profiles, goals, events, exercise_variants, exercise_references, training_plans,
  planned_activities, exercise_targets, completed_activities, completed_sets, endurance_metrics,
  benchmarks, wellness_checks, device_metrics, change_log to authenticated;

-- Pas d'écriture directe sur les tables protégées par versionnement : uniquement via RPC.
-- (wellness_checks et athlete_profiles restent modifiables directement par le propriétaire.)
grant insert, update on wellness_checks, athlete_profiles to authenticated;
-- Le journal est en ajout seul : ni update ni delete, même pour le propriétaire.
revoke update, delete on change_log from authenticated;
