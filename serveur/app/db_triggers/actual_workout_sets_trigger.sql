IF TG_TABLE_NAME = 'actual_workout_sets' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'actual_workout_set_deleted',
      'uuid',   rec.uuid,
      'userId', (
        SELECT get_user_id_for('actual_workouts', awe.actual_workout_uuid)
        FROM actual_workout_exercises awe
        WHERE awe.uuid = rec.actual_workout_exercise_uuid
      )
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'actual_workout_set_updated',
      'payload', jsonb_build_object(
        'id',                        rec.id,
        'uuid',                      rec.uuid,
        'actualWorkoutExerciseUUID', rec.actual_workout_exercise_uuid,
        'setOrder',                  rec.set_order,
        'reps',                      rec.reps,
        'weight',                    rec.weight,
        'isDropset',                 rec.is_dropset,
        'notes',                     rec.notes,
        'recommendation',            rec.recommendation,
        'status',                    rec.status,
        'updatedAt',                 iso_utc(rec.updated_at)
      ),
      'userId', (
        SELECT get_user_id_for('actual_workouts', awe.actual_workout_uuid)
        FROM actual_workout_exercises awe
        WHERE awe.uuid = rec.actual_workout_exercise_uuid
      )
    );
  END IF;
END IF;
