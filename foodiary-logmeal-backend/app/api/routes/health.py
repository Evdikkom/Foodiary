from fastapi import APIRouter

from app.core.config import get_settings
from app.schemas.common import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    settings = get_settings()
    return HealthResponse(
        status="ok",
        app=settings.app_name,
        environment=settings.app_env,
        logmeal_company_token_configured=bool(settings.logmeal_company_token),
        logmeal_apiuser_token_configured=bool(settings.logmeal_apiuser_token),
    )
