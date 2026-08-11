# app/schemas/superset_group.py

from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- SupersetGroup --------------------

class SupersetGroupBase(BaseModel):
    name: str

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class SupersetGroupCreate(SupersetGroupBase):
    pass

class SupersetGroupOut(SupersetGroupBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte user_id et userId en entrÃ©e
    }
