IF TG_TABLE_NAME = 'recipes' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'recipe_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('recipes', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'recipe_updated',
      'payload', jsonb_build_object(
        'id',           rec.id,
        'uuid',         rec.uuid,
        'userId',       rec.user_id,
        'name',         rec.name,
        'kind',         rec.kind,
        'totalWeightG', rec.total_weight_g,

        'updatedAt',    iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('recipes', rec.uuid)
    );
  END IF;
END IF;
