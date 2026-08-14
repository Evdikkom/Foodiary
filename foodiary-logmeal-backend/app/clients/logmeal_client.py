from __future__ import annotations

import logging
from typing import Any

import httpx

from app.core.config import Settings

logger = logging.getLogger(__name__)


class LogMealClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._client = httpx.AsyncClient(
            base_url=settings.logmeal_base_url,
            timeout=settings.logmeal_timeout_seconds,
            headers={"accept": "application/json"},
        )

    async def close(self) -> None:
        await self._client.aclose()

    async def get_services(self) -> dict[str, Any]:
        if not self.settings.logmeal_company_token:
            raise ValueError("LOGMEAL_COMPANY_TOKEN is not configured")
        response = await self._client.get(
            "/v2/info/services",
            headers={"Authorization": f"Bearer {self.settings.logmeal_company_token}"},
        )
        self._raise_for_status_with_log(response, endpoint="/v2/info/services")
        return response.json()

    async def segment_image(self, image_bytes: bytes, filename: str, content_type: str) -> dict[str, Any]:
        token = self._require_apiuser_token()
        files = {
            "image": (filename, image_bytes, content_type),
        }
        response = await self._client.post(
            "/v2/image/segmentation/complete",
            headers={"Authorization": f"Bearer {token}"},
            files=files,
        )
        self._raise_for_status_with_log(response, endpoint="/v2/image/segmentation/complete")
        return response.json()

    async def confirm_dish(
        self,
        image_id: str,
        confirmed_class: list[int],
        food_item_position: list[int],
    ) -> dict[str, Any]:
        token = self._require_apiuser_token()
        payload = {
            "imageId": self._coerce_image_id(image_id),
            "confirmedClass": confirmed_class,
            "source": ["logmeal"] * len(confirmed_class),
            "food_item_position": food_item_position,
        }
        response = await self._client.post(
            "/v2/image/confirm/dish",
            headers={"Authorization": f"Bearer {token}"},
            json=payload,
        )
        self._raise_for_status_with_log(response, endpoint="/v2/image/confirm/dish", payload=payload)
        return response.json()

    async def get_ingredients(self, image_id: str) -> dict[str, Any]:
        token = self._require_apiuser_token()
        payload = {"imageId": self._coerce_image_id(image_id)}
        response = await self._client.post(
            "/v2/nutrition/recipe/ingredients",
            headers={"Authorization": f"Bearer {token}"},
            json=payload,
        )
        self._raise_for_status_with_log(response, endpoint="/v2/nutrition/recipe/ingredients", payload=payload)
        return response.json()

    async def get_nutritional_info(self, image_id: str) -> dict[str, Any]:
        token = self._require_apiuser_token()
        payload = {"imageId": self._coerce_image_id(image_id)}
        response = await self._client.post(
            "/v2/nutrition/recipe/nutritionalInfo",
            headers={"Authorization": f"Bearer {token}"},
            json=payload,
        )
        self._raise_for_status_with_log(response, endpoint="/v2/nutrition/recipe/nutritionalInfo", payload=payload)
        return response.json()

    def _require_apiuser_token(self) -> str:
        if not self.settings.logmeal_apiuser_token:
            raise ValueError(
                "LOGMEAL_APIUSER_TOKEN is not configured. Open your LogMeal Users dashboard and copy the testing APIUser token."
            )
        return self.settings.logmeal_apiuser_token

    def _coerce_image_id(self, image_id: str) -> int | str:
        try:
            return int(image_id)
        except (TypeError, ValueError):
            return image_id

    def _raise_for_status_with_log(
        self,
        response: httpx.Response,
        *,
        endpoint: str,
        payload: dict[str, Any] | None = None,
    ) -> None:
        try:
            response.raise_for_status()
        except httpx.HTTPStatusError:
            logger.error(
                "LogMeal request failed endpoint=%s status=%s payload=%s body=%s",
                endpoint,
                response.status_code,
                payload,
                response.text,
            )
            raise
