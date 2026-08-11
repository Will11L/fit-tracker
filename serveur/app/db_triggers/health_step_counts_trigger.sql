IF TG_TABLE_NAME = 'health_step_counts' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'health_step_count_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('health_step_counts', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'health_step_count_updated',
      'payload', jsonb_build_object(
        'id',           rec.id,
        'uuid',         rec.uuid,
        'userId',       rec.user_id,
        'date',         rec.date,
        'bucketStart',  rec.bucket_start,
        'steps',        rec.steps,
        'updatedAt',    iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('health_step_counts', rec.uuid)
    );
  END IF;
END IF;
