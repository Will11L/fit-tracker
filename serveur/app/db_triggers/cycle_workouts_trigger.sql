IF TG_TABLE_NAME = 'cycle_workouts' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'cycle_workout_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('cycle_workouts', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'cycle_workout_updated',
      'payload', jsonb_build_object(
        'id',                 rec.id,
        'uuid',               rec.uuid,
        'trainingCycleUUID',  rec.training_cycle_uuid,
        'plannedWorkoutUUID', rec.planned_workout_uuid,
        'updatedAt',          iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('cycle_workouts', rec.uuid)
    );
  END IF;
END IF;