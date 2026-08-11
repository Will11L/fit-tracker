from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- AvailableEquipment --------------------

class AvailableEquipmentBase(BaseModel):
    name: str

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class AvailableEquipmentCreate(AvailableEquipmentBase):
    pass

class AvailableEquipmentOut(AvailableEquipmentBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
