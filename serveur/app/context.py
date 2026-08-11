# app/context.py
from contextvars import ContextVar
client_id_ctx: ContextVar[str | None] = ContextVar("client_id_ctx", default=None)
