IF TG_TABLE_NAME = 'muscle_goals' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'muscle_goal_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('muscle_goals', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'muscle_goal_updated',
      'payload', jsonb_build_object(
        'id',             rec.id,
        'uuid',           rec.uuid,
        'userId',         get_user_id_for('muscle_goals', rec.uuid),
        'muscleUUID',     rec.muscle_uuid,
        'priority',       rec.priority,
        'done',           rec.done,
        'target',         rec.target,
        'weekISO',        rec.week_iso,
        'status',         rec.status,
        'addedManually',  rec.added_manually,
        'updatedAt',      iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('muscle_goals', rec.uuid)
    );
  END IF;
END IF;