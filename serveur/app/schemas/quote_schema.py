from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- Quote --------------------

class QuoteBase(BaseModel):
    text: str
    author: Optional[str] = None

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class QuoteCreate(QuoteBase):
    pass

class QuoteOut(QuoteBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
