IF TG_TABLE_NAME = 'superset_exercises' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'superset_exercise_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('superset_exercises', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'superset_exercise_updated',
      'payload', jsonb_build_object(
        'id',         rec.id,
        'uuid',       rec.uuid,
        'supersetGroupUUID', rec.superset_group_uuid,
        'exerciseUUID', rec.exercise_uuid,
        'orderInGroup', rec.order_in_group,
        'updatedAt',  iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('superset_exercises', rec.uuid)
    );
  END IF;
END IF;