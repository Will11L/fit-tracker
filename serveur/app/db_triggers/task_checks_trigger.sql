IF TG_TABLE_NAME = 'task_checks' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'task_check_deleted',
      'uuid',   rec.uuid,
      'userId', rec.user_id
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'task_check_updated',
      'payload', jsonb_build_object(
        'id',             rec.id,
        'uuid',           rec.uuid,
        'userId',         rec.user_id,
        'taskUUID',       rec.task_uuid,
        'occurrenceDate', rec.occurrence_date,
        'isChecked',      rec.is_checked,
        'checkedAt',      iso_utc(rec.checked_at),
        'updatedAt',      iso_utc(rec.updated_at)
      ),
      'userId', rec.user_id
    );
  END IF;
END IF;
