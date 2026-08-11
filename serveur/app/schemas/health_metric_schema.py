from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- HealthMetric --------------------

class HealthMetricBase(BaseModel):
    type: str                                            # UPPER_CASE : HEART_RATE | SLEEP | DISTANCE | ACTIVE_CALORIES | SPO2
    value: float
    unit: str                                            # bpm | min | m | km | kcal | %...
    date: str                                            # "YYYY-MM-DD"
    start_time: Optional[str] = Field(None, alias="startTime")   # "HH:MM" optionnel

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class HealthMetricCreate(HealthMetricBase):
    pass

class HealthMetricOut(HealthMetricBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
