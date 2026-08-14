from __future__ import annotations

import os

from fastapi import APIRouter, Depends, File, Header, HTTPException, UploadFile, status

from app.clients.logmeal_client import LogMealClient
from app.core.config import Settings, get_settings
from app.schemas.vision import AnalyzeFoodRawResponse, AnalyzeFoodResponse
from app.services.vision_service import VisionService

router = APIRouter(prefix="/api/v1/vision", tags=["vision"])


def get_logmeal_client(settings: Settings = Depends(get_settings)) -> LogMealClient:
    return LogMealClient(settings)


def require_api_key(
    settings: Settings = Depends(get_settings),
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
) -> None:
    expected = settings.foodiary_public_api_key
    if not expected:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Server API key is not configured.")
    if x_api_key != expected:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or missing API key.")


async def _read_and_validate_image(file: UploadFile, settings: Settings) -> tuple[bytes, str, str]:
    extension = os.path.splitext(file.filename or "upload.jpg")[1].lower()
    if extension and extension not in settings.allowed_image_extensions_set:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported file type: {extension}. Allowed: {', '.join(sorted(settings.allowed_image_extensions_set))}",
        )

    content_type = (file.content_type or "").lower()
    if content_type and not content_type.startswith("image/"):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Uploaded file must be an image.")

    content = await file.read()
    max_size_bytes = settings.max_upload_size_mb * 1024 * 1024
    if len(content) > max_size_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"File is too large. Maximum allowed size is {settings.max_upload_size_mb} MB.",
        )

    if not content:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Uploaded image is empty.")

    return content, file.filename or "upload.jpg", file.content_type or "image/jpeg"


@router.post("/analyze-food", response_model=AnalyzeFoodResponse, dependencies=[Depends(require_api_key)])
async def analyze_food(
    image: UploadFile = File(...),
    settings: Settings = Depends(get_settings),
    client: LogMealClient = Depends(get_logmeal_client),
) -> AnalyzeFoodResponse:
    try:
        image_bytes, filename, content_type = await _read_and_validate_image(image, settings)
        service = VisionService(client)
        return await service.analyze_food(image_bytes=image_bytes, filename=filename, content_type=content_type)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=f"LogMeal request failed: {exc}") from exc
    finally:
        await client.close()


@router.post("/analyze-food/raw", response_model=AnalyzeFoodRawResponse, dependencies=[Depends(require_api_key)])
async def analyze_food_raw(
    image: UploadFile = File(...),
    settings: Settings = Depends(get_settings),
    client: LogMealClient = Depends(get_logmeal_client),
) -> AnalyzeFoodRawResponse:
    if not settings.enable_raw_debug_endpoint:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")

    try:
        image_bytes, filename, content_type = await _read_and_validate_image(image, settings)
        service = VisionService(client)
        return await service.analyze_food_raw(image_bytes=image_bytes, filename=filename, content_type=content_type)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=f"LogMeal request failed: {exc}") from exc
    finally:
        await client.close()
