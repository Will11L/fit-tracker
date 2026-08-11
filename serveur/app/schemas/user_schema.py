from datetime import date
from typing import Literal

from pydantic import BaseModel, Field

# -------------------- User --------------------

class UserCreate(BaseModel):
    username: str
    password: str
    email: str | None = Field(None, max_length=254)
    first_name: str | None = Field(None, alias="firstName")
    last_name: str | None = Field(None, alias="lastName")

    model_config = {
        "populate_by_name": True,  # accepte snake_case et camelCase
    }


class SignupRequest(BaseModel):
    """Endpoint public POST /signup. Pas de user_id, pas d'is_admin (le client
    ne peut pas s'auto-promouvoir admin). Email optionnel : le login reste
    username, l'email n'est qu'un champ de contact (max 254 = RFC 5321)."""
    username: str = Field(..., min_length=3, max_length=50)
    password: str = Field(..., min_length=8, max_length=128)
    email: str | None = Field(None, max_length=254)
    first_name: str | None = Field(None, alias="firstName", max_length=50)
    last_name: str | None = Field(None, alias="lastName", max_length=50)

    model_config = {
        "populate_by_name": True,
    }

class UserOut(BaseModel):
    id: int
    username: str
    is_admin: bool = Field(False, alias="isAdmin")
    # Email reel optionnel (2026-06-06) -- remplace l'ancien email synthetique
    # `{username}@sportapp.com` de GET /me. Nullable : signup sans email.
    email: str | None = Field(None)
    # F6-4 (2026-05-06) : exposés ici pour permettre à ProfileScreen de les afficher.
    # Stockés à la création via SignupRequest (V8.3) ou UserCreate, mais jamais relus
    # côté client jusqu'ici → mismatch "stocké jamais relu".
    first_name: str | None = Field(None, alias="firstName")
    last_name: str | None = Field(None, alias="lastName")
    # Bio (livré 2026-05-11) -- alimente Nutrition future + personnalisation.
    birth_date: date | None = Field(None, alias="birthDate")
    sex: Literal["MALE", "FEMALE", "OTHER"] | None = Field(None)
    height_cm: float | None = Field(None, alias="heightCm")
    weight_kg: float | None = Field(None, alias="weightKg")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }

class UserPublic(BaseModel):
    id: int
    username: str
    first_name: str | None = Field(None, alias="firstName")
    last_name: str | None = Field(None, alias="lastName")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }

class MeProfileUpdate(BaseModel):
    """Body de PATCH /me/profile -- self-only (le user édite son propre profil).
    Utilisé par l'onboarding (étape Welcome : firstName ; étape BIO : birthDate/
    sex/heightCm/weightKg) + ProfileScreen (édition future). Seuls les champs
    profil sont éditables ; username/password/is_admin nécessitent des endpoints
    dédiés (admin)."""
    email: str | None = Field(None, max_length=254)
    first_name: str | None = Field(None, alias="firstName", max_length=50)
    last_name: str | None = Field(None, alias="lastName", max_length=50)
    birth_date: date | None = Field(None, alias="birthDate")
    sex: Literal["MALE", "FEMALE", "OTHER"] | None = Field(None)
    height_cm: float | None = Field(None, alias="heightCm", ge=50, le=300)
    weight_kg: float | None = Field(None, alias="weightKg", ge=20, le=500)

    model_config = {
        "populate_by_name": True,
    }


class MeDeleteRequest(BaseModel):
    """Body de DELETE /me -- confirmation par mot de passe avant la suppression
    IRRÉVERSIBLE du compte (cascade ON DELETE sur toutes les données user-scoped).
    Re-saisie du password : évite une suppression accidentelle ou via JWT volé."""
    password: str


class UserAdminToggle(BaseModel):
    """Body de PATCH /users/{user_id}/admin — toggle uniquement le flag is_admin
    sans toucher aux autres champs (séparation préoccupations promotion vs édition
    profil)."""
    is_admin: bool = Field(..., alias="isAdmin")

    model_config = {
        "populate_by_name": True,
    }


class UserUpsert(BaseModel):
    id: int
    username: str
    password: str
    first_name: str | None = Field(None, alias="firstName")
    last_name: str | None = Field(None, alias="lastName")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
