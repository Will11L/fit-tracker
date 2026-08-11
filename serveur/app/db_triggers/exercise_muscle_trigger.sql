IF TG_TABLE_NAME = 'exercise_muscles' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'exercise_muscle_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('exercise_muscles', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'exercise_muscle_updated',
      'payload', jsonb_build_object(
        'id',           rec.id,
        'uuid',         rec.uuid,
        'exerciseUUID', rec.exercise_uuid,
        'muscleUUID',   rec.muscle_uuid,
        'coefficient',  rec.coefficient,
        'updatedAt',    iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('exercise_muscles', rec.uuid)
    );
  END IF;
END IF;
