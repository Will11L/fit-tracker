from sqlalchemy import Boolean, Column, Date, Float, Integer, String
from app.database import Base

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True)
    username = Column(String, unique=True, nullable=False)
    hashed_password = Column(String, nullable=False)

    # Email reel optionnel (2026-06-06). Le login reste username -- l'email
    # est un champ de contact nullable (l'user peut signup sans). Login par
    # email + reset password = tache backlog separee.
    email = Column(String, nullable=True)

    first_name = Column(String, nullable=True)
    last_name = Column(String, nullable=True)

    is_admin = Column(Boolean, nullable=False, default=False, server_default="false")

    # Bio (livré 2026-05-11) -- alimente Nutrition future (BMR/TDEE) + personnalisation.
    # Tous nullable : l'user peut skip l'étape BIO de l'onboarding.
    birth_date = Column(Date, nullable=True)
    # UPPER_CASE policy 11 -- accepte "MALE"/"FEMALE"/"OTHER" (validé Pydantic).
    sex = Column(String, nullable=True)
    # Canoniques cm/kg (affichage selon lengthUnit/weightUnit côté client).
    height_cm = Column(Float, nullable=True)
    weight_kg = Column(Float, nullable=True)
