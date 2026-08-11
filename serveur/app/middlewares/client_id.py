# app/middlewares/client_id.py
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response
from app.context import client_id_ctx

class ClientIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        token = client_id_ctx.set(request.headers.get("x-client-id"))  # peut être None
        try:
            response: Response = await call_next(request)
            return response
        finally:
            client_id_ctx.reset(token)
