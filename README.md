# Foodiary

Foodiary - Android-приложение для ведения дневника питания, учета калорий и БЖУ, работы с продуктами, рецептами, ограничениями пользователя и персональными рекомендациями.

Проект подготовлен для задания «Демонстрация практических навыков». В архиве находятся:

> Важно: верхний каталог пакета с `Foodiary/`, `foodiary-logmeal-backend/` и `README.md` является демонстрационным контейнером, а не Android/Gradle-проектом. В Android Studio нужно открывать вложенную папку `Foodiary/`. Если открыть внешний каталог, в Run Configuration поле `Module` может показывать только `<no module>`.

- `Foodiary/` - основной Android-проект;
- `foodiary-logmeal-backend/` - дополнительный FastAPI backend для распознавания еды по фотографии через внешний сервис LogMeal;
- `README.md` - инструкция по запуску и краткое описание структуры проекта.

## Кратко о проекте

Основная задача Foodiary - помочь пользователю вести дневник питания и принимать более осознанные решения по рациону. Приложение позволяет добавлять приемы пищи, считать дневные показатели по калориям, белкам, жирам и углеводам, хранить продукты и рецепты, учитывать пользовательские ограничения и формировать рекомендации продуктов.

С инженерной точки зрения проект состоит из нескольких частей:

- локальное Android-приложение на Kotlin;
- локальная база данных Room;
- слой бизнес-логики с use case-классами;
- сетевые интеграции через Retrofit и OkHttp;
- импорт продуктов из Open Food Facts;
- опциональная интеграция с backend-сервисом для анализа еды по фотографии.

## Требования для запуска Android-приложения

Перед запуском рекомендуется установить:

- Android Studio;
- Android SDK Platform 34;
- JDK 17 или выше. При запуске из Android Studio можно использовать встроенный JBR;
- эмулятор Android или физическое устройство.

Параметры Android-проекта:

- `minSdk`: 24;
- `targetSdk`: 34;
- `compileSdk`: 34;
- Gradle Wrapper: 8.13;
- Android Gradle Plugin: 8.13.0;
- Kotlin Android Plugin: 1.9.22.

Основные зависимости Android-приложения перечислены в файле:

```text
Foodiary/app/build.gradle.kts
```

Среди ключевых библиотек: AndroidX, Material Components, Room, Kotlin Coroutines, Retrofit, OkHttp, Coil, CameraX и ML Kit Barcode Scanning.

## Рекомендуемый способ запуска через Android Studio

1. Распакуйте архив в путь без кириллицы и пробелов.

   Рекомендуемый пример:

   ```text
   C:\FoodiaryDemo
   ```

   После распаковки структура должна выглядеть так:

   ```text
   C:\FoodiaryDemo\
     Foodiary\
     foodiary-logmeal-backend\
     README.md
   ```

2. Откройте Android Studio.

3. Выберите `Open` и откройте именно папку Android-проекта:

   ```text
   C:\FoodiaryDemo\Foodiary
   ```

   Важно: открывать нужно папку `Foodiary`, а не общий корень архива.

4. Дождитесь завершения Gradle Sync.

5. Если Android Studio предложит установить недостающие компоненты SDK, установите Android SDK Platform 34.

6. Выберите эмулятор или подключенное Android-устройство.

7. Запустите конфигурацию `app` через кнопку `Run`.

При первом запуске приложение может попросить разрешения, связанные с камерой или уведомлениями. Камера нужна для сценариев, связанных со штрихкодом и фото.

## Если Android Studio показывает `Add Configuration`

Иногда после первого открытия проекта Android Studio не создает конфигурацию запуска автоматически. Это не ошибка кода. Обычно достаточно дождаться Gradle Sync или создать конфигурацию вручную.

Перед созданием конфигурации проверьте два момента:

- открыт каталог `C:\FoodiaryDemo\Foodiary`, а не общий каталог `C:\FoodiaryDemo`;
- Gradle Sync завершился без ошибок. При необходимости нажмите `File -> Sync Project with Gradle Files`.

Если в верхней панели Android Studio вместо `app` отображается `Add Configuration`, выполните следующие действия:

1. Нажмите `Add Configuration`.
2. В окне `Run/Debug Configurations` нажмите `+`.
3. Выберите `Other -> Android App`. В некоторых версиях Android Studio пункт может называться просто `Android App`.
4. В поле `Name` укажите `app`.
5. В поле `Module` выберите модуль `Foodiary.app` или `app`.
6. В `Launch Options` оставьте запуск `Default Activity`.
7. В выборе устройства оставьте запуск на выбранном эмуляторе или подключенном устройстве.
8. Нажмите `Apply`, затем `OK`.
9. После этого выберите конфигурацию `app` в верхней панели и нажмите `Run`.

В проект также добавлена shared-конфигурация запуска:

```text
Foodiary/.run/app.run.xml
```

Если Android Studio поддерживает shared run configurations, конфигурация `app` появится автоматически после открытия проекта и Gradle Sync. Если она не появилась, используйте ручной способ выше.

Если в списке `Module` нет `Foodiary.app` или `app`, значит Android Studio еще не распознала Gradle-проект. В первую очередь проверьте, что открыт не внешний каталог пакета, а вложенная папка `Foodiary`, где лежит `settings.gradle.kts`. После этого дождитесь синхронизации Gradle или переоткройте проект через `File -> Open`.

## Сборка через командную строку Windows

Если нужно проверить сборку без Android Studio, можно использовать Gradle Wrapper.

Откройте PowerShell или Command Prompt и перейдите в папку Android-проекта:

```powershell
cd C:\FoodiaryDemo\Foodiary
```

Если `JAVA_HOME` не настроен или в системе по умолчанию используется старая Java, укажите JBR из Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

Укажите путь к Android SDK:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

Запустите сборку debug-версии:

```powershell
.\gradlew.bat :app:assembleDebug
```

После успешной сборки APK будет создан здесь:

```text
Foodiary/app/build/outputs/apk/debug/app-debug.apk
```

Если сборка сообщает, что Android SDK не найден, можно создать файл `Foodiary/local.properties` и указать путь к SDK вручную:

```properties
sdk.dir=C:\\Users\\<имя_пользователя>\\AppData\\Local\\Android\\Sdk
```

Файл `local.properties` специально не включается в архив, потому что он зависит от конкретного компьютера.

## Первый запуск приложения

После запуска приложения рекомендуется пройти базовый сценарий:

1. Заполнить профиль пользователя или пройти стартовую настройку.
2. Открыть главный экран дневника питания.
3. Нажать добавление приема пищи.
4. Выбрать тип приема пищи: завтрак, обед, ужин или перекус.
5. Найти продукт в локальной базе или через внешний поиск.
6. Указать массу продукта в граммах.
7. Сохранить прием пищи.
8. Вернуться на главный экран и проверить пересчет дневных калорий и БЖУ.

Для демонстрации сложной логики удобно показать персональные рекомендации продуктов. Они формируются не случайно: приложение учитывает историю питания, избранные продукты, текущий дневной баланс БЖУ, цель пользователя, время приема пищи, практичность порции и ограничения по аллергенам.

Ключевые файлы для демонстрации кода:

```text
Foodiary/app/src/main/java/com/example/foodiary/presentation/viewmodel/AddMealViewModel.kt
Foodiary/app/src/main/java/com/example/foodiary/domain/usecase/GetPersonalizedFoodRecommendationsUseCase.kt
Foodiary/app/src/main/java/com/example/foodiary/data/local/database/AppDatabase.kt
Foodiary/app/src/main/java/com/example/foodiary/data/repository/FoodImportRepositoryImpl.kt
```

## Структура Android-проекта

```text
Foodiary/
  app/
    src/main/
      java/com/example/foodiary/
        data/          # локальная база, репозитории, сетевые источники
        domain/        # модели предметной области и use case-логика
        presentation/  # экраны, адаптеры, ViewModel
      res/             # layout, drawable, values и другие Android-ресурсы
  build.gradle.kts
  settings.gradle.kts
  gradlew
  gradlew.bat
```

### Слой `data`

В этом слое находятся Room-сущности, DAO, база данных, репозитории, seed-каталоги продуктов и интеграции с внешними API.

Пример: `AppDatabase.kt` описывает локальную базу `foodiary.db`, ее сущности и первоначальное заполнение продуктами и аллергенами.

### Слой `domain`

Здесь расположены модели предметной области и use case-классы. Этот слой отделяет бизнес-логику от интерфейса и источников данных.

Пример: `GetPersonalizedFoodRecommendationsUseCase.kt` рассчитывает персональные рекомендации продуктов.

### Слой `presentation`

Этот слой отвечает за пользовательский интерфейс: фрагменты, адаптеры, ViewModel и состояние экранов.

Пример: `AddMealViewModel.kt` управляет поиском продуктов, импортом, выбором продукта, сохранением приема пищи и загрузкой рекомендаций.

## Импорт продуктов из Open Food Facts

Приложение поддерживает внешний источник продуктов Open Food Facts:

- поиск продуктов по названию;
- импорт продукта по штрихкоду;
- сохранение КБЖУ и изображения продукта;
- применение информации по аллергенам, если она есть во внешнем ответе.

Для этой функции нужен доступ к интернету. Если внешний сервис временно недоступен, базовые функции приложения и локальная база продолжают работать.

## Опциональный backend для распознавания еды по фото

Папка `foodiary-logmeal-backend/` содержит вспомогательный backend-сервис на FastAPI. Он нужен для функции анализа еды по фотографии.

Backend принимает изображение от Android-приложения, обращается к LogMeal, нормализует ответ и возвращает результат в приложение.

### Требования backend-модуля

- Python 3.11 или выше либо Docker;
- зависимости из файла `foodiary-logmeal-backend/requirements.txt`;
- действующие токены LogMeal.

### Запуск backend на Windows без Docker

Перейдите в папку backend:

```powershell
cd C:\FoodiaryDemo\foodiary-logmeal-backend
```

Создайте `.env` на основе примера:

```powershell
copy .env.example .env
```

Заполните в `.env` значения:

```env
LOGMEAL_COMPANY_TOKEN=...
LOGMEAL_APIUSER_TOKEN=...
FOODIARY_PUBLIC_API_KEY=replace-with-long-random-string
ENABLE_RAW_DEBUG_ENDPOINT=false
APP_DEBUG=false
```

Установите окружение и запустите сервис:

```powershell
.\scripts\windows\setup_windows.ps1
.\scripts\windows\start_dev.ps1
```

Проверка запуска:

```text
http://localhost:8080/health
http://localhost:8080/docs
```

### Запуск backend через Docker

Если установлен Docker, можно использовать готовый скрипт:

```powershell
cd C:\FoodiaryDemo\foodiary-logmeal-backend
copy .env.example .env
```

После заполнения `.env`:

```powershell
.\scripts\windows\start_docker.ps1
```

### Подключение backend к Android-приложению

В Android-приложении нужно указать:

- адрес backend-сервиса, например `http://10.0.2.2:8080/` для эмулятора Android;
- тот же API-ключ, который указан в `FOODIARY_PUBLIC_API_KEY`.

Для физического устройства вместо `10.0.2.2` обычно используется IP-адрес компьютера в локальной сети.

## Что не включено в архив

В архив специально не включены:

- скомпилированные APK/AAB-файлы;
- папки `build/`;
- папки `.gradle/`;
- папка `.idea/`;
- папки зависимостей;
- `.env` с секретами;
- `local.properties`, потому что путь к Android SDK индивидуален для каждого компьютера.

Это сделано для того, чтобы архив содержал только исходный код и необходимые проектные файлы.

## Возможные проблемы и решения

### В поле `Module` доступно только `<no module>`

Если при создании Android App configuration в поле `Module` доступно только `<no module>`, Android Studio еще не импортировала Gradle-модуль `app`. В такой ситуации запуск через Run Configuration не поможет, пока не будет исправлена причина Gradle Sync.

Самая частая причина на Windows - путь к проекту с кириллицей. Например, путь вида:

```text
C:\Users\Админ\Documents\...
```

может приводить к ошибке Gradle при создании локального кеша `.gradle`. В результате Android Studio открывает файлы проекта, но не видит модуль `app`.

Рабочий порядок действий:

1. Закройте проект в Android Studio.
2. Распакуйте архив в простой путь без кириллицы и пробелов, например:

   ```text
   C:\FoodiaryDemo
   ```

3. Откройте в Android Studio именно папку:

   ```text
   C:\FoodiaryDemo\Foodiary
   ```

4. Дождитесь `Gradle Sync`.
5. Откройте `View -> Tool Windows -> Gradle`. В дереве проекта должен появиться `Foodiary -> app`.
6. После этого в Run Configuration поле `Module` должно содержать `Foodiary.app` или `app`.

Дополнительно можно проверить импорт проекта из командной строки:

```powershell
cd C:\FoodiaryDemo\Foodiary
.\gradlew.bat projects
```

Ожидаемый результат:

```text
Root project 'Foodiary'
\--- Project ':app'
```

Если команда `gradlew.bat projects` не показывает `Project ':app'`, Android Studio тоже не сможет запустить приложение через `Run`.

### Android Studio открыта не в той папке

Если Android Studio не видит модуль `app`, проверьте, что открыта папка:

```text
C:\FoodiaryDemo\Foodiary
```

а не общий корень архива.

### Используется слишком старая Java

Если сборка падает из-за версии Java, укажите JBR из Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### Не найден Android SDK

Укажите переменную окружения:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

или создайте файл `local.properties` в папке `Foodiary`.

### Проблемы из-за пути с кириллицей

На Windows Gradle иногда нестабильно работает, если проект расположен в пути с кириллицей. Рекомендуется распаковать проект в простой путь:

```text
C:\FoodiaryDemo
```

### Не работает поиск внешних продуктов

Проверьте подключение к интернету. Функция внешнего поиска зависит от доступности Open Food Facts.

### Не работает распознавание еды по фото

Проверьте, что backend запущен, адрес backend указан в приложении, API-ключ совпадает, а токены LogMeal заполнены корректно.

## Проверка подготовленного архива

Перед подготовкой этого пакета была выполнена проверка:

- исходный архив не содержал `build/`, `.gradle/`, `.idea/`, `.git`, `.env`, `local.properties` и папок зависимостей;
- Android-проект был успешно собран через Gradle Wrapper в отдельной проверочной директории без кириллицы в пути;
- итоговый APK создавался в `Foodiary/app/build/outputs/apk/debug/app-debug.apk`;
- сгенерированные артефакты сборки не включались в итоговый архив исходного кода.

## Автор

Евдокимов Артемий Русланович.








