from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime, time
from app.utc_datetime import UTCDateTime

# -------------------- RoutinePeriod --------------------

class RoutinePeriodBase(BaseModel):
    name: str
    start_time: str = Field(..., alias="startTime")  # "06:30"
    end_time: str = Field(..., alias="endTime")      # "09:00"
    order: int = 0

    # Rappels notifs (2026-06-08) : minutes avant début / fin. NULL = désactivé,
    # 0 = pile à l'heure, N = N min avant. Default None IMPÉRATIF : le wire Gson
    # (serializeNulls off) omet les champs null -> sans default, Pydantic 422.
    reminder_before_start_minutes: Optional[int] = Field(None, alias="reminderBeforeStartMinutes")
    reminder_before_end_minutes: Optional[int] = Field(None, alias="reminderBeforeEndMinutes")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class RoutinePeriodCreate(RoutinePeriodBase):
    pass

class RoutinePeriodOut(RoutinePeriodBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
