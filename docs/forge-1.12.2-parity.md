# Карта паритета BBS FS 1.12.2

Актуально на 2026-10-05. Полный порт **не завершён**. Baseline `41fc825c0`
собран и выдан до изменений, после чего проверен через ai_helper. Последний
подтверждённый общий runtime checkpoint — build12; build13 собран: настоящий Forge release
client, Java 8, OptiFine G5, скрытый рабочий стол Windows. Номера build —
локальные метки этой сессии, не версии продукта.

## Подтверждено в текущей серии игровых проверок

| Система | Что реально проверено | Отчёт в `build/reports` |
| --- | --- | --- |
| Dashboard | Исходные панели, настройки, кириллица, создание/сохранение/открытие фильма; восстановлены исходные горячие клавиши с сохранением пользовательских настроек | `forge1122-dashboard-release-smoke.json` |
| Формы | Block/вода, Item, Label, Structure с fluid/TESR, Spline repeat, framebuffer; сериализация, GPU picking и восстановление GL | `forge1122-extended-forms-smoke.json` |
| Мобы | Pig, player wide/slim, поза головы, attached form, выбор костей и матрицы | тот же extended forms report |
| Видеоформа | Настоящий FFmpeg decode red/green, frame6/frame16, loop, отдельный замороженный UI preview | `forge1122-video-forms-smoke.json` |
| Частицы | Molang/эмиттер, компоненты, simulation, Bedrock/vanilla GPU pixels, pause/resume | `forge1122-particles-smoke.json` |
| Структуры | Исходный диалог wand, сохранение server Template, recent/cache invalidation | `forge1122-structure-wand-smoke.json` |
| Блоки моделей | Полные properties, body/light/sound/collision, cameraCollision отдельно от solid, NBT/packets, 4 item forms, replay transform | `forge1122-model-blocks-smoke.json` |
| Кривые шейдеров | Реальные GLSL uniforms, интерполяция и сброс после clip/stop; QA pack и Complementary | `forge1122-shader-curves-smoke.json`, `forge1122-shader-curves-complementary-smoke.json` |
| Экспорт | Реальная кнопка Record в Film UI, H264 1280×720/60fps/120frames/2s, AAC48kHz440Hz; кадр без служебных линий | `forge1122-export-audio-smoke.json` |
| Импорт | Исходные importers и выходные файлы; PNG/skin/audio/video conversions | `forge1122-importers-smoke.json` |
| Аддоны | Настоящее обнаружение Forge ASM, порядок common/client событий, доступ к addon assets и version guard | `forge1122-addon-smoke.json` |
| Запись действий | Реальные native ItemStack/World hooks и ActionManager | `forge1122-actions-smoke.json` |
| Оружие | Native item/entity, spawn payload, launch/bounce/stick/fall/damage/commands/lifetime/zoom | `forge1122-guns-smoke.json` |
| Mob/Trail | Матрицы standing/crouch/bow/glide, attached forms, движущийся след, pause/expiry и восстановление GL | `forge1122-trail-mob-smoke.json` |
| Предметы моделей | Реальный RenderItem: GUI/first/third/ground; GPU-рисование трещин разрушения | `forge1122-model-item-gpu-smoke.json` |
| Selectors | Правила выбора, приоритет/NBT, tick/render hooks, исходный UI и сохранение | `forge1122-selectors-smoke.json` |
| Экспорт мира | F4 отмена прогрева/запись, F6 фильм со звуком 440 Гц, завершение, изменение и восстановление размера окна | `forge1122-world-export-smoke.json` |
| Dedicated server | Загрузка настоящего release JAR без ai_helper/OptiFine, мир, команды, модельные/chroma блоки, сохранение и штатная остановка | `forge1122-dedicated-smoke.json` |

Исходные камера, timeline/clips, gizmo, геометрия/rigging, animation states,
медиа и Film backend подключены и проверялись на предыдущих этапах. Их старые
отчёты не заменяют общий регрессионный прогон после последних изменений.

## Текущая работа и границы подтверждения

- PBR: реальные LabPBR карты, material sliders, relief и animated maps прошли
  первый GPU-прогон. Проверка OldPBR и визуальная проверка материалов продолжаются.
- Native world forms с shader pack: 7 форм уже дали GPU pixels и GL0;
  CEM catalog/load/reload/remove прошёл. Полный CEM visual smoke повторяется
  после исправления камеры самого проверочного стенда.
- Sun horizontal rotation: реальные shadow matrices/light direction/reset
  проверены; визуальный стенд камеры исправлен и ожидает повторного прогона.
- Mob: новые позы и Trail проверены; новая проверка цвета кожаной брони и
  управления персонажем в Film UI ожидает запуска build13.
- Chroma blocks, Minecraft texture browser и дробное pixel-art UI smoothing:
  source готов, проверяется новой сборкой и GPU smoke.
- Native drag-and-drop: настоящие Win32 WM_DROPFILES, два Unicode PNG,
  очередь/слушатель/восстановление экрана проверены на скрытом окне.
- Требуются совместный регрессионный прогон, проверка без OptiFine и проверка
  multiplayer границ и новых команд /bbs. Сложные сочетания прозрачных форм,
  материалы разных shader packs и физический UI-путь предметов покрыты частично.
- Видеоform decode прошёл; найдена блокировка временного MP4 при закрытии,
  проверяется освобождение decoder/preview ресурсов.

Framebuffer пользователь разрешил исключить, если перенос слишком сложен.
Простая и вложенная формы уже рисуются после исправления native projection
stack, но полное совпадение этой функции не является обязательным критерием.

Все приведённые PASS относятся к конкретным сценариям, а не к гарантии полной
совместимости любого проекта BBS или стороннего модпака. Инструкции сборки и
запуска находятся в [руководстве порта](forge-1.12.2.md).
