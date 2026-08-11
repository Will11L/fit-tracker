IF TG_TABLE_NAME = 'equipments' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'equipment_deleted',
      'uuid',   rec.uuid
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'equipment_updated',
      'payload', jsonb_build_object(
        'id',         rec.id,
        'uuid',       rec.uuid,
        'name',       rec.name,
        'updatedAt',  iso_utc(rec.updated_at)
      )
    );
  END IF;
END IF;