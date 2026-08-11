# app/models/superset_group.py

from sqlalchemy import Column, Integer, String, ForeignKey, DateTime, func
from sqlalchemy.orm import relationship
from app.database import Base

import uuid

class SupersetGroup(Base):
    __tablename__ = "superset_groups"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=True, index=True)
    name = Column(String, nullable=False)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    uuid = Column(String, default=lambda: str(uuid.uuid4()), nullable=False, unique=True)
