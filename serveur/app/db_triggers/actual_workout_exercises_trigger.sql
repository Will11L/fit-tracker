IF TG_TABLE_NAME = 'actual_workout_exercises' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'actual_workout_exercise_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('actual_workouts', rec.actual_workout_uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'actual_workout_exercise_updated',
      'payload', jsonb_build_object(
        'id',                rec.id,
        'uuid',              rec.uuid,
        'actualWorkoutUUID', rec.actual_workout_uuid,
        'exerciseUUID',      rec.exercise_uuid,
        'sets',              rec.sets,
        'reps',              rec.reps,
        'phase',             rec.phase,
        'status',            rec.status,
        'order',             rec."order",
        'addedManually',     rec.added_manually,
        'updatedAt',         iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('actual_workouts', rec.actual_workout_uuid)
    );
  END IF;
END IF;
