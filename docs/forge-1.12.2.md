# BBS FS для Minecraft Forge 1.12.2

Рабочая ветка `1.12.2`. Полный порт находится в разработке. Успешная сборка
сама по себе не означает совпадения с BBS FS: текущие игровые проверки и
оставшиеся расхождения перечислены в [карте паритета](forge-1.12.2-parity.md).

## Установка

Проверенная среда: Minecraft **1.12.2**, Forge **14.23.5.2847**, Java **8**.
Установочный JAR из `build/libs` помещается в `mods`; `-dev` и `-sources`
для установки не нужны. JOML и необходимые runtime-библиотеки входят в JAR.
Fabric, Blockbuster, McLib, Metamorph и AI Helper для обычной игры не нужны.

**0** открывает исходный Dashboard BBS FS: морфинг, фильмы, блоки моделей,
ресурсы и редакторы. Старый тестовый StudioScreen больше не является основным
интерфейсом. **Home** открывает редактор поддерживаемого предмета в руке.
**F4** запускает запись мира, **F6** — воспроизведение выбранного фильма с записью. Модельный блок редактируется через исходную панель; творческий режим и
проверки сервера ограничивают изменение блоков и предметов.

Глобальная библиотека находится в `config/bbs/assets`; дополнительные ресурсы
одиночного мира — в `saves/<мир>/bbs/assets`. Приоритет: глобальные ресурсы,
ресурсы мира, встроенная библиотека. Модели: `assets/models/<имя>/` с
`model.bbs.json`, текстурами и `config.json`. Доступны исходные загрузчики
кубических, mesh и CEM моделей. Текстуры Minecraft читаются из активных паков.
Автоматическая пересылка библиотеки на удалённый клиент не реализована.

Исходный шрифт 1.20.4 используется и на 1.12.2. При первом запуске нужные
font assets подготавливаются в `config/bbs/runtime/fonts-1.20.4`; статус
подготовки и ошибки показываются в интерфейсе. Для видео и импорта медиа
нужен FFmpeg, который использует обычная конфигурация BBS.

## Сборка

Gradle **8.12**, RetroFuturaGradle **1.4.6**, MCP **stable_39**. Gradle
запускается на JDK 17; Minecraft и результат сборки работают на Java 8.
Jabel позволяет сохранить современный синтаксис общей кодовой базы.

```powershell
.\gradlew.bat build
```

`src/forge/java` содержит native Forge/LWJGL2 границы. Оригинальные классы из
`src/main/java` и `src/client/java` включаются через `gradle/forge-core.txt`
и `gradle/forge-client.txt`. Генератор копирует их в `build/generated`;
исправлять нужно исходники, а не сгенерированные копии. Современные исходники,
которые не перечислены в manifests и не имеют native версии, не входят в JAR.
`src/forgeTest` содержит проверки перенесённого ядра.

OptiFine G5 подключается для тестов флагом `-PwithOptiFine`; задача подготовки
проверяет SHA256. Он остаётся отдельным модом и не включается в BBS JAR.
Кривые шейдеров и PBR требуют совместимого shader pack. Воспроизводимые
проверки используют небольшой QA pack и Complementary Unbound 5.9.3.

## Проверки через AI Helper

`src/aihelper` собирается отдельным тестовым модом и не попадает в BBS JAR.
Используется протокол и `tools/mc.py` из соседнего `../ai_helper`.
Release-проверки запускают обфусцированный JAR в `run-forge1122-obf`, мир
`ai_test`; dev-клиент использует отдельный каталог `run-forge1122`.

```powershell
.\gradlew.bat build reobfAihelper
$env:AIH_PORT = '25612'
$env:AIH_DIRECT_UI = '1'
python tools/forge1122/background_client.py --timeout 300 --gradle-args '-PwithOptiFine -PforgeReleaseTest runObfClient --no-daemon'
python tools/forge1122/dashboard_smoke.py --release
python tools/forge1122/extended_forms_smoke.py
python tools/forge1122/export_audio_smoke.py
python ../ai_helper/tools/mc.py stop
```

`background_client.py` запускает клиент на отдельном скрытом рабочем столе
Windows, без переключения пользовательского рабочего стола. OpenGL и
снимки работают. `AIH_DIRECT_UI=1` отправляет UI-события через ai_helper
без движения системного курсора. Прогоны, которые меняют экран, выполняются
последовательно. Перед заменой запущенного JAR клиент останавливается.
Отчёты находятся в `build/reports/forge1122-*-smoke.json`, снимки — в
`run-forge1122-obf/screenshots/ai`.

## Донорские исходники

Порт сверяется с оригиналом BBS FS и `../bb-sources`:

- Blockbuster: Forge lifecycle, структура/Template, TESR модельных блоков,
  перечисление resource packs, снаряды и столкновения.
- Aperture: shader curves, uniform rewriting и поворот солнца.
- Metamorph: native mob models, кости/присоединённые формы и события рендера.
- McLib: UI/GL подходы 1.12 и платформенные утилиты.
- Minema: границы фиксированного времени и записи кадров.

Это адаптация существующих решений к архитектуре BBS FS, а не замена BBS
донорскими интерфейсами. Подробности подтверждённого состояния — в карте паритета.


Dedicated smoke: `python tools/forge1122/dedicated_smoke.py` запускает настоящий
release Forge server без GUI/OptiFine/ai_helper в отдельном
`run-forge1122-server`, только 127.0.0.1. Мир `ai_server_test` и RCON-пароль
одноразовой проверки остаются в этом игнорируемом каталоге. Сценарий проверяет
загрузку, команды, native блоки, сохранение и штатную остановку; report —
`build/reports/forge1122-dedicated-smoke.json`. Подключение нескольких клиентов
и серверные командные сценарии проверяются отдельно.
