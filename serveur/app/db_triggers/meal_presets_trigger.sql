IF TG_TABLE_NAME = 'meal_presets' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'meal_preset_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('meal_presets', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'meal_preset_updated',
      'payload', jsonb_build_object(
        'id',          rec.id,
        'uuid',        rec.uuid,
        'userId',      rec.user_id,
        'name',        rec.name,
        'orderIndex',  rec.order_index,
        'defaultTime', rec.default_time,

        'updatedAt',   iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('meal_presets', rec.uuid)
    );
  END IF;
END IF;
