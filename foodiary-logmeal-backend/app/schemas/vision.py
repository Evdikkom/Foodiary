from typing import Any
from pydantic import BaseModel, Field


class DishCandidate(BaseModel):
    class_id: int | None = None
    name: str
    confidence: float | None = None


class DetectedDishItem(BaseModel):
    item_index: int
    top_candidate: DishCandidate | None = None
    candidates: list[DishCandidate] = Field(default_factory=list)
    serving_size: str | None = None
    calories_kcal: float | None = None
    protein_g: float | None = None
    fat_g: float | None = None
    carbs_g: float | None = None
    ingredients: list[str] = Field(default_factory=list)
    raw_region: dict[str, Any] | None = None


class NutritionSummary(BaseModel):
    calories_kcal: float | None = None
    protein_g: float | None = None
    fat_g: float | None = None
    carbs_g: float | None = None


class AnalyzeFoodResponse(BaseModel):
    success: bool = True
    image_id: str
    items: list[DetectedDishItem]
    summary: NutritionSummary
    raw_dish_label: str | None = None
    notes: list[str] = Field(default_factory=list)


class AnalyzeFoodRawResponse(BaseModel):
    success: bool = True
    segmentation: dict[str, Any]
    ingredients: dict[str, Any]
    nutrition: dict[str, Any]
