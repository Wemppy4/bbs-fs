# Карта паритета BBS FS 1.12.2

Актуально на 2026-10-05. Основные системы порта реализованы; ниже перечислены
подтверждённые сценарии и границы проверки. Baseline `41fc825c0` собран и выдан
до изменений, после чего проверен через ai_helper. Последний подтверждённый
широкий runtime checkpoint — build17: настоящий Forge release client, Java 8,
с OptiFine G5 и без него, а также отдельный dedicated server. Номера build —
локальные метки этой сессии, не версии продукта.

## Исправления после пользовательской проверки build17

Build20 передан на ручную проверку по просьбе пользователя. Сборка и 44 теста
проходят; это не заявление о завершении порта. Подтверждённые регрессии:

- Build18: настоящий редактор частиц получает текущую долю тика (90 кадров,
  0 расхождений); редактор пикселей обрабатывает wheel каждый кадр (90/90
  событий, изменение zoom между тиками). Главный игрок скрывается при входе
  в редактор и возвращается при выходе, preview-player остаётся видимым.
- Build18: Shift+T и клавиши интерполяций проверены через native input;
  цвета XYZ проверены на GPU/снимке. Категория управления одна. Стандартная
  клавиша Dashboard — 0, сохранённая пользовательская R не изменяется.
- Build19: creative model block и вкладка используют оригинальные billboard
  textures/NBT. Четыре item perspective и трещины проверены при фиксированном
  дневном освещении. Defaults billboard/extruded используют icon.png.
- Build19: BBS Gun сверен с оригиналом; внешнее перемещение освобождает
  застрявший снаряд, отклонённый урон восстанавливает точный fire timer.
  Оба случая, прежние gun-сценарии и отдельный dedicated server прошли.
- На исходном build17 воспроизведён сломанный block icon при отключённом cull:
  GL_ALWAYS рисует внутренние стороны куба, LEQUAL даёт правильный результат.
  Native item boundary теперь выставляет LEQUAL и включает запись глубины.
- Затемнение Model preview подтверждено GPU-сравнением build17/19; перенесены
  исходные GUI light vectors. Build20 дополняет нормали Mob и свет прозрачных
  частей; визуальная проверка последней коррекции оставлена пользователю.

Исчезновение UI при minecraft-структуре пока **не закрыто**: исходный build17
с пользовательским 123.nbt, UI scale 1.5, RP_MODE=2 и настоящим Dashboard
не воспроизвёл сбой. Прохождение этих сценариев не доказывает исправление
жалобы. Копия пользовательского мира с исходной позицией и соседними блоками
моделей также не воспроизвела исчезновение UI. Периодическая потеря
глубины всего превью требует пользовательского подтверждения отдельно от
воспроизведённого дефекта иконок.

Отчёты: `qa-bugs18/`, `qa-bugs19/`, `qa-baseline17/` внутри `build/reports`.

## Подтверждено в текущей серии игровых проверок

| Система | Что реально проверено | Отчёт в `build/reports` |
| --- | --- | --- |
| Dashboard | Исходные панели, настройки, кириллица, создание/сохранение/открытие фильма; восстановлены исходные горячие клавиши с сохранением пользовательских настроек | `forge1122-dashboard-release-smoke.json` |
| Общая регрессия build17 | Все панели Dashboard, создание/дублирование/переименование/удаление/открытие фильма; модельный редактор 1280×720, gizmo picking/drag/numeric/spaces, модельные GPU shaders | `qa/forge1122-dashboard-release-smoke.json`, `qa/forge1122-original-gizmo-smoke.json`, `qa/forge1122-original-model-gpu-smoke.json` |
| Без OptiFine | В runtime подтверждено отсутствие OptiFine. Формы/picking/прозрачность, model-item/cracks, chroma/resources/PixelArt, модельные GPU shaders и редактор прошли; скриншоты просмотрены | `qa-no-optifine/forge1122-no-optifine-suite.json` и отдельные отчёты рядом |
| Формы | Build16: Block/вода, Item, Label, Structure с fluid/TESR, Spline repeat, простой и вложенный framebuffer; 12 видимых форм и roundtrip, GPU picking без неизвестных ID, восстановление GL | `qa/forge1122-extended-forms-smoke.json` |
| Мобы | Build16: pig, player wide/slim, поза головы, attached form, выбор костей и матрицы | `qa/forge1122-extended-forms-smoke.json` |
| Видеоформа | Настоящий FFmpeg decode red/green, frame6/frame16, loop, отдельный замороженный UI preview; оба decoder владельца освобождены, временный MP4 удалён | `forge1122-video-forms-smoke.json` |
| Частицы | Molang/эмиттер, компоненты, simulation, Bedrock/vanilla GPU pixels, pause/resume | `forge1122-particles-smoke.json` |
| Структуры | Диалог wand → server Template → ответ/cache/recent; размер и блоки после загрузки совпадают. Build16: GPU-геометрия fluid/TESR, края уменьшенной листвы не пропускают фон | `forge1122-structure-wand-smoke.json`, `qa/forge1122-forms-transparency-smoke.json` |
| Прозрачность форм | Build16: перестановка ближней/дальней Label, Block glass, Item glass и slime даёт попиксельно одинаковый результат; полупрозрачный красный фон Label над зелёным — RGB [126,128,0] вместо [62,128,0]; GL0 | `qa/forge1122-forms-transparency-smoke.json` |
| Блоки моделей | Полные properties, body/light/sound/collision, cameraCollision отдельно от solid, NBT/packets, 4 item forms, replay transform | `forge1122-model-blocks-smoke.json` |
| Кривые шейдеров | Реальные GLSL uniforms, интерполяция и сброс после clip/stop; QA pack и Complementary | `forge1122-shader-curves-smoke.json`, `forge1122-shader-curves-complementary-smoke.json` |
| Поворот солнца | Горизонтальная кривая одновременно поворачивает shadow matrix и light vector; остановка сбрасывает поворот, камера стенда проверена визуально | `forge1122-shader-sun-smoke.json` |
| PBR | Подтверждённые LabPBR/OldPBR RP_MODE=3/2; реальные normal/specular maps, sliders/animation, isolated materials, atlas/tangents/cleanup; свечение, металл и рельеф проверены в кадре | `forge1122-pbr-smoke.json`, `forge1122-pbr-visual-smoke.json` |
| Shader pack / CEM | Цветная CEM-модель, настоящий resource-pack enable/reload/remove, native world draws и восстановление GL; чёрный силуэт устранён и проверен визуально | `qa/forge1122-shader-world-forms-smoke.json` |
| Наложение моделей с шейдерами | GPU entityColor и изображение: custom blue0.8 → hurt red77/255 → исходный цвет; вспышка урона перекрывает custom overlay | `qa/forge1122-model-overlay-smoke.json` |
| Экспорт | Реальная кнопка Record в Film UI, H264 1280×720/60fps/120frames/2s, AAC48kHz440Hz; кадр без служебных линий | `forge1122-export-audio-smoke.json` |
| Импорт | Исходные importers и выходные файлы; PNG/skin/audio/video conversions | `forge1122-importers-smoke.json` |
| Аддоны | Настоящее обнаружение Forge ASM, порядок common/client событий, доступ к addon assets и version guard | `forge1122-addon-smoke.json` |
| Запись действий | Реальные native ItemStack/World hooks и ActionManager | `forge1122-actions-smoke.json` |
| Оружие | Native item/entity, spawn payload, launch/bounce/stick/fall/damage/commands/lifetime/zoom | `forge1122-guns-smoke.json` |
| Mob/Trail | Build16: standing/crouch/bow/glide, ошибка drawn/collected матриц 0, attached forms, обновление native model, предмет не расходуется при preview, цвет кожаной брони; Trail 6 samples, pause/unlit/expiry, GL0 | `qa/forge1122-trail-mob-smoke.json` |
| Предметы моделей | Реальный RenderItem → override → TEISR: GUI/first/third/ground выбирают свои формы, по 6090 GPU-пикселей; трещины stage0/stage9, восстановление GL | `forge1122-model-item-gpu-smoke.json` |
| Selectors | Правила выбора, приоритет/NBT, tick/render hooks, исходный UI и сохранение | `forge1122-selectors-smoke.json` |
| Экспорт мира | F4 отмена прогрева/запись, F6 фильм со звуком 440 Гц, завершение, изменение и восстановление размера окна | `forge1122-world-export-smoke.json` |
| Команды /bbs | Исходные morph/morph_entity, films play/stop с камерой и без, model state/refresh, structures save, DC, config, cheats, on_head, boom; отказ без прав, реальные client packets; HUD и GPU image/subtitle overlays | `qa/forge1122-commands-smoke.json` |
| Горячие клавиши форм | Build17: native Forge key event, GLFW-коды, приоритет morph → main model → offhand gun → Recent → User; release/repeat/GUI игнорируются. Реальный сетевой roundtrip через временный self-observer; два отдельных клиента не проверялись | `qa/forge1122-hotkeys-smoke.json` |
| Dedicated server | Build17: настоящий release JAR без ai_helper/OptiFine, отдельный server/world; RCON `/help bbs`, gamerule, установка/чтение model/chroma блоков, save-all и штатная остановка exit0 | `forge1122-dedicated-smoke.json` |
| Chroma/ресурсы/UI | 8 chroma блоков без AO/diffuse; 1460 Minecraft texture links, PNG; GPU bitmap/кириллица и сглаживание при масштабе 1.5 | `forge1122-native-resources-smoke.json` |
| Редактор модели | Build17, 1280×720: реальное создание кости/куба, русское имя, координата 3.25, undo/redo, запись JSON и повторное открытие через Dashboard; скриншот просмотрен | `qa/forge1122-model-editor-smoke.json` |
| Управление/броня | Mapping переназначенных клавиш/мыши и sneak/sprint; настоящий Forge input event блокирует движение под overlay Film UI (без OS-нажатий); build16 GPU-цвета кожаной брони | `forge1122-film-control-input-smoke.json`, `qa/forge1122-trail-mob-smoke.json` |

Исходные камера, timeline/clips, геометрия/rigging, animation states,
медиа и Film backend подключены и проверялись на предыдущих этапах.
Общий прогон интерфейса build17 и повтор изменённых рендереров приведены выше;
неизменённые подсистемы сохраняют свои отдельные отчёты предыдущих этапов.

## Текущая работа и границы подтверждения

- PBR: GPU-прогон LabPBR/OldPBR и визуальный стенд свечения/металла/рельефа
  завершены на Complementary. Произвольные сторонние shader packs не проверены.
- Native world draws / CEM повторно проверены в отдельном QA-клиенте 25615:
  8 форм, GL0/state restore, настоящий resource-pack lifecycle. Чёрный силуэт
  устранён сохранением реального GPU entityColor вместо sentinel кеша OptiFine.
  Custom overlay и hurt-flash проверены по GPU uniform и скриншотам.
- Sun horizontal rotation: shadow matrices/light direction/reset и исправленный
  визуальный стенд камеры проверены. Все shader-прогоны использовали OptiFine G5.
- Формы build16: видимость, picking, матрицы и прозрачность повторно проверены
  на отдельном release-клиенте `run-forge1122-qa`; Mob/Trail/броня также прошли.
  Label/Framebuffer после alpha-исправления видимы в shader-world прогоне
  (`qa/forge1122-shader-world-forms-smoke.json`), без GL-ошибок.
- Native drag-and-drop: настоящие Win32 WM_DROPFILES, два Unicode PNG,
  очередь/слушатель/восстановление экрана проверены на скрытом окне.
- Заключительный прогон редакторов и проверка без OptiFine завершены.
  Сетевые границы сверены с оригиналом; команды /bbs прошли полный сценарий
  с камерой. Сеанс с двумя независимыми игроками не проверялся. Материалы
  разных shader packs и физический UI-путь предметов покрыты частично.
- Формы/picking, прозрачность и model-item/cracks без OptiFine прошли повтор
  на build17. Wand-отчёт проверяет изменение
  выделения через production-методы и настоящий диалог Save/сеть; use/attack/
  Alt-wheel через native input events отдельно не проверялись. Это граница
  покрытия, а не подтверждённая отсутствующая функция.
- Видеоформа: decode и освобождение обоих decoder/preview владельцев прошли;
  после завершения процессов временный MP4 успешно удаляется (наблюдалась
  краткая задержка Windows около 10 мс).

Framebuffer пользователь разрешил исключить, если перенос слишком сложен.
Простая и вложенная формы прошли build16 GPU/picking после исправления native
projection stack и alpha-композитинга; полное совпадение этой функции не является
обязательным критерием.

Для этой части повторно сверены `bb-sources/blockbuster-1.12/.../StructureRenderer.java`
(раздельные solid/cutout/translucent и TESR) и `bb-sources/metamorph-1.12/.../ItemMorph.java`,
`BodyPart.java` (native item transforms и привязки). Правило непрозрачных cutout
сохранено для Structure; на BlockForm оно не распространено, поскольку исходный BBS FS
включает для него blending явно.

Все приведённые PASS относятся к конкретным сценариям, а не к гарантии полной
совместимости любого проекта BBS или стороннего модпака. Инструкции сборки и
запуска находятся в [руководстве порта](forge-1.12.2.md).
