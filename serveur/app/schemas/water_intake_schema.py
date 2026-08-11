from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- WaterIntake --------------------

class WaterIntakeBase(BaseModel):
    date: str                                              # "YYYY-MM-DD" (jour local)
    amount_ml: int = Field(..., alias="amountMl", gt=0)    # volume d'une prise en ml (> 0)

    created_at: Optional[UTCDateTime] = Field(None, alias="createdAt")
    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class WaterIntakeCreate(WaterIntakeBase):
    pass

class WaterIntakeOut(WaterIntakeBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
