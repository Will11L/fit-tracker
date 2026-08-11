IF TG_TABLE_NAME = 'planned_workouts' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'planned_workout_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('planned_workouts', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'planned_workout_updated',
      'payload', jsonb_build_object(
        'id',         rec.id,
        'uuid',       rec.uuid,
        'userId',     get_user_id_for('planned_workouts', rec.uuid),
        'name',       rec.name,
        'dayOfWeek',  rec.day_of_week,

        'updatedAt',  iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('planned_workouts', rec.uuid)
    );
  END IF;
END IF;