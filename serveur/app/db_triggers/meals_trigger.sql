IF TG_TABLE_NAME = 'meals' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'meal_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('meals', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'meal_updated',
      'payload', jsonb_build_object(
        'id',         rec.id,
        'uuid',       rec.uuid,
        'userId',     rec.user_id,
        'date',       rec.date,
        'name',       rec.name,
        'orderIndex', rec.order_index,
        'time',       rec.time,
        'presetUuid', rec.preset_uuid,

        'updatedAt',  iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('meals', rec.uuid)
    );
  END IF;
END IF;
