from pydantic import BaseModel, Field
from datetime import date, datetime
from app.utc_datetime import UTCDateTime
from typing import Optional

# -------------------- TrainingCycle --------------------

class TrainingCycleBase(BaseModel):
    name: str
    # Format wire strict : "YYYY-MM-DD". Pydantic v2 type `date` rejette
    # automatiquement les datetimes ISO complets avec heure (`date_from_datetime_inexact`,
    # 422). Côté Android, le field `startDate: String` doit aussi rester en
    # "YYYY-MM-DD" strict. Cf. F4a-2 pour la même politique sur d'autres `date`.
    start_date: date = Field(..., alias="startDate")
    end_date: date = Field(..., alias="endDate")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class TrainingCycleCreate(TrainingCycleBase):
    pass

class TrainingCycleOut(TrainingCycleBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte start_date et startDate en entrÃ©e
    }
