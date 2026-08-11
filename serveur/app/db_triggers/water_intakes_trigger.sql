IF TG_TABLE_NAME = 'water_intakes' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'water_intake_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('water_intakes', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'water_intake_updated',
      'payload', jsonb_build_object(
        'id',        rec.id,
        'uuid',      rec.uuid,
        'userId',    rec.user_id,
        'date',      rec.date,
        'amountMl',  rec.amount_ml,
        'createdAt', iso_utc(rec.created_at),
        'updatedAt', iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('water_intakes', rec.uuid)
    );
  END IF;
END IF;
