IF TG_TABLE_NAME = 'superset_groups' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'superset_group_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('superset_groups', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'superset_group_updated',
      'payload', jsonb_build_object(
        'id',         rec.id,
        'uuid',       rec.uuid,
        'userId',     get_user_id_for('superset_groups', rec.uuid),
        'name',       rec.name,
        'updatedAt',  iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('superset_groups', rec.uuid)
    );
  END IF;
END IF;