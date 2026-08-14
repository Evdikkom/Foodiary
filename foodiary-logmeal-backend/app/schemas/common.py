from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: str
    app: str
    environment: str
    logmeal_company_token_configured: bool
    logmeal_apiuser_token_configured: bool


class ErrorResponse(BaseModel):
    detail: str
