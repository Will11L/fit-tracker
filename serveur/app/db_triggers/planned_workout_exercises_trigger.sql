IF TG_TABLE_NAME = 'planned_workout_exercises' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'planned_workout_exercise_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('planned_workout_exercises', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'planned_workout_exercise_updated',
      'payload', jsonb_build_object(
        'id',                 rec.id,
        'uuid',               rec.uuid,
        'plannedWorkoutUUID', rec.planned_workout_uuid,
        'exerciseUUID',       rec.exercise_uuid,
        'sets',               rec.sets,
        'reps',               rec.reps,
        'phase',              rec.phase,
        'status',             rec.status,
        'order',              rec."order",
        'ignored',            rec.ignored,

        'updatedAt',          iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('planned_workout_exercises', rec.uuid)
    );
  END IF;
END IF;
