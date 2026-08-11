IF TG_TABLE_NAME = 'nutrition_goals' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'nutrition_goal_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('nutrition_goals', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'nutrition_goal_updated',
      'payload', jsonb_build_object(
        'id',            rec.id,
        'uuid',          rec.uuid,
        'userId',        rec.user_id,
        'effectiveFrom', rec.effective_from,
        'dayKind',       rec.day_kind,
        'kcal',          rec.kcal,
        'proteinG',      rec.protein_g,
        'carbsG',        rec.carbs_g,
        'fatG',          rec.fat_g,

        'updatedAt',     iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('nutrition_goals', rec.uuid)
    );
  END IF;
END IF;
