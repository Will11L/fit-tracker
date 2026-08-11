# app/schemas/task_schema.py
#
# Phase 0 (2026-05-12) : schemas unifies Task. Remplace RoutineTask schemas.
# Validators conditionnels selon recurrence_kind (politique 11 UPPER_CASE).

from pydantic import BaseModel, Field, model_validator
from typing import Optional, Literal
from app.utc_datetime import UTCDateTime

RecurrenceKind = Literal["NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"]


class TaskBase(BaseModel):
    model_config = {"populate_by_name": True}

    uuid: str

    # Core
    title: str
    notes: Optional[str] = None
    is_active: bool = Field(True, alias="isActive")
    order: int = 0

    # Recurrence
    recurrence_kind: RecurrenceKind = Field(..., alias="recurrenceKind")

    # Conditional fields
    due_date: Optional[str] = Field(None, alias="dueDate", pattern=r"^\d{4}-\d{2}-\d{2}$")
    due_time: Optional[str] = Field(None, alias="dueTime", pattern=r"^\d{2}:\d{2}$")
    period_uuid: Optional[str] = Field(None, alias="periodUUID")
    recurrence_weekdays: Optional[list[int]] = Field(None, alias="recurrenceWeekdays")
    recurrence_start_date: Optional[str] = Field(
        None, alias="recurrenceStartDate", pattern=r"^\d{4}-\d{2}-\d{2}$"
    )
    recurrence_end_date: Optional[str] = Field(
        None, alias="recurrenceEndDate", pattern=r"^\d{4}-\d{2}-\d{2}$"
    )

    # B.4 : dates ISO a exclure des occurrences (mode "Only this" du dialog edit
    # recurrence). Ignore si recurrence_kind in (NONE, DAILY).
    excluded_dates: list[str] = Field(
        default_factory=list, alias="excludedDates"
    )

    # Phase 3
    reminder_minutes_before: Optional[int] = Field(None, alias="reminderMinutesBefore", ge=0)

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    @model_validator(mode="after")
    def _validate_recurrence_consistency(self) -> "TaskBase":
        """
        Validation conditionnelle :
        - NONE     : due_date REQUIRED, period_uuid + recurrence_* must be NULL
        - DAILY    : period_uuid REQUIRED, recurrence_start_date REQUIRED,
                     due_date + recurrence_weekdays must be NULL
        - WEEKLY   : recurrence_weekdays REQUIRED (non-empty, ints 0..6),
                     recurrence_start_date REQUIRED, due_date + period_uuid NULL
        - MONTHLY  : recurrence_start_date REQUIRED, due_date + period_uuid +
                     recurrence_weekdays NULL
        - YEARLY   : idem MONTHLY
        """
        kind = self.recurrence_kind

        if kind == "NONE":
            if self.due_date is None:
                raise ValueError("due_date REQUIRED when recurrence_kind=NONE")
            if self.period_uuid is not None:
                raise ValueError("period_uuid forbidden when recurrence_kind=NONE")
            if self.recurrence_weekdays is not None:
                raise ValueError("recurrence_weekdays forbidden when recurrence_kind=NONE")
            if self.recurrence_start_date is not None:
                raise ValueError("recurrence_start_date forbidden when recurrence_kind=NONE")
            if self.recurrence_end_date is not None:
                raise ValueError("recurrence_end_date forbidden when recurrence_kind=NONE")

        elif kind == "DAILY":
            if self.period_uuid is None:
                raise ValueError("period_uuid REQUIRED when recurrence_kind=DAILY")
            if self.recurrence_start_date is None:
                raise ValueError("recurrence_start_date REQUIRED when recurrence_kind=DAILY")
            if self.due_date is not None:
                raise ValueError("due_date forbidden when recurrence_kind=DAILY")
            if self.recurrence_weekdays is not None:
                raise ValueError("recurrence_weekdays forbidden when recurrence_kind=DAILY")

        elif kind == "WEEKLY":
            if not self.recurrence_weekdays:
                raise ValueError("recurrence_weekdays REQUIRED (non-empty) when recurrence_kind=WEEKLY")
            if any(d < 0 or d > 6 for d in self.recurrence_weekdays):
                raise ValueError("recurrence_weekdays must contain ints in [0..6] (Mon=0..Sun=6)")
            if self.recurrence_start_date is None:
                raise ValueError("recurrence_start_date REQUIRED when recurrence_kind=WEEKLY")
            if self.due_date is not None:
                raise ValueError("due_date forbidden when recurrence_kind=WEEKLY")
            if self.period_uuid is not None:
                raise ValueError("period_uuid forbidden when recurrence_kind=WEEKLY")

        elif kind in ("MONTHLY", "YEARLY"):
            if self.recurrence_start_date is None:
                raise ValueError(f"recurrence_start_date REQUIRED when recurrence_kind={kind}")
            if self.due_date is not None:
                raise ValueError(f"due_date forbidden when recurrence_kind={kind}")
            if self.period_uuid is not None:
                raise ValueError(f"period_uuid forbidden when recurrence_kind={kind}")
            if self.recurrence_weekdays is not None:
                raise ValueError(f"recurrence_weekdays forbidden when recurrence_kind={kind}")

        # Optional cap recurrence_end_date : valide uniquement si != NONE
        # (deja garde au-dessus pour NONE). Si fournie, doit etre >= start_date.
        if self.recurrence_end_date is not None and self.recurrence_start_date is not None:
            if self.recurrence_end_date < self.recurrence_start_date:
                raise ValueError("recurrence_end_date must be >= recurrence_start_date")

        return self


class TaskCreate(TaskBase):
    pass


class TaskOut(TaskBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
