IF TG_TABLE_NAME = 'health_metrics' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'health_metric_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('health_metrics', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'health_metric_updated',
      'payload', jsonb_build_object(
        'id',         rec.id,
        'uuid',       rec.uuid,
        'userId',     rec.user_id,
        'type',       rec.type,
        'value',      rec.value,
        'unit',       rec.unit,
        'date',       rec.date,
        'startTime',  rec.start_time,
        'updatedAt',  iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('health_metrics', rec.uuid)
    );
  END IF;
END IF;
