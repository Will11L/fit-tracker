IF TG_TABLE_NAME = 'exercise_equipment' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'exercise_equipment_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('exercise_equipment', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'exercise_equipment_updated',
      'payload', jsonb_build_object(
        'id',             rec.id,
        'uuid',           rec.uuid,
        'exerciseUUID',   rec.exercise_uuid,
        'equipmentUUID',  rec.equipment_uuid,
        'updatedAt',      iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('exercise_equipment', rec.uuid)
    );
  END IF;
END IF;
