IF TG_TABLE_NAME = 'meal_entries' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'meal_entry_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('meal_entries', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'meal_entry_updated',
      'payload', jsonb_build_object(
        'id',              rec.id,
        'uuid',            rec.uuid,
        'mealUUID',        rec.meal_uuid,
        'foodUUID',        rec.food_uuid,
        'recipeUUID',      rec.recipe_uuid,
        'displayName',     rec.display_name,
        'quantityG',       rec.quantity_g,
        'portionLabel',    rec.portion_label,
        'kcalPer100g',     rec.kcal_per_100g,
        'proteinPer100g',  rec.protein_per_100g,
        'carbsPer100g',    rec.carbs_per_100g,
        'fatPer100g',      rec.fat_per_100g,
        'fiberPer100g',    rec.fiber_per_100g,
        'sugarPer100g',    rec.sugar_per_100g,
        'satFatPer100g',   rec.sat_fat_per_100g,
        'saltPer100g',     rec.salt_per_100g,
        'ironPer100g',        rec.iron_per_100g,
        'calciumPer100g',     rec.calcium_per_100g,
        'magnesiumPer100g',   rec.magnesium_per_100g,
        'zincPer100g',        rec.zinc_per_100g,
        'potassiumPer100g',   rec.potassium_per_100g,
        'sodiumPer100g',      rec.sodium_per_100g,
        'vitaminCPer100g',    rec.vitamin_c_per_100g,
        'vitaminDPer100g',    rec.vitamin_d_per_100g,
        'vitaminB12Per100g',  rec.vitamin_b12_per_100g,
        'vitaminAPer100g',    rec.vitamin_a_per_100g,

        'updatedAt',       iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('meal_entries', rec.uuid)
    );
  END IF;
END IF;
