# Exemple : app/models/available_equipment.py

from sqlalchemy import Column, Integer, String, ForeignKey, DateTime, UniqueConstraint, func
from app.database import Base

import uuid

class AvailableEquipment(Base):
    __tablename__ = "available_equipments"
    __table_args__ = (
        UniqueConstraint("user_id", "name", name="uq_available_equipments_user_id_name"),
    )

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)

    uuid = Column(String, nullable=False, default=lambda: str(uuid.uuid4()), unique=True)

    name = Column(String, nullable=False)

    updated_at = Column(DateTime(timezone=True), nullable=True)
