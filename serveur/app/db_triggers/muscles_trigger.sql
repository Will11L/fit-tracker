IF TG_TABLE_NAME = 'muscles' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'muscle_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('muscles', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'muscle_updated',
      'payload', jsonb_build_object(
        'id',          rec.id,
        'uuid',        rec.uuid,
        'userId',      get_user_id_for('muscles', rec.uuid),
        'name',        rec.name,
        'muscleGroup', rec.muscle_group,
        'zone',        rec.zone,
        'isFavorite',  rec.is_favorite,
        'updatedAt',   iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('muscles', rec.uuid)
    );
  END IF;
END IF;
