# BBS FS 1.12.2: карта паритета

Снимок 2026-10-04, ветка `1.12.2`. Цель — исходный интерфейс и поведение BBS FS
1.20.4. `StudioScreen` не является готовым результатом порта. Ни компиляция
отдельного виджета, ни его проверочный экран не подтверждают готовность Dashboard.

## Что уже есть

Поздний runtime checkpoint 2026-10-04: build9 compileJava/aihelperClasses PASS.
Исправлены miniatures depth test и восстановление FBO после Film picking; оба
дефекта подтверждены пользователем как исправленные. Root сравнил 12 кадров до
(2 пропажи панели) и 24 после (0 пропаж). Настоящий экспорт из Film UI создал
H264 1280×720/60fps/300frames/5s и восстановил редактор, GL0; отчёт
`build/reports/forge1122-export-smoke.json`. Вспомогательные линии редактора пока
попадают в видео — остающийся дефект; аудиодорожка этим прогоном не подтверждена.
Полный паритет не достигнут. Работа остановлена по просьбе пользователя из-за лимитов.

| Область | Реализовано | Подтверждённая проверка | Что осталось |
| --- | --- | --- | --- |
| Данные | Value/Form/ModelForm, Film/Replay/FilmManager, ключи, категории дорожек и кубические модели | В передаче предыдущей сессии: 9 JUnit, сохранение фильма/формы и NBT | Сложные формы и поведение современных ригов не покрыты полной проверкой |
| Основа UI | Исходные UIElement, раскладка, поля, меню, overlays, hotkeys; Forge UIScreen и нижний рендер | `original_ui_smoke.py`, скриншоты и GL0 по передаче предыдущей сессии | Полное соответствие шрифтов/pixel-art filtering и подключение к редакторам |
| Кейфреймы | Исходный UIKeyframes/graph/dope sheet, styles, loops, previews | `timeline_smoke.py`, GL0 и скриншоты по передаче предыдущей сессии | Связь с настоящим UIFilmPanel и редактором состояний |
| Клипы | Исходный UIClips и контроллеры маркеров; Forge vertex backend для огибающей; native TimelineEvents | Два полных `clips_smoke.py` PASS: реальные mouse/key/menu, waveform, markers, данные, GL0; `build/reports/forge1122-original-clips-smoke.json` | Связь с настоящим UIFilmPanel, playback и undo |
| Медиа клипов | VideoPlayer/VideoManager, OpenAL SoundManager, waveform, FFmpeg recorder и фабрики подключены | Свежий `media_smoke.py` PASS сообщён координирующим агентом; звуковая волна проверена clips smoke | Полный VideoExportSession/PanelVideoExportSession и экспорт из Film UI |
| Пользовательские шрифты | Native FontManager, TTF/OTF atlas, SFNT metrics, formatting/width/trim/wrap, oversampling/expiry/watch invalidation | 6 CPU JUnit и fonts_smoke PASS; координирующий агент просмотрел скриншот | AWT raster не доказан как побитно совпадающий со STB; watcher пока не проверен в игре |
| Dashboard и панели | Настоящий UIDashboard/F6, Film UI, settings/CRUD/landing/onboarding, texture/audio UI, morphing и model editor | Build6 compile/test (33)/aihelper PASS; build7: dashboard_smoke PASS, F6/settings/CRUD/reopen .dat, сохранение изменённого clip title, GL0. Morphing отображён и demorph проверен; texture/audio вкладки открыты | Model editor ещё не проверен в игре; полный texture/audio workflow и все Film функции не покрыты. Particles/modelblocks/selectors остаются незавершёнными |
| Default font 1.20.4 | Исходные bitmap PNG/providers, space/reference/Unihex, проверяемый cache и native atlas | Координирующий агент: build3 compile/test/helper и default_font_smoke PASS; скриншот просмотрен, glyph metrics/reload/GL0 | Exact RTL и видимый сетевой failure/retry сценарий ещё не проверены в игре; CPU cache failure tests прошли общий test |
| Film/actors/actions | Реальные Films, controllers, recorder, ActorEntity, Morph, C2S/S2C, DamageControl HEAD hook | Координирующий агент: native film backend smoke PASS — packets/actor pause-stop/morph size/DC block+tile NBT restore | Расширенные Film UI сценарии и экспорт; block-break progress/use-on-block capture и неподдерживаемые в vanilla1.12 действия |

Статусы предыдущей сессии приведены по локальной передаче. При новой итоговой
проверке заменять их ссылками на актуальные отчёты, а не повторять как новый PASS.

## Почему один импорт включает почти весь клиент

Dry-run `.aihelper/ui_closure.py` на начальном manifest показал по **931** ещё не
подключённому классу для `UIDashboard`, `UIFilmPanel` и `UIFormEditor`, но только
**27** для `UIClips`. После четырёх новых Forge overrides Dashboard показывает
927. Это эвристический граф исходных ссылок, не количество ошибок компиляции:
скрипт останавливается на уже выбранных классах и не видит отсутствующие методы
в текущем `BBSModClient`.

Самая большая сильно связанная группа — 414 UI/render-классов. Отдельно есть
45 классов частиц и 16 классов actor/network. Главные обратные связи:

- `ui/dashboard/UIDashboard` регистрирует все панели, settings, onboarding и utility.
  `UIDashboardPanel` хранит ссылку обратно на Dashboard.
- `ui/ContentType` статически связывает Film, Model и Particle UI и репозитории.
  Подключение одного CRUD экрана втягивает остальные редакторы.
- `ui/forms/editors/UIFormEditor` → `cubic/ModelInstance` → `client/BBSRendering`
  → `UIDashboard` → панели → `UIFormEditor`.
- `UIFormEditor` регистрирует все типы форм; model/form preview втягивает
  `FormRenderingContext`, framebuffer, stencil, gizmo, IK, shapes и particle backend.
- `UIDashboard` → `settings/ui/UISettingsOverlayPanel` → `ui/utility/UIUtilitySettings`
  → `BBSResources`, resource importers и внешние сервисы.

Не разрезать эти связи пустыми API, fake-классами `net.minecraft`/`net.fabricmc`,
неподключёнными кнопками или удалением исходных вкладок.

## Последовательность подключения

1. Довести независимый пакет `ui/film/UIClips`, `IUIClipsDelegate`, `ICursor`,
   `ui/film/markers/*`, `ui/film/clips/renderer/*`, `ui/utils/context/UIChoiceMenu`.
   Список исходных включений: `.aihelper/ui-clips-core.txt` и
   `.aihelper/ui-clips-client.txt`. Полный waveform требует реального аудио backend.
2. Завершить платформенные сервисы `BBSModClient`: camera controllers, sound/video,
   settings, resource lifecycle, export. `CameraController` — собственная BBS логика;
   получение player/camera/FOV, захват мыши и Forge события находятся на границе.
3. Перенести нижний 3D слой `ui/framework/elements/utils/UIModelRenderer`,
   `forms/renderers/FormRenderingContext`, `forms/FormUtilsClient`,
   `ui/utils/StencilFormFramebuffer`, `client/BBSRendering`. Сохранить алгоритмы
   исходных редакторов, заменить MC matrices/vertex consumers/FBO/lightmap на
   Forge/JOML/LWJGL 2. World render callback должен использовать настоящий Forge
   event/свой небольшой контекст, не эмуляцию Fabric `WorldRenderContext`.
4. На этом слое подключить исходные form renderers и `UIFormEditor` вместе с
   `UIPickableFormRenderer`, state editor, bone picker, gizmo и свойствами.
   `UIBaseMenu.releaseTransform` тогда получает действительное завершение жеста.
5. Перенести actor/film runtime: `film/BaseFilmController`, `FrozenFilmController`,
   `camera/controller/RunnerCameraController`, network/recorder. Подключить
   `UIClipsPanel`, `UIReplaysEditor`, `UIFilmPreview`, `UIFilmController`, undo и dock.
6. Подключить целый `ui/dashboard/UIDashboard` с исходной регистрацией панелей,
   `DashboardWarmup`, `ContentType`/repositories, onboarding и utility. F6 открывает
   UIScreen с ним. Проверить создание/открытие/сохранение фильма, актора, формы,
   проигрывание/камеру, undo, настройки и повторный вход в мир.

## Конкретные платформенные границы

- `VideoPlayer`: исходное последовательное декодирование, асинхронный seek и
  точные кадры во время экспорта сохранены. LWJGL 3 `MemoryUtil` заменён на
  LWJGL 2 `BufferUtils`, Java 9 `readAllBytes`/`Redirect.DISCARD` — Java 8 IO.
- `VideoManager.startFrame()` вызывается перед render frame, `update()` на tick,
  `delete()` при освобождении клиентских ресурсов. Не делить decoder между
  одновременно играющими владельцами одного файла.
- Настоящий `utils/VideoRecorder`: `startRecording`, `recordFrame`,
  `stopRecording`, `isRecording`. `film/VideoExportSession` и
  `ui/film/PanelVideoExportSession` управляют фиксированным временем, кадрами,
  warm-up/отменой. `AudioClientClip` использует это состояние для mute, а
  `VideoPlayer` — для точного декода; постоянный `false` не заменяет recorder.
- `api/client/events/TimelineEvents` использует собственный listener dispatch,
  сохраняет `register`/`invoker` и порядок обработчиков, не зависит от Fabric.
- Камера/FOV/HUD: референс `../bb-sources/aperture-1.12/src/main/java/mchorse/aperture/client/RenderingHandler.java`.
  Превью: `../bb-sources/mclib-1.12/src/main/java/mchorse/mclib/client/gui/framework/elements/GuiModelRenderer.java`.
  Это примеры адаптации платформы; внешний вид и логика берутся из BBS FS.

## Проверочные экраны

`OriginalUIProbe`, `OriginalTimelineProbe`, `OriginalClipsProbe` находятся только
в `src/aihelper`; это disposable данные и диагностические стенды, не пользовательские
редакторы. Clips smoke прошёл настоящие menu/key/mouse пути, изменённые данные, waveform,
маркеры, GL и screenshot. Просмотрены `run-forge1122/screenshots/ai/original-clips-edits-verified.png`
и `original-clips-marker-editor-verified.png`. Исправления сценария: перед Shift-wheel
высота дорожек увеличивается настоящим Alt+Up; native cursor проверяется перед
mouse down/up. В контрольном старом сценарии наблюдалось постороннее перемещение
курсора и контекстное меню во время trim, поэтому оно не является доказательством
ошибки исходного UIClips. Полный Dashboard этим стендом не проверяется.

## Приоритет библиотек и следующий нижний слой

В текущем вложенном поручении прямо задан порядок: **глобальные
`config/bbs/assets` → библиотека мира → встроенные**. Forge сохраняет этот порядок.
В исходном `DynamicSourcePack` и решении сентября 2026 был иной порядок чтения
совпавшего файла: мировой файл перекрывал глобальный, при глобальном корне для
создания. Это известное различие между исходником и новым поручением; старую
реализацию нельзя переносить вслепую и менять действующий приоритет.

Следующий анализ: `utils/watchdog/WatchDog` и `WatchDogProxy` — два независимых
Java-класса. `fonts/FontManager` требует настоящего TTF/OTF atlas backend,
метрик/переносов/масштаба, освобождения GL-ресурсов и invalidation по watcher.
`BBSResources` нельзя считать перенесённым, удалив font/structure listeners:
ему нужны все реальные службы и generation guard для событий старого мира.

`FormCategories` через `FormCategory.createUI` возвращает зависимость на
`UIFormCategory`/Dashboard/network. Его dry-run даёт 883 класса на текущем
снимке. Кроме UI, нужны ModelManager catalog/reload listener, ParticleManager и
полный список form types. Значение `Needed: 45` для одного BBSResources не
доказывает замкнутость пакета: анализатор не исследует отсутствующие методы
нативного BBSModClient.

## Native fonts: граница точности

`fonts/TrueTypeFontData` читает hhea/head/maxp/hmtx из TTF/OTF: масштаб
`size/(ascent-descent)`, высота и lineHeight соответствуют исходному FontManager,
advances не зависят от подсказок Java или oversampling. Bearing/ascent следуют
фактическому TrueTypeFont из локального 1.20.4 client JAR. `NativeTrueTypeFontRenderer`
использует AWT только для растеризации outline, собственные OpenGL atlas256
с nearest filtering и native 1.12 FontRenderer adapter. AWT hinting/antialias
не объявлены пиксельно совпадающими со STB; пользовательские TTF не подменяют
отдельный перенос bitmap glyphs стандартного шрифта 1.20.

FontManager сохраняет min/max size, ключ `(Link,size,oversample)`, MAX_RASTER192,
30-секундное освобождение, invalidation по Link для created/modified/deleted,
null fallback при отсутствии/повреждении файла. Listener/update/delete необходимо
вызывать на клиентском потоке, как у исходного WatchDogProxy.

## Исходный аудит default bitmap1.20 (реализован ниже)

Локальный клиент1.20.4 содержит исходные providers `font/default.json` →
`include/space` → `include/default` → `include/unifont`. Bitmap-порядок:
`nonlatin_european.png` (128×536, height8/ascent7), `accented.png`
(144×900, height12/ascent10), `ascii.png` (128×128, height8/ascent7).
Ё/ё/й находятся в accented, поэтому одного nonlatin недостаточно. По bytecode
BitmapFont: `advance = int(lastOccupiedColumnPlusOne * height / cellHeight + 0.5) + 1`;
рисуется полный cell с nearest, смещение сверху `7-ascent`. BBS wrapper
сохраняет default height7/line12. Например, «Перемещение» занимает67 единиц
в1.20 bitmap и45 в1.12 Unicode: это самостоятельная проблема паритета.

Внешний `include/unifont.json` находится в asset index `1.20.4-12`, hash
`f8d4768707b20359f2f7660346bd3a84b6ee27b1`; одноимённый JSON внутри JAR пустой.
`unifont.zip` hash `109663114d0099c48a703626c8462e07d802e08b` содержит
`unifont_all_no_pua-15.1.04.hex` и LICENSE.txt. Его glyph raster16px/2,
advance `floor(width/2)+1`, bold/shadow offset0.5, плюс JSON size_overrides.

Аудит реализован в native reader ниже. Доставка выбрана через три проверяемых
официальных файла в собственном cache; установленная 1.20.4 или Loom не нужны.
Пользовательский TTF остаётся отдельным источником glyphs.

## Native film foundation — 2026-10-04

Новые Base/World/FirstPerson/Films/Recorder, FilmControllerContext/Target,
WorldRenderContext и полноценный FilmEntityRenderer на диске; Frozen/FilmMatrices/
ThirdPersonItemUse берутся из originals (.aihelper/film-client.txt). Сохранены
replay clock/anchors/frozen/actor ownership/shadow-follow/gizmo modes и mob recording.
Form агент даёт ModelInstance/MatrixCache; root GPU/actors/morph. Координирующий агент подтвердил общий build2/build3 и native film backend QA.
Dashboard/фильм редактор этим прогоном ещё не проверены.

Один view-space: WorldRenderContext снимает реальные GL matrices, Draw не должен
добавлять view второй раз. Packed overlay default10<<16, hurt3<<16.
NativeItemUseScope возвращает exact active stack/count/HAND_STATES после кадра.
Root lifecycle: endFrame до START, Films.startRenderFrame, beginFrame, рисование,
END endFrame; ClientTick.START также endFrame. ItemUsePose.source=ThirdPersonItemUse.

Native ClientNetwork/ServerNetwork, FilmNetwork: IDs3 C2S /4 S2C, разные классы,
реальная фрагментация24k/reassembly32MiB, 4 pending/60s, dispatch game-thread.
FilmClientBridge разрывает UI-цикл; будущий UIFilmPanel bind supplier/receiveActions.
Запись применяется к отслеживаемому Film или хранится до consumer. Локальный
fallback BBSModClient.getLocalFilms = config/bbs/data/films; сервер BBSMod.getFilms
= world/bbs/films, это не папки ассетов.

ActionManager/Player/Recorder/DamageControl сохраняют экипировку/форму и original
seek/restart/stop restore. Capture привязан к конкретному WorldServer.
Coremod mchorse.bbs_mod.forge.core.BBSLoadingPlugin → BBSWorldTransformer:
World.setBlockState HEAD получает oldstate+tileNBT до замены. Root должен включить
FMLCorePlugin manifest и dev -Dfml.coreMods.load. FilmServerEvents.register()
регистрирует packets/Forge events/actiontypes; serverStarting(server) gamerule
bbsEditing=true как исходник; serverStopping() reset. Root добавляет getActions.

Не завершено: exact capture block-break progress и use-on-block требуют nativehooks;
новый UIFilmPanel переносится параллельно и пока не проверен в игре.
RuntimeQA сети/actor/morph/DC выполнен координирующим агентом.
Vanilla1.12 не имеет trident/crossbow; loaded riptide clip сообщает unsupported,
а не имитирует реализацию. Native block-state codec сохраняет NBT Name/Properties.
Стандартный bitmap font1.20 перенесён и прошёл root smoke. TTF smoke root PASS,
watcher пока не проверен. Никаких commit/push.


## Default font 1.20.4 — native source checkpoint (2026-10-04)

`fonts/nativefonts` reads the original `reference`, `space`, `bitmap` and `unihex`
providers. Provider order, PNG pixels, full-cell quads, advances, accented Cyrillic
bearing (-3), missing glyph, Unihex crop/size overrides and half-pixel bold/shadow
are carried across. `Batcher2D` selects this renderer; existing user TTF/OTF service
is retained. Wrapper metrics remain height 7 / line 12. Native 1.12 ICU supplies
bidirectional shaping; exact cross-version RTL raster/layout parity is not yet proven.

Delivery does not depend on a 1.20.4 installation or Loom. A daemon worker downloads
three immutable official files once into `config/bbs/runtime/fonts-1.20.4`, verifies
size + SHA-1, then atomically replaces each cache entry. Every startup revalidates the
cache, and successful caches work offline. No downloaded Java classes are executed.
Pinned source metadata was read from the official 1.20.4 version manifest and asset
index 12 on 2026-10-04:

| File | SHA-1 | Bytes |
| --- | --- | ---: |
| Client archive (PNG + font JSON only are read) | fd19469fed4a4b4c15b2d5133985f0e3e7816a8a | 24445539 |
| `minecraft/font/include/unifont.json` | f8d4768707b20359f2f7660346bd3a84b6ee27b1 | 1879 |
| `minecraft/font/unifont.zip` | 109663114d0099c48a703626c8462e07d802e08b | 1615995 |

The mod JAR contains loader code, not these third-party binary assets. This is a
technical delivery choice, not a claim that redistribution is prohibited. The cached
Unihex ZIP retains its upstream LICENSE.txt. Temporary 1.12 text has a visible
“Подготовка шрифта…” banner; errors show the cause and a clickable retry. Network,
checksums and provider parsing stay off the render thread. Resource reload disposes
GL atlases; switching from temporary metrics triggers BBS screen layout again.

Seven CPU tests cover fractional formatting, real provider metrics when the verified
cache fixture is available, corrupted/same-size cache, first-run offline failure,
offline reuse, wrong/truncated/oversized downloads and temporary-file cleanup.
`OriginalDefaultFontProbe` and `tools/forge1122/default_font_smoke.py` cover the real
Batcher2D path, Cyrillic width 67, bold width 78, Unihex bold width 19, accented baseline,
atlas disposal/reload and a screenshot fixture. **Root reported build3 compile/test/helper
and default_font_smoke PASS, inspected the screenshot, and confirmed GL0.**
This validates the default-font checkpoint, not the new Dashboard sources.

## Dashboard — первый подтверждённый runtime checkpoint 2026-10-04

134 исходных/нативных файла перечислены в `.aihelper/dashboard-ui-owned.txt`;
включения originals — `.aihelper/dashboard-ui-core.txt` и `dashboard-ui-client.txt`.
Число 931 выше относится к начальному графу, не к текущему числу отсутствующих функций.
Координирующий агент сообщил build6 compileJava/test (33)/aihelperClasses PASS.
На build7 после исправления disconnect deadlock пройден настоящий F6 Dashboard:
settings, создание/duplicate/rename/delete/reopen фильма, чтение сохранённого `.dat`
и сохранение изменённого clip title. `build/reports/forge1122-dashboard-smoke.json`
подтверждает UIDashboard, реальные панели, CRUD и GL0; скриншоты сохранены сценарием.
Это проверка перечисленных действий, не заявление полного паритета всех редакторов.

| Исходная панель (порядок) | Текущее состояние |
| --- | --- |
| Morphing (0) | Настоящая панель отображена, demorph проверен; остальные form editing сценарии ещё не подтверждены |
| Film (1) | Настоящая панель открыта через F6; CRUD/reopen .dat и изменение clip title с сохранением PASS. Экспорт и расширенные действия проверяются отдельно |
| Model blocks (2) | Замыкание исходной панели ещё требуется |
| Particles (3) | Backend/editor ещё не замкнуты; фиктивной панели нет |
| Model editor (4) | Включён и зарегистрирован, редактор в игре ещё не проверен |
| Textures (5) | Настоящая вкладка открыта в Dashboard smoke; paint/layers/frames/undo/save ещё не проверены в этом сценарии |
| Audio (6) | Настоящая вкладка открыта в Dashboard smoke; полный editor/import/liked sounds workflow ещё не проверен |
| Settings, utility, welcome | Настройки открыты кнопкой и hotkey, кириллический поиск проверен. Utility, системные диалоги и CDN ещё не проверены в этом прогоне |
| Selectors pinned action | Требуется настоящий selectors backend/UI; пустой кнопки нет |

`DashboardPanelRegistry` убирает только циклические импорты: хранит реальные фабрики,
исходный порядок, callbacks layout/export и pinned actions. `ContentType` сохраняет
IDs и CRUD API, выбирает текущий server/world FilmManager при каждом запросе;
локальные фильмы лежат в config/bbs/data/films. Particle repository связывается
только после появления реального менеджера.

`BBSResources` сохраняет глобальный и мировой watcher, generation guard, callbacks
texture/model/sound/font/FormCategories, reload/GL disposal/world swap и assetsVersion.
Native CDN transport сохраняет protocol GET files / POST file, upload, delete;
работает на worker, scheduled UI callbacks. URL и player skin источники реальные.
Системный Windows folder picker сохранён через JNA COM, file picker использует
native AWT вместо отсутствующего tinyfd/LWJGL3. В utility остаётся исходное сообщение
о недоступном Iris; кнопки управления несуществующим провайдером не создаются.

Batcher2D custom shader quads используют настоящий native VAO/VBO pipeline:
selection marching ants и multilink erase/pixelate не заменены обычной картинкой.
Общая компиляция, открытие Dashboard и базовые settings/CRUD/panel switching
подтверждены выше. Полные texture/audio/model-editor и оставшиеся Film UI сценарии
ещё требуют реальной проверки; первые успешные действия не закрывают весь /goal.

## Обязательный оставшийся ресурсный lifecycle (2026-10-04)

После первого рабочего Dashboard/Film нужно включить исходные `importers/**`,
вызвать Importers.setup и RegisterImportersEvent, перенести исходный сценарий
UIScreen.filesDragged и настоящий native file-drop bridge для LWJGL2. Один setup
не обеспечивает перетаскивание файлов; Win32 hook пока не начат. Fake callback/
фиктивный импорт недопустимы. Нужны также addon lifecycle events вокруг реальных
реестров (settings widgets, keybinds, language, importers), с исходным порядком.
Вне этого checkpoint остаётся настоящий CemSourcePack lifecycle; строка категории
`cem` сама по себе не означает поддержку CEM pack.

Build2 выявил доступ к JDK FILE_TREE через non-standard import; native WatchDog
получает настоящий Modifier reflection, сохраняя один рекурсивный Windows handle.
Переподключение на отдельные блокирующие handles дочерних папок не допускается.
