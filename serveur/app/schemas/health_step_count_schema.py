from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- HealthStepCount --------------------

class HealthStepCountBase(BaseModel):
    date: str                                                # "YYYY-MM-DD"
    bucket_start: str = Field(..., alias="bucketStart")      # "HH:MM" début de tranche intraday
    steps: int

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class HealthStepCountCreate(HealthStepCountBase):
    pass

class HealthStepCountOut(HealthStepCountBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
