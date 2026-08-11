-- ============================================
-- Fonction utilitaire pour récupérer le user_id
-- ============================================
CREATE OR REPLACE FUNCTION get_user_id_for(table_name text, record_uuid text)
RETURNS INT AS $$
DECLARE
  uid INT;
BEGIN
  CASE table_name
    WHEN 'muscles' THEN
      SELECT m.user_id INTO uid FROM muscles m WHERE m.uuid = record_uuid;

    WHEN 'exercises' THEN
      SELECT e.user_id INTO uid FROM exercises e WHERE e.uuid = record_uuid;
    
    WHEN 'exercise_equipment' THEN
      SELECT e.user_id INTO uid
      FROM exercises e
      JOIN exercise_equipment ee ON ee.exercise_uuid = e.uuid
      WHERE ee.uuid = record_uuid;

    WHEN 'exercise_muscles' THEN
      SELECT e.user_id INTO uid
      FROM exercises e
      JOIN exercise_muscles em ON em.exercise_uuid = e.uuid
      WHERE em.uuid = record_uuid;
    
    WHEN 'actual_workouts' THEN
      SELECT aw.user_id INTO uid FROM actual_workouts aw WHERE aw.uuid = record_uuid;

    WHEN 'actual_workout_exercises' THEN
      SELECT aw.user_id INTO uid
      FROM actual_workouts aw
      JOIN actual_workout_exercises awe ON awe.actual_workout_uuid = aw.uuid
      WHERE awe.uuid = record_uuid;

    WHEN 'actual_workout_sets' THEN
      SELECT aw.user_id INTO uid
      FROM actual_workouts aw
      JOIN actual_workout_exercises awe ON awe.actual_workout_uuid = aw.uuid
      JOIN actual_workout_sets aws ON aws.actual_workout_exercise_uuid = awe.uuid
      WHERE aws.uuid = record_uuid;

    WHEN 'muscle_goals' THEN
      SELECT mg.user_id INTO uid
      FROM muscle_goals mg
      WHERE mg.uuid = record_uuid;

    WHEN 'planned_workouts' THEN
      SELECT pw.user_id INTO uid
      FROM planned_workouts pw
      WHERE pw.uuid = record_uuid;

    WHEN 'planned_workout_exercises' THEN
      SELECT pw.user_id INTO uid
      FROM planned_workouts pw
      JOIN planned_workout_exercises pwe ON pwe.planned_workout_uuid = pw.uuid
      WHERE pwe.uuid = record_uuid;

    WHEN 'notifications' THEN
      SELECT n.user_id INTO uid FROM notifications n WHERE n.uuid = record_uuid;

    WHEN 'quotes' THEN
      SELECT q.user_id INTO uid FROM quotes q WHERE q.uuid = record_uuid;

    WHEN 'routine_periods' THEN
      SELECT rp.user_id INTO uid FROM routine_periods rp WHERE rp.uuid = record_uuid;

    WHEN 'tasks' THEN
      -- Phase 0 (2026-05-12) : unification routine_tasks -> tasks (toutes recurrences).
      SELECT t.user_id INTO uid FROM tasks t WHERE t.uuid = record_uuid;

    WHEN 'task_checks' THEN
      SELECT tc.user_id INTO uid FROM task_checks tc WHERE tc.uuid = record_uuid;

    WHEN 'superset_groups' THEN
      SELECT sg.user_id INTO uid
      FROM superset_groups sg
      WHERE sg.uuid = record_uuid;

    WHEN 'superset_exercises' THEN
      SELECT sg.user_id INTO uid
      FROM superset_groups sg
      JOIN superset_exercises se ON se.superset_group_uuid = sg.uuid
      WHERE se.uuid = record_uuid;

    WHEN 'training_cycles' THEN
      -- V5.7 (2026-05-05) : training_cycles a maintenant une colonne user_id directe
      -- (Type A user-scoped). Lookup direct, plus de JOIN fragile vers planned_workouts.
      SELECT tc.user_id INTO uid FROM training_cycles tc WHERE tc.uuid = record_uuid;

    WHEN 'cycle_workouts' THEN
      SELECT pw.user_id INTO uid
      FROM planned_workouts pw
      JOIN cycle_workouts cw ON cw.planned_workout_uuid = pw.uuid
      WHERE cw.uuid = record_uuid;

    WHEN 'available_equipments' THEN
      -- F8-Q2 (2026-05-06) : bascule Type C global -> Type A user-scoped.
      SELECT ae.user_id INTO uid FROM available_equipments ae WHERE ae.uuid = record_uuid;

    -- Nutrition V1 (2026-06-12, cf. docs/NUTRITION_DESIGN.md)
    WHEN 'foods' THEN
      SELECT f.user_id INTO uid FROM foods f WHERE f.uuid = record_uuid;

    WHEN 'food_portions' THEN
      SELECT f.user_id INTO uid
      FROM foods f
      JOIN food_portions fp ON fp.food_uuid = f.uuid
      WHERE fp.uuid = record_uuid;

    WHEN 'recipes' THEN
      SELECT r.user_id INTO uid FROM recipes r WHERE r.uuid = record_uuid;

    WHEN 'recipe_ingredients' THEN
      SELECT r.user_id INTO uid
      FROM recipes r
      JOIN recipe_ingredients ri ON ri.recipe_uuid = r.uuid
      WHERE ri.uuid = record_uuid;

    WHEN 'meal_presets' THEN
      SELECT mp.user_id INTO uid FROM meal_presets mp WHERE mp.uuid = record_uuid;

    WHEN 'meals' THEN
      SELECT me.user_id INTO uid FROM meals me WHERE me.uuid = record_uuid;

    WHEN 'meal_entries' THEN
      SELECT me.user_id INTO uid
      FROM meals me
      JOIN meal_entries men ON men.meal_uuid = me.uuid
      WHERE men.uuid = record_uuid;

    WHEN 'nutrition_goals' THEN
      SELECT ng.user_id INTO uid FROM nutrition_goals ng WHERE ng.uuid = record_uuid;

    -- Santé / Health Connect V1 (2026-06-17) — Type A user-scoped direct.
    WHEN 'health_step_counts' THEN
      SELECT hsc.user_id INTO uid FROM health_step_counts hsc WHERE hsc.uuid = record_uuid;

    WHEN 'health_metrics' THEN
      SELECT hm.user_id INTO uid FROM health_metrics hm WHERE hm.uuid = record_uuid;

    WHEN 'health_goals' THEN
      SELECT hg.user_id INTO uid FROM health_goals hg WHERE hg.uuid = record_uuid;

    -- Hydratation (2026-07-05) — Type A user-scoped direct.
    WHEN 'water_intakes' THEN
      SELECT wi.user_id INTO uid FROM water_intakes wi WHERE wi.uuid = record_uuid;

    ELSE
      uid := NULL; -- fallback si pas de user_id
  END CASE;

  RETURN uid;
END;
$$ LANGUAGE plpgsql STABLE;
