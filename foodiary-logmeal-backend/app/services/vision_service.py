from __future__ import annotations

import logging
from collections.abc import Iterable
from typing import Any

from app.clients.logmeal_client import LogMealClient
from app.schemas.vision import AnalyzeFoodRawResponse, AnalyzeFoodResponse, DetectedDishItem, DishCandidate, NutritionSummary

logger = logging.getLogger(__name__)


class VisionService:
    def __init__(self, client: LogMealClient):
        self.client = client

    async def analyze_food(self, image_bytes: bytes, filename: str, content_type: str) -> AnalyzeFoodResponse:
        segmentation = await self.client.segment_image(image_bytes=image_bytes, filename=filename, content_type=content_type)
        image_id = self._extract_image_id(segmentation)
        segmentation_items = self._extract_segmentation_items(segmentation)
        logger.info("LogMeal segmentation completed image_id=%s segments=%s", image_id, len(segmentation_items))

        confirmation_payload = self._build_confirmation_payload(segmentation_items)
        if confirmation_payload is not None:
            logger.info(
                "LogMeal auto-confirming top-1 dishes image_id=%s confirmed_items=%s",
                image_id,
                len(confirmation_payload["confirmed_class"]),
            )
            await self.client.confirm_dish(
                image_id=image_id,
                confirmed_class=confirmation_payload["confirmed_class"],
                food_item_position=confirmation_payload["food_item_position"],
            )
        else:
            logger.warning(
                "Skipping LogMeal confirm/dish image_id=%s because segmentation payload did not expose confirmable top-1 candidates",
                image_id,
            )

        ingredients = await self.client.get_ingredients(image_id)
        ingredients_per_item = self._extract_ingredients_per_item(ingredients)
        logger.info("LogMeal ingredients completed image_id=%s item_groups=%s", image_id, len(ingredients_per_item))

        nutrition = await self.client.get_nutritional_info(image_id)
        nutrition_per_item = self._extract_nutrition_per_item(nutrition)
        top_level_summary_preview = NutritionSummary(
            calories_kcal=self._extract_energy_kcal(nutrition),
            protein_g=self._extract_macro(nutrition, ["protein", "proteins"]),
            fat_g=self._extract_macro(nutrition, ["fat", "fats", "lipids"]),
            carbs_g=self._extract_macro(nutrition, ["carbohydrates", "carbs", "carbohydrate"]),
        )
        logger.info(
            "LogMeal nutrition completed image_id=%s top_level_summary=%s per_item_groups=%s",
            image_id,
            top_level_summary_preview.model_dump(),
            len(nutrition_per_item),
        )
        self._log_nutrition_diagnostics(image_id=image_id, nutrition=nutrition, nutrition_per_item=nutrition_per_item)

        return self._normalize(segmentation=segmentation, ingredients=ingredients, nutrition=nutrition)

    async def analyze_food_raw(self, image_bytes: bytes, filename: str, content_type: str) -> AnalyzeFoodRawResponse:
        segmentation = await self.client.segment_image(image_bytes=image_bytes, filename=filename, content_type=content_type)
        image_id = self._extract_image_id(segmentation)
        confirmation_payload = self._build_confirmation_payload(self._extract_segmentation_items(segmentation))
        if confirmation_payload is not None:
            await self.client.confirm_dish(
                image_id=image_id,
                confirmed_class=confirmation_payload["confirmed_class"],
                food_item_position=confirmation_payload["food_item_position"],
            )
        ingredients = await self.client.get_ingredients(image_id)
        nutrition = await self.client.get_nutritional_info(image_id)
        return AnalyzeFoodRawResponse(segmentation=segmentation, ingredients=ingredients, nutrition=nutrition)

    def _normalize(self, segmentation: dict[str, Any], ingredients: dict[str, Any], nutrition: dict[str, Any]) -> AnalyzeFoodResponse:
        image_id = self._extract_image_id(segmentation)
        segmentation_items = self._extract_segmentation_items(segmentation)
        ingredients_per_item = self._extract_ingredients_per_item(ingredients)
        nutrition_per_item = self._extract_nutrition_per_item(nutrition)

        normalized_items: list[DetectedDishItem] = []
        for idx, raw_item in enumerate(segmentation_items):
            candidates = self._extract_candidates(raw_item)
            top_candidate = candidates[0] if candidates else None
            item_ingredients = ingredients_per_item[idx] if idx < len(ingredients_per_item) else []
            item_nutrition = nutrition_per_item[idx] if idx < len(nutrition_per_item) else {}

            normalized_items.append(
                DetectedDishItem(
                    item_index=idx,
                    top_candidate=top_candidate,
                    candidates=candidates,
                    serving_size=self._extract_serving_size(raw_item, item_nutrition),
                    calories_kcal=self._extract_energy_kcal(item_nutrition),
                    protein_g=self._extract_macro(item_nutrition, ["protein", "proteins"]),
                    fat_g=self._extract_macro(item_nutrition, ["fat", "fats", "lipids"]),
                    carbs_g=self._extract_macro(item_nutrition, ["carbohydrates", "carbs", "carbohydrate"]),
                    ingredients=item_ingredients,
                    raw_region=raw_item,
                )
            )

        top_level_summary = NutritionSummary(
            calories_kcal=self._extract_energy_kcal(nutrition),
            protein_g=self._extract_macro(nutrition, ["protein", "proteins"]),
            fat_g=self._extract_macro(nutrition, ["fat", "fats", "lipids"]),
            carbs_g=self._extract_macro(nutrition, ["carbohydrates", "carbs", "carbohydrate"]),
        )
        item_level_summary = self._build_item_level_summary(normalized_items)
        summary = NutritionSummary(
            calories_kcal=top_level_summary.calories_kcal if top_level_summary.calories_kcal is not None else item_level_summary.calories_kcal,
            protein_g=top_level_summary.protein_g if top_level_summary.protein_g is not None else item_level_summary.protein_g,
            fat_g=top_level_summary.fat_g if top_level_summary.fat_g is not None else item_level_summary.fat_g,
            carbs_g=top_level_summary.carbs_g if top_level_summary.carbs_g is not None else item_level_summary.carbs_g,
        )

        raw_dish_label = normalized_items[0].top_candidate.name if normalized_items and normalized_items[0].top_candidate else None

        notes = ["LogMeal nutrition is based on the recognized standard portion unless quantity is confirmed."]
        if self._summary_was_completed(top_level_summary, summary):
            notes.append("Summary was completed from item-level nutrition because top-level nutritional fields were incomplete.")
        if not self._summary_has_any_values(summary):
            notes.append("LogMeal response did not include calories or macros in either top-level or item-level nutrition.")

        logger.info(
            "Normalized nutrition image_id=%s items=%s items_with_nutrition=%s items_with_macros=%s summary_top_level=%s summary_final=%s",
            image_id,
            len(normalized_items),
            self._count_items_with_any_nutrition(normalized_items),
            self._count_items_with_macros(normalized_items),
            top_level_summary.model_dump(),
            summary.model_dump(),
        )

        return AnalyzeFoodResponse(
            image_id=image_id,
            items=normalized_items,
            summary=summary,
            raw_dish_label=raw_dish_label,
            notes=notes,
        )

    def _extract_image_id(self, payload: dict[str, Any]) -> str:
        for key in ("imageId", "image_id", "id"):
            value = payload.get(key)
            if value:
                return str(value)
        raise ValueError("LogMeal response does not contain imageId")

    def _extract_segmentation_items(self, payload: dict[str, Any]) -> list[dict[str, Any]]:
        for key in ("segmentation_results", "segmentationResults", "results"):
            value = payload.get(key)
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]
        return []

    def _extract_candidates(self, raw_item: dict[str, Any]) -> list[DishCandidate]:
        result: list[DishCandidate] = []
        candidate_groups = []
        for key in ("recognition_results", "recognitionResults", "results", "candidates"):
            value = raw_item.get(key)
            if isinstance(value, list):
                candidate_groups = value
                break

        for entry in candidate_groups:
            if not isinstance(entry, dict):
                continue
            name = (
                entry.get("name")
                or entry.get("dish_name")
                or entry.get("label")
                or entry.get("class_name")
                or entry.get("food_name")
            )
            if not name:
                continue
            result.append(
                DishCandidate(
                    class_id=self._safe_int(entry.get("id") or entry.get("class_id")),
                    name=str(name),
                    confidence=self._safe_float(
                        entry.get("prob")
                        or entry.get("probability")
                        or entry.get("confidence")
                        or entry.get("score")
                    ),
                )
            )
        return result

    def _build_confirmation_payload(self, segmentation_items: list[dict[str, Any]]) -> dict[str, list[int]] | None:
        confirmed_class: list[int] = []
        food_item_position: list[int] = []

        for idx, raw_item in enumerate(segmentation_items):
            candidates = self._extract_candidates(raw_item)
            top_candidate = candidates[0] if candidates else None
            if top_candidate is None or top_candidate.class_id is None:
                continue

            position = self._extract_food_item_position(raw_item)
            if position is None:
                position = idx + 1

            confirmed_class.append(top_candidate.class_id)
            food_item_position.append(position)

        if not confirmed_class:
            return None

        return {
            "confirmed_class": confirmed_class,
            "food_item_position": food_item_position,
        }

    def _extract_food_item_position(self, raw_item: dict[str, Any]) -> int | None:
        for key in ("food_item_position", "foodItemPosition", "position", "item_position"):
            value = self._safe_int(raw_item.get(key))
            if value is not None:
                return value
        return None

    def _extract_ingredients_per_item(self, payload: dict[str, Any]) -> list[list[str]]:
        result: list[list[str]] = []
        raw_items = payload.get("ingredients_per_item") or payload.get("ingredientsPerItem") or []
        if not isinstance(raw_items, list):
            return result
        for raw_item in raw_items:
            item_names: list[str] = []
            if isinstance(raw_item, dict):
                ingredients = raw_item.get("ingredients") or raw_item.get("items") or raw_item.get("ingredient_list") or []
            elif isinstance(raw_item, list):
                ingredients = raw_item
            else:
                ingredients = []

            for ingredient in ingredients:
                if isinstance(ingredient, dict):
                    name = ingredient.get("name") or ingredient.get("ingredient") or ingredient.get("label")
                    if name:
                        item_names.append(str(name))
                elif isinstance(ingredient, str):
                    item_names.append(ingredient)
            result.append(item_names)
        return result

    def _extract_nutrition_per_item(self, payload: dict[str, Any]) -> list[dict[str, Any]]:
        raw_items = payload.get("nutritional_info_per_item") or payload.get("nutritionalInfoPerItem") or []
        return self._normalize_item_collection(raw_items)

    def _extract_energy_kcal(self, payload: Any) -> float | None:
        if isinstance(payload, list):
            return self._extract_indicator_value(payload, ["energy", "kcal", "calorie"])
        if not isinstance(payload, dict):
            return None

        direct_candidates = [
            payload.get("calories"),
            payload.get("energy_kcal"),
            payload.get("kcal"),
            payload.get("energyKcal"),
        ]
        for candidate in direct_candidates:
            value = self._extract_numeric_value(candidate)
            if value is not None:
                return value

        nutrition_info = payload.get("nutritional_info") or payload.get("nutritionalInfo")
        if isinstance(nutrition_info, dict):
            return self._extract_energy_kcal(nutrition_info)
        if isinstance(nutrition_info, list):
            value = self._extract_indicator_value(nutrition_info, ["energy", "kcal", "calorie"])
            if value is not None:
                return value

        total_nutrients = payload.get("totalNutrients") or payload.get("total_nutrients")
        value = self._extract_total_nutrient_quantity(total_nutrients, ["ENERC_KCAL"])
        if value is not None:
            return value

        indicators = payload.get("nutritionalIndicators") or payload.get("nutritional_indicators") or payload.get("indicators")
        return self._extract_indicator_value(indicators, ["energy", "kcal", "calorie"])

    def _extract_macro(self, payload: Any, aliases: Iterable[str]) -> float | None:
        if isinstance(payload, list):
            return self._extract_indicator_value(payload, aliases)
        if not isinstance(payload, dict):
            return None

        nutrition_info = payload.get("nutritional_info") or payload.get("nutritionalInfo")
        if isinstance(nutrition_info, dict):
            value = self._extract_macro(nutrition_info, aliases)
            if value is not None:
                return value
        if isinstance(nutrition_info, list):
            value = self._extract_indicator_value(nutrition_info, aliases)
            if value is not None:
                return value

        for alias in aliases:
            direct = self._extract_numeric_value(payload.get(alias))
            if direct is not None:
                return direct

        alias_code_map = {
            "protein": ["PROCNT"],
            "proteins": ["PROCNT"],
            "fat": ["FAT"],
            "fats": ["FAT"],
            "lipids": ["FAT"],
            "carbohydrates": ["CHOCDF"],
            "carbs": ["CHOCDF"],
            "carbohydrate": ["CHOCDF"],
        }
        nutrient_codes: list[str] = []
        for alias in aliases:
            nutrient_codes.extend(alias_code_map.get(alias.lower(), []))
        total_nutrients = payload.get("totalNutrients") or payload.get("total_nutrients")
        value = self._extract_total_nutrient_quantity(total_nutrients, nutrient_codes)
        if value is not None:
            return value

        indicators = payload.get("nutritionalIndicators") or payload.get("nutritional_indicators") or payload.get("indicators")
        return self._extract_indicator_value(indicators, aliases)

    def _extract_serving_size(self, raw_item: dict[str, Any], nutrition_item: dict[str, Any]) -> str | None:
        for source in (nutrition_item, raw_item):
            serving = source.get("serving_size") or source.get("servingSize")
            if serving is not None:
                return str(serving)
        return None

    def _normalize_item_collection(self, raw_items: Any) -> list[dict[str, Any]]:
        if isinstance(raw_items, list):
            return [item for item in raw_items if isinstance(item, dict)]
        if not isinstance(raw_items, dict):
            return []

        for key in ("items", "results", "values", "data"):
            nested = raw_items.get(key)
            normalized = self._normalize_item_collection(nested)
            if normalized:
                return normalized

        indexed_items: list[tuple[int, dict[str, Any]]] = []
        plain_items: list[dict[str, Any]] = []
        for key, value in raw_items.items():
            if not isinstance(value, dict):
                continue
            index = self._safe_int(key)
            if index is None:
                plain_items.append(value)
            else:
                indexed_items.append((index, value))

        if indexed_items:
            indexed_items.sort(key=lambda item: item[0])
            return [value for _, value in indexed_items] + plain_items
        return plain_items

    def _extract_indicator_value(self, indicators: Any, aliases: Iterable[str]) -> float | None:
        if not isinstance(indicators, list):
            return None

        lowered_aliases = [alias.lower() for alias in aliases]
        for item in indicators:
            if not isinstance(item, dict):
                continue

            raw_name = " ".join(
                str(item.get(key) or "")
                for key in ("name", "label", "code", "short_name")
            ).lower()
            if any(alias in raw_name for alias in lowered_aliases):
                value = self._extract_numeric_value(
                    item.get("value")
                    or item.get("amount")
                    or item.get("quantity")
                    or item.get("qty")
                )
                if value is not None:
                    return value
        return None

    def _extract_numeric_value(self, value: Any) -> float | None:
        direct = self._safe_float(value)
        if direct is not None:
            return direct

        if isinstance(value, dict):
            for key in ("value", "amount", "quantity", "qty"):
                nested = self._safe_float(value.get(key))
                if nested is not None:
                    return nested
        return None

    def _extract_total_nutrient_quantity(self, total_nutrients: Any, nutrient_codes: Iterable[str]) -> float | None:
        if not isinstance(total_nutrients, dict):
            return None

        for code in nutrient_codes:
            nutrient = total_nutrients.get(code)
            value = self._extract_numeric_value(nutrient)
            if value is not None:
                return value
        return None

    def _build_item_level_summary(self, items: list[DetectedDishItem]) -> NutritionSummary:
        return NutritionSummary(
            calories_kcal=self._sum_or_none(item.calories_kcal for item in items),
            protein_g=self._sum_or_none(item.protein_g for item in items),
            fat_g=self._sum_or_none(item.fat_g for item in items),
            carbs_g=self._sum_or_none(item.carbs_g for item in items),
        )

    def _sum_or_none(self, values: Iterable[float | None]) -> float | None:
        collected = [value for value in values if value is not None]
        if not collected:
            return None
        return sum(collected)

    def _summary_has_any_values(self, summary: NutritionSummary) -> bool:
        return any(
            value is not None
            for value in (
                summary.calories_kcal,
                summary.protein_g,
                summary.fat_g,
                summary.carbs_g,
            )
        )

    def _summary_was_completed(self, top_level_summary: NutritionSummary, final_summary: NutritionSummary) -> bool:
        return any(
            top_value is None and final_value is not None
            for top_value, final_value in (
                (top_level_summary.calories_kcal, final_summary.calories_kcal),
                (top_level_summary.protein_g, final_summary.protein_g),
                (top_level_summary.fat_g, final_summary.fat_g),
                (top_level_summary.carbs_g, final_summary.carbs_g),
            )
        )

    def _count_items_with_macros(self, items: list[DetectedDishItem]) -> int:
        return sum(
            1
            for item in items
            if item.protein_g is not None
            or item.fat_g is not None
            or item.carbs_g is not None
        )

    def _count_items_with_any_nutrition(self, items: list[DetectedDishItem]) -> int:
        return sum(
            1
            for item in items
            if item.calories_kcal is not None
            or item.protein_g is not None
            or item.fat_g is not None
            or item.carbs_g is not None
        )

    def _has_any_nutrition_values(self, payload: dict[str, Any]) -> bool:
        return any(
            value is not None
            for value in (
                self._extract_energy_kcal(payload),
                self._extract_macro(payload, ["protein", "proteins"]),
                self._extract_macro(payload, ["fat", "fats", "lipids"]),
                self._extract_macro(payload, ["carbohydrates", "carbs", "carbohydrate"]),
            )
        )

    def _log_nutrition_diagnostics(
        self,
        *,
        image_id: str,
        nutrition: dict[str, Any],
        nutrition_per_item: list[dict[str, Any]],
    ) -> None:
        top_level_nutrition_info = nutrition.get("nutritional_info") or nutrition.get("nutritionalInfo")
        top_level_indicators = nutrition.get("nutritionalIndicators") or nutrition.get("nutritional_indicators") or nutrition.get("indicators")
        logger.info(
            "LogMeal nutrition payload diagnostics image_id=%s top_level_keys=%s hasNutritionalInfo=%s nutrition_info_type=%s indicators_count=%s raw_nutritional_info=%s",
            image_id,
            sorted(nutrition.keys()),
            nutrition.get("hasNutritionalInfo"),
            type(top_level_nutrition_info).__name__ if top_level_nutrition_info is not None else None,
            len(top_level_indicators) if isinstance(top_level_indicators, list) else None,
            top_level_nutrition_info,
        )

        item_previews: list[dict[str, Any]] = []
        for idx, item in enumerate(nutrition_per_item[:3]):
            nested_nutrition = item.get("nutritional_info") or item.get("nutritionalInfo")
            item_indicators = item.get("nutritionalIndicators") or item.get("nutritional_indicators") or item.get("indicators")
            item_previews.append(
                {
                    "item_index": idx,
                    "keys": sorted(item.keys()),
                    "hasNutritionalInfo": item.get("hasNutritionalInfo"),
                    "nutrition_info_type": type(nested_nutrition).__name__ if nested_nutrition is not None else None,
                    "indicators_count": len(item_indicators) if isinstance(item_indicators, list) else None,
                    "raw_nutritional_info": nested_nutrition,
                    "calories": self._extract_energy_kcal(item),
                    "protein": self._extract_macro(item, ["protein", "proteins"]),
                    "fat": self._extract_macro(item, ["fat", "fats", "lipids"]),
                    "carbs": self._extract_macro(item, ["carbohydrates", "carbs", "carbohydrate"]),
                }
            )

        logger.info(
            "LogMeal item-level nutrition diagnostics image_id=%s previews=%s",
            image_id,
            item_previews,
        )

    def _safe_float(self, value: Any) -> float | None:
        try:
            if value is None or value == "":
                return None
            return float(value)
        except (TypeError, ValueError):
            return None

    def _safe_int(self, value: Any) -> int | None:
        try:
            if value is None or value == "":
                return None
            return int(value)
        except (TypeError, ValueError):
            return None
