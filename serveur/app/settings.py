from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    # URL async pour l'app (SQLAlchemy + asyncpg)
    DATABASE_URL: str = "postgresql+asyncpg://fittracker:change-me@localhost:5432/fittracker"
    JWT_SECRET_KEY: str = "change-me"
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    JWT_ISS: str = "fittracker-api"
    JWT_AUD: str = "fittracker-clients"

    # V8.2 refresh token (OAuth-like flow). 7 jours par défaut.
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7

    # Mot de passe des comptes de demonstration crees par le seed. Jamais en dur
    # dans le depot : defini dans .env (non versionne). Defaut inoffensif.
    SEED_USER_PASSWORD: str = "changeme"

    # V8.4 starter pack signup. Username du user fixture qui heberge le
    # catalogue de muscles+exercises copie aux nouveaux users au /signup.
    STARTER_TEMPLATE_USERNAME: str = "starter_template"

    # T1.1.b (2026-05-06) : URL DB de test pour pytest. Pointe sur
    # fittracker_test (DB Postgres separee, OWNER fittracker). Override via
    # env var TEST_DATABASE_URL si besoin (CI, autre nom de DB, etc.).
    TEST_DATABASE_URL: str = "postgresql+asyncpg://fittracker:fittracker@127.0.0.1:5432/fittracker_test"

    # Cas C — Agent IA in-app (Phase 2 MCP). Cle Anthropic dans .env Pi + .env PC
    # dev, JAMAIS dans Git ni dans l'APK (politique design doc). Vide par defaut
    # -> l'endpoint /api/v1/agent/chat repond 503 tant que la cle n'est pas posee.
    ANTHROPIC_API_KEY: str = ""
    # Modele Claude utilise par la boucle tool-use (decision actee 2026-05-31).
    AGENT_MODEL: str = "claude-sonnet-4-6"
    # Garde-fou cout : rate limit slowapi par IP sur /agent/chat (anti cost-bomb).
    AGENT_RATE_LIMIT: str = "30/minute"
    # Borne le nombre d'allers-retours tool-use avant de forcer une reponse
    # (evite une boucle infinie si le modele rappelle des tools sans converger).
    AGENT_MAX_TOOL_ITERATIONS: int = 8
    # Plafond de tokens de la reponse Claude (cout + latence bornes).
    AGENT_MAX_TOKENS: int = 1024

settings = Settings()
