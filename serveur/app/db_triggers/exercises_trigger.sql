IF TG_TABLE_NAME = 'exercises' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'exercise_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('exercises', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'exercise_updated',
      'payload', jsonb_build_object(
        'id',                rec.id,
        'uuid',              rec.uuid,
        'userId',            get_user_id_for('exercises', rec.uuid),
        'name',              rec.name,
        'description',       rec.description,
        'instructions',      rec.instructions,
        'recommendedSets',   rec.recommended_sets,
        'recommendedReps',   rec.recommended_reps,
        'durationInSeconds', rec.duration_in_seconds,
        'restTimeSeconds',   rec.rest_time_seconds,
        'gifUrl',            rec.gif_url,
        'isFavorite',        rec.is_favorite,
        'lastDone',          rec.last_done,
        'updatedAt',         iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('exercises', rec.uuid)
    );
  END IF;
END IF;
