IF TG_TABLE_NAME = 'tasks' THEN
  IF op = 'delete' THEN
    payload := jsonb_build_object(
      'type',   'task_deleted',
      'uuid',   rec.uuid,
      'userId', rec.user_id
    );
  ELSE
    payload := jsonb_build_object(
      'type',   'task_updated',
      'payload', jsonb_build_object(
        'id',                    rec.id,
        'uuid',                  rec.uuid,
        'userId',                rec.user_id,
        'title',                 rec.title,
        'notes',                 rec.notes,
        'isActive',              rec.is_active,
        'order',                 rec.order_index,
        'recurrenceKind',        rec.recurrence_kind,
        'dueDate',               rec.due_date,
        'dueTime',               rec.due_time,
        'periodUUID',            rec.period_uuid,
        'recurrenceWeekdays',    rec.recurrence_weekdays,
        'recurrenceStartDate',   rec.recurrence_start_date,
        'recurrenceEndDate',     rec.recurrence_end_date,
        'excludedDates',         rec.excluded_dates,
        'reminderMinutesBefore', rec.reminder_minutes_before,
        'updatedAt',             iso_utc(rec.updated_at)
      ),
      'userId', rec.user_id
    );
  END IF;
END IF;
