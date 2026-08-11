# app/schemas/task_check_schema.py
#
# Phase 0 (2026-05-12) : remplace RoutineTaskCheck. Une coche par occurrence_date
# d'une tache (rename : date -> occurrence_date pour eviter confusion avec
# updated_at "date de coche", et cohere avec semantique "date d'occurrence").

from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime


class TaskCheckBase(BaseModel):
    model_config = {"populate_by_name": True}

    uuid: str
    task_uuid: str = Field(..., alias="taskUUID")
    occurrence_date: str = Field(..., alias="occurrenceDate", pattern=r"^\d{4}-\d{2}-\d{2}$")

    is_checked: bool = Field(False, alias="isChecked")
    checked_at: Optional[UTCDateTime] = Field(None, alias="checkedAt")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")


class TaskCheckCreate(TaskCheckBase):
    pass


class TaskCheckOut(TaskCheckBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
