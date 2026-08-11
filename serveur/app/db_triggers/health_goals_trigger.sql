IF TG_TABLE_NAME = 'health_goals' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'health_goal_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('health_goals', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'health_goal_updated',
      'payload', jsonb_build_object(
        'id',            rec.id,
        'uuid',          rec.uuid,
        'userId',        rec.user_id,
        'type',          rec.type,
        'target',        rec.target,
        'effectiveFrom', rec.effective_from,
        'updatedAt',     iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('health_goals', rec.uuid)
    );
  END IF;
END IF;
