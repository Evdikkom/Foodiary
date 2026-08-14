from functools import lru_cache
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Foodiary LogMeal Backend"
    app_env: str = "development"
    app_host: str = "0.0.0.0"
    app_port: int = 8080
    app_debug: bool = True

    logmeal_base_url: str = "https://api.logmeal.com"
    logmeal_timeout_seconds: float = 60.0
    logmeal_company_token: str | None = Field(default=None, alias="LOGMEAL_COMPANY_TOKEN")
    logmeal_apiuser_token: str | None = Field(default=None, alias="LOGMEAL_APIUSER_TOKEN")

    foodiary_public_api_key: str | None = Field(default=None, alias="FOODIARY_PUBLIC_API_KEY")
    enable_raw_debug_endpoint: bool = Field(default=False, alias="ENABLE_RAW_DEBUG_ENDPOINT")

    default_user_language: str = "ru"
    default_user_country: str = "RU"

    max_upload_size_mb: int = 10
    allowed_image_extensions: str = ".jpg,.jpeg,.png,.webp"

    cors_allow_origins: str = "*"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    @property
    def allowed_image_extensions_set(self) -> set[str]:
        return {part.strip().lower() for part in self.allowed_image_extensions.split(",") if part.strip()}

    @property
    def cors_allow_origins_list(self) -> list[str]:
        return [part.strip() for part in self.cors_allow_origins.split(",") if part.strip()]


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
