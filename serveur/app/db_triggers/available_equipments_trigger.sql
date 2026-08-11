IF TG_TABLE_NAME = 'available_equipments' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'available_equipment_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('available_equipments', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'available_equipment_updated',
      'payload', jsonb_build_object(
        'id',        rec.id,
        'uuid',      rec.uuid,
        'userId',    get_user_id_for('available_equipments', rec.uuid),
        'name',      rec.name,
        'updatedAt', iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('available_equipments', rec.uuid)
    );
  END IF;
END IF;
