from pydantic import BaseModel, Field
from typing import Optional, Dict, Any
from datetime import datetime
from app.utc_datetime import UTCDateTime


# -------------------- Notification --------------------

class NotificationBase(BaseModel):
    type: str
    level: str = "info"

    title: str
    body: Optional[str] = None

    data: Optional[Dict[str, Any]] = None
    dedupe_key: Optional[str] = Field(None, alias="dedupeKey")

    created_at: Optional[UTCDateTime] = Field(None, alias="createdAt")
    read_at: Optional[UTCDateTime] = Field(None, alias="readAt")
    archived_at: Optional[UTCDateTime] = Field(None, alias="archivedAt")
    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str

    model_config = {"populate_by_name": True}

class NotificationCreate(NotificationBase):
    pass


class NotificationOut(NotificationBase):
    user_id: int = Field(..., alias="userId")
    # Override : Postgres pose `nullable=False, server_default=func.now()` → garanti non-null à la sortie.
    created_at: UTCDateTime = Field(..., alias="createdAt")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
