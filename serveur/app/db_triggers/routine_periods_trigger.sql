IF TG_TABLE_NAME = 'routine_periods' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'routine_period_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('routine_periods', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'routine_period_updated',
      'payload', jsonb_build_object(
        'id',         rec.id,
        'uuid',       rec.uuid,
        'name',       rec.name,
        'startTime',  rec.start_time,
        'endTime',    rec.end_time,
        'order',      rec.order_index,
        'updatedAt',  iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('routine_periods', rec.uuid)
    );
  END IF;
END IF;
