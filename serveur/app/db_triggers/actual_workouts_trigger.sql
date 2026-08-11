IF TG_TABLE_NAME = 'actual_workouts' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'actual_workout_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('actual_workouts', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'actual_workout_updated',
      'payload', jsonb_build_object(
        'id',        rec.id,
        'uuid',      rec.uuid,
        'userId',    get_user_id_for('actual_workouts', rec.uuid),
        'name',      rec.name,
        'date',      rec.date,
        'notes',     rec.notes,
        'location',  rec.location,
        'isDone',    rec.is_done,
        'updatedAt', iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('actual_workouts', rec.uuid)
    );
  END IF;
END IF;
