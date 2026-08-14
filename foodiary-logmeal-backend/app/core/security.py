from __future__ import annotations

from fastapi import Depends, Header, HTTPException, status

from app.core.config import Settings, get_settings


def require_api_key(
    settings: Settings = Depends(get_settings),
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
) -> None:
    expected = settings.foodiary_public_api_key
    if not expected:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Server API key is not configured.")
    if x_api_key != expected:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or missing API key.")
