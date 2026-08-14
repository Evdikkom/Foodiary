from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes.health import router as health_router
from app.api.routes.vision import router as vision_router
from app.core.config import get_settings
from app.core.logging import configure_logging

configure_logging()
settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI):
    yield


docs_url = "/docs" if settings.app_debug else None
redoc_url = "/redoc" if settings.app_debug else None
openapi_url = "/openapi.json" if settings.app_debug else None

app = FastAPI(
    title=settings.app_name,
    version="1.0.0",
    debug=settings.app_debug,
    docs_url=docs_url,
    redoc_url=redoc_url,
    openapi_url=openapi_url,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_allow_origins_list or ["*"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

app.include_router(health_router)
app.include_router(vision_router)
