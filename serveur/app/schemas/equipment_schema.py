from datetime import datetime
from app.utc_datetime import UTCDateTime
from pydantic import BaseModel, Field
from typing import Optional

# -------------------- Equipment --------------------

class EquipmentBase(BaseModel):
    name: str
    uuid: str

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")
    model_config = {"populate_by_name": True}

class EquipmentCreate(EquipmentBase):
    pass

class EquipmentOut(EquipmentBase):
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }
