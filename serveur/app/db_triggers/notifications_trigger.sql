IF TG_TABLE_NAME = 'notifications' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'notification_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('notifications', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'notification_updated',
      'payload', jsonb_build_object(
        'id',          rec.id,
        'uuid',        rec.uuid,
        'type',        rec.type,
        'level',       rec.level,
        'title',       rec.title,
        'body',        rec.body,
        'data',        rec.data,
        'dedupeKey',   rec.dedupe_key,
        'createdAt',   iso_utc(rec.created_at),
        'readAt',      iso_utc(rec.read_at),
        'archivedAt',  iso_utc(rec.archived_at),
        'updatedAt',   iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('notifications', rec.uuid)
    );
  END IF;
END IF;
