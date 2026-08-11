IF TG_TABLE_NAME = 'quotes' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'quote_deleted',
      'uuid',   rec.uuid,
      'userId', get_user_id_for('quotes', rec.uuid)
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'quote_updated',
      'payload', jsonb_build_object(
        'id',         rec.id,
        'uuid',       rec.uuid,
        'userId',     rec.user_id,
        'text',       rec.text,
        'author',     rec.author,
        'updatedAt',  iso_utc(rec.updated_at)
      ),
      'userId', get_user_id_for('quotes', rec.uuid)
    );
  END IF;
END IF;
