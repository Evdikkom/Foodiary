# Foodiary LogMeal Backend

Минимальный backend для Foodiary, который:
- принимает фото еды от Android-приложения;
- отправляет фото в LogMeal;
- получает распознавание блюда;
- подтягивает ингредиенты и КБЖУ;
- возвращает нормализованный JSON для Foodiary.

## Что уже умеет

### `GET /health`
Проверка, что сервер жив и какие токены настроены.

### `POST /api/v1/vision/analyze-food`
Основной endpoint.

Принимает `multipart/form-data` с полем `image`.
Возвращает:
- `image_id`
- список найденных блюд/регионов
- top-кандидатов
- ингредиенты
- summary по калориям/белкам/жирам/углеводам

### `POST /api/v1/vision/analyze-food/raw`
Отладочный endpoint.
Возвращает сырые ответы LogMeal:
- segmentation
- ingredients
- nutrition

---

## Важный нюанс по токенам

Для реального image recognition LogMeal требует **APIUser token**, а не только company token.

Поэтому в `.env` нужны оба значения:
- `LOGMEAL_COMPANY_TOKEN`
- `LOGMEAL_APIUSER_TOKEN`

Если у тебя сейчас только company token:
1. зайди в LogMeal dashboard;
2. открой страницу пользователей;
3. найди автоматически созданного testing APIUser;
4. скопируй его token в `.env`.

---

## Запуск на Windows 11 без Docker

### 1. Открой PowerShell в папке проекта

### 2. Разреши локальные PowerShell-скрипты для текущего пользователя
```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### 3. Выполни первичную настройку
```powershell
.\scripts\windows\setup_windows.ps1
```

### 4. Открой `.env` и вставь токены
Пример:
```env
LOGMEAL_COMPANY_TOKEN=...
LOGMEAL_APIUSER_TOKEN=...
```

### 5. Запусти сервер
```powershell
.\scripts\windows\start_dev.ps1
```

### 6. Проверь, что всё работает
Открой:
- `http://localhost:8080/health`
- `http://localhost:8080/docs`

---

## Запуск через Docker Desktop на Windows 11

### 1. Установи Docker Desktop

### 2. Создай `.env`
```powershell
Copy-Item .env.example .env
```
Заполни токены.

### 3. Подними контейнер
```powershell
.\scripts\windows\start_docker.ps1
```

Сервис будет доступен на:
- `http://localhost:8080`
- `http://localhost:8080/docs`

---

## Тест запроса через Swagger UI

1. Перейди на `http://localhost:8080/docs`
2. Открой `POST /api/v1/vision/analyze-food`
3. Нажми `Try it out`
4. Загрузи фото еды
5. Выполни запрос

---

## Тест через curl

```bash
curl -X POST "http://localhost:8080/api/v1/vision/analyze-food" \
  -H "accept: application/json" \
  -H "Content-Type: multipart/form-data" \
  -F "image=@sample_meal.jpg"
```

---

## Что делать дальше после первого успешного ответа

После того как сервер заработает, следующий шаг в Foodiary:
1. добавить в Android экран фото еды;
2. отправлять изображение на этот backend;
3. показывать пользователю черновик распознанного блюда;
4. дать подтвердить / удалить / исправить найденные позиции;
5. только после подтверждения сохранять блюдо в дневник.

---

## Текущие ограничения MVP

1. Сейчас сервер использует базовый flow без отдельного шага `confirm dish`.
   То есть LogMeal вернёт top-1 prediction, если пользователь ещё не подтвердил блюдо.

2. Количество/масса блюда пока не подтверждаются отдельно.
   Поэтому nutrition может быть рассчитана относительно стандартной порции.

3. Логика нормализации ответа сделана устойчивой, но LogMeal может менять состав полей в зависимости от плана и модели.

---

## Следующий серверный пакет

Следующим этапом имеет смысл добавить:
- user confirmation flow;
- confirm dish endpoint;
- confirm quantity endpoint;
- отдельный Android-friendly DTO под экран подтверждения;
- кэширование и журнал запросов.


## Security patch notes

Before exposing the backend over a public tunnel, set these values in `.env`:

```env
FOODIARY_PUBLIC_API_KEY=replace-with-long-random-string
ENABLE_RAW_DEBUG_ENDPOINT=false
APP_DEBUG=false
```

All photo-analysis endpoints now require the `X-API-Key` header. The Android app must send the same key.

Recommended tunnel test flow on Windows 11:
1. Start backend locally.
2. Run a public HTTPS tunnel to `http://localhost:8080`.
3. Put the external HTTPS URL into the Android app `FOODIARY_BACKEND_BASE_URL`.
4. Put the same `FOODIARY_PUBLIC_API_KEY` value into Android `BuildConfig`.
5. Verify `GET /health` from laptop, then test photo analysis from the phone.
