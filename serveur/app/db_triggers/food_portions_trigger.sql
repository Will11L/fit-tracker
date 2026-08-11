IF TG_TABLE_NAME = 'food_portions' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'food_portion_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('food_portions', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'food_portion_updated',
      'payload', jsonb_build_object(
        'id',        rec.id,
        'uuid',      rec.uuid,
        'foodUUID',  rec.food_uuid,
        'label',     rec.label,
        'grams',     rec.grams,

        'updatedAt', iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('food_portions', rec.uuid)
    );
  END IF;
END IF;
