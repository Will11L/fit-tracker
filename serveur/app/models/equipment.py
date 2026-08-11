from sqlalchemy import Column, Integer, String, DateTime, func
from app.database import Base

import uuid

class Equipment(Base):
    __tablename__ = "equipments"

    id = Column(Integer, primary_key=True, index=True)
    uuid = Column(String, nullable=False, default=lambda: str(uuid.uuid4()), unique=True)
    
    name = Column(String, unique=True, nullable=False)

    updated_at = Column(DateTime(timezone=True), nullable=True)
