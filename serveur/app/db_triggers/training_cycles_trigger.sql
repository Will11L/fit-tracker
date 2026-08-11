IF TG_TABLE_NAME = 'training_cycles' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'training_cycle_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('training_cycles', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'training_cycle_updated',
      'payload', jsonb_build_object(
        'id',         rec.id,
        'uuid',       rec.uuid,
        'name',       rec.name,
        'startDate',  rec.start_date,
        'endDate',    rec.end_date,
        'updatedAt',  iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('training_cycles', rec.uuid)
    );
  END IF;
END IF;
