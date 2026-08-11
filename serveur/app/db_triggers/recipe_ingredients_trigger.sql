IF TG_TABLE_NAME = 'recipe_ingredients' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'recipe_ingredient_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('recipe_ingredients', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'recipe_ingredient_updated',
      'payload', jsonb_build_object(
        'id',         rec.id,
        'uuid',       rec.uuid,
        'recipeUUID', rec.recipe_uuid,
        'foodUUID',   rec.food_uuid,
        'quantityG',  rec.quantity_g,
        'orderIndex', rec.order_index,

        'updatedAt',  iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('recipe_ingredients', rec.uuid)
    );
  END IF;
END IF;
