Полностью реализуй Spline IK в BBS Mod / BBS FS. Меня зовут Вемпи. Общайся по-русски, просто и по делу. Это поручение на реализацию, интеграцию и реальные проверки, а не на очередное исследование или прототип без законченного пользовательского сценария.

Ниже — контекст предыдущей сессии, которая изучила проект, но НЕ реализовывала Spline IK. Используй его как карту кода и исходное предложение. Проверяй фактическую ветку и код: если найдёшь более подходящий способ реализации, используй его и кратко объясни причину. Названия новых сущностей и предложенная схема данных не являются обязательным контрактом. Обязательны результат, совместимость, удобство, корректная анимация и проверки.

1. Рабочее место, исходное состояние и границы

Репозиторий: I:/Projects/IT/Java/bbs-mod-wemppy/bbs-fs.
Подготовлена локальная ветка spline-ik от dev, исходный HEAD 480176851 — fix(assets): вернуть глобальную библиотеку как основную. Снимок исследования: 2026-09-27. Minecraft 1.20.4, Fabric, BBS 2.7, Java target 17. Значения проверены по gradle.properties и build.gradle. Перепроверь их перед работой. Предыдущая сессия добавила этот документ и локальную память; функциональный код и модель «Змея» не менялись.

Прочитай AGENTS.md, .memory/MEMORY.md, working-rules.md, current-work.md, затем spline-ik-research-20260927.md и только связанные актуальные записи. В старых записях бывают планы, уже заменённые реализацией. В частности, старое «IK redesign — код не начат» не описывает нынешний код. Старое «без тестов» относилось к другой задаче с таймлайном: здесь тестирование прямо обязательно. Не воскрешай закрытые задачи и отвергнутые идеи системы произвольных пользовательских контроллеров рига. Управляемые точки сплайна нужны в рамках этой задачи.

В начале проверь git status, текущую ветку, HEAD и наличие живого клиента. Сохраняй чужие изменения. Работай в этой ветке; переносы на другие версии Minecraft, слияния и публикация не входят в поручение. Не трогай temp_change_log.md без моей просьбы. .memory и AGENTS.md локальные и исключены из Git; не добавляй их принудительно.

Веди короткую актуальную память по решениям и результатам проверок. Выполняй этапы последовательно и продолжай до готового результата; не требуй моего подтверждения после каждой технической детали. Вопросы задавай только по действительно необходимым пользовательским решениям, которые нельзя принять из контекста и проверить в проекте.

2. Что должна уметь система

Spline IK управляет последовательной цепочкой костей через пространственную кривую. Пользователь двигает небольшое число control points, цепочка следует форме кривой, геометрия следует костям. Основные сценарии: змея, хвост, щупальце, позвоночник, провод. Это управление позой; новая физическая симуляция и столкновения сами по себе не требуются.

Концептуальный ориентир — Blender Spline IK: цепочка, целевая кривая, влияние, правила распределения длины и масштаба. В Blender есть Chain Length, Even Divisions, Chain Offset, Fit Curve/Bone Original и поперечные режимы масштаба. Не переноси автоматически его порядок constraints, ось Y, все переключатели или организацию объектов. Источник: [Blender Manual — Spline IK](https://docs.blender.org/manual/en/3.4/animation/constraints/tracking/spline_ik.html). Это справка по концепции, не обещание совпадения всех опций Blender.

Обязательный законченный сценарий: выбрать цепочку, создать подходящий сплайн одной командой или назначить существующий сплайн этой формы, двигать его точки прямо во viewport, анимировать их и влияние/twist, сохранять и снова загружать сцену без изменения результата. Предусмотри несколько независимых Spline IK цепочек в одной форме и независимые настройки нескольких актёров с одной моделью.

Нужны понятные режимы сохранения длины и подгонки под кривую, устойчивое управление twist, плавное влияние 0..1, безопасное отключение, работа обычных поз и анимаций под solver. Пользователь не должен вручную создавать по вспомогательной кости на каждую точку или править JSON для базового сценария.

Не превращай задачу в общий редактор кривых, новый scene graph, node editor или новую систему анимации. Замкнутые кривые, NURBS, универсальные пространственные связи, физика сплайна, сложные профили толщины и произвольное число типов интерполяции не обязательны. Добавляй продвинутые возможности только там, где они решают конкретный сценарий и укладываются в существующую архитектуру.

3. Карта существующей архитектуры

В следующих путях используются два сокращения:
M = I:/Projects/IT/Java/bbs-mod-wemppy/bbs-fs/src/main/java/mchorse/bbs_mod
C = I:/Projects/IT/Java/bbs-mod-wemppy/bbs-fs/src/client/java/mchorse/bbs_mod
Это реальные корни исходников; путь M/cubic/IModel.java означает соответствующий файл внутри M. Указанные существующие классы проверены по исходникам при исследовании. Новые классы, предлагаемые дальше, пока отсутствуют.

Кости, модель, поза:

- M/cubic/IModel.java и M/cubic/IBoneHierarchy.java — общая модель и иерархия: getBone, getRigBones, parent/children, resetPose, applyPose, snapshotChannels, restoreChannels. Используй общий контракт вместо дублирования solver для каждого формата.
- M/cubic/RigBone.java — evaluatedRotation, orient, offset, доступ к каналам и единицам вращения. Возвращаемый getBoneTransform — живой объект. Между описанием getRestTranslation в интерфейсе и конкретными реализациями есть нюанс: нельзя без проверки считать его универсальной локальной длиной кости.
- M/cubic/data/model/Model.java и ModelGroup.java — cubic-скелет; initial/current Transform, родитель/дети, cubes/meshes. ModelGroup.initial.translate — АБСОЛЮТНЫЙ пивот в пиксельном пространстве модели, а не локальный offset от родителя. Семантику подтверждает реальный рендер.
- M/bobj/BOBJBone.java и C/cubic/model/bobj/BOBJModel.java — BOBJ-скелет, bind/inverse-bind/relative matrices, skinning. Не считай его математически идентичным cubic только из-за общего интерфейса.
- M/utils/pose/Transform.java, PoseTransform.java, Pose.java — трансформы, Euler/quaternion, поза как карта bone name → PoseTransform. PoseTransform добавляет fix, видимость, цвет и другие свойства. Значения PoseTransform.rotate используются в радианах; cubic current.rotate хранит градусы, BOBJ — радианы. Пользуйся RigBone и существующими Matrices-конверсиями.
- M/cubic/animation/IAnimator.java, Animator.java, ProceduralAnimator.java; M/cubic/CubicModelAnimator.java; C/cubic/model/bobj/BOBJModelAnimator.java — существующие анимации и действия. M/cubic/data/animation/ содержит их данные.
- M/cubic/CubicLoader.java, C/cubic/model/loaders/CubicModelLoader.java, C/cubic/model/ModelManager.java, C/cubic/model/config/ModelConfig.java — загрузка геометрии/анимаций, кэш модели, config.json. Не переноси автоматически настройки нового constraint в геометрию model.bbs.json.

Данные формы и настройки существующих solver:

- M/forms/forms/ModelForm.java — model, pose, poseOverlay, actions, bones, wind и transient overrides целей. Форму можно менять и анимировать независимо от других форм той же модели.
- M/forms/forms/utils/FormBone.java — per-bone настройки IK, joint freedom, physics, constraints. Обычная IK-цепочка задана на tip bone: ik_target, ik_pole_target, ik_chain_length, режимы, compound-параметр ik. Структурные адреса и режимы отделены от анимируемых чисел.
- M/forms/forms/utils/ValueBones.java — ленивое создание записей, сохранение только ненейтральных костей, сохранение данных отсутствующей в текущей модели кости, глубокое копирование динамического набора. Модель сама анимируема, поэтому отсутствие кости на данном кадре не повод удалять настройку.
- M/cubic/ik/IKControl.java, IKControls.java, BoneIKIO.java; M/cubic/chains/ChainControl.java, ChainControls.java; M/settings/values/core/ValueBoneIK.java — значения параметров и обменный формат пресетов. BoneIKIO читает старый плоский формат и wrapped chains/bones, сохраняет classic-семантику старых ригов.
- M/settings/values/base/BaseValue.java, BaseValueBasic.java, BaseKeyframeFactoryValue.java; M/settings/values/core/ValueGroup.java, ValueStableList.java, StableIds.java — уведомления, Undo, static/runtime значения, фабрики ключей и постоянные ID. BaseValueBasic.get() возвращает runtimeValue при его наличии; toData/copy должны сохранять авторские значения, а не временный solve.

Существующие вычисления IK и ограничений:

- C/cubic/ik/ModelIKRuntime.java — apply, получение цепочек/контроллеров, проверки и isRotationConstrained для UI.
- C/cubic/ik/ModelIKCache.java — компиляция структуры tip→ancestors, проверка ссылок/циклической цели, один memo-слот по model/form/RenderFrame epoch. Это не универсальный долгоживущий кэш топологии.
- C/cubic/ik/ModelIKApplier.java — сортировка/объединение перекрывающихся обычных IK-цепочек, преобразование целей, вывод orient и stretch offsets.
- C/cubic/ik/solver/IKTree.java, IKJoint.java, IKTreeSolver.java — существующий IK solver; C/cubic/ik/ClassicLimbSolver.java и IKRig.java — классический путь и различия моделей. Spline IK не обязан превращаться в расширение этого численного IK-дерева: специализированный расчёт вдоль кривой может быть проще.
- C/cubic/render/ModelPivotFrames.java и CubicRenderer.java — PivotFrame(position, parentRotation, worldRotation, scale), получение текущих координат пивотов.
- C/cubic/render/ModelRotationBlender.java — пример преобразования решённых сегментов в локальные orient и смешивания с исходной позой. Не используй без проверки: он не решает автоматически все вопросы spline twist, bind roll, произвольных осей и масштаба.
- C/cubic/render/ICubicRenderer.java — действительные правила translate/pivot/rotation/scale. Cubic translate по X имеет смену знака, пиксели делятся на 16. Рендер формы содержит поворот на 180° по Y. Не подбирай знаки экспериментальными костылями.
- C/cubic/constraints/ModelConstraintsRuntime.java — финальные ограничения evaluated rotation; C/cubic/physics/ModelPhysicsRuntime.java и ModelPhysicsCache.java — физика поверх позы, принадлежащие ей цепочки.

Animation/render pipeline:

- C/forms/renderers/ModelFormRenderer.java — основная точка интеграции. evaluateChannels: resetPose → animator.applyActions → default pose модели → составная поза формы. Затем renderModel вызывает applyIK → applyPhysics → applyConstraints. applyPhysics также вызывает FormPoseEvents.MODEL_POSE до встроенной физики.
- getPose() в том же классе собирает pose и overlays. Не подменяй его позой результата Spline IK.
- evaluateChannels использует snapshotChannels/restoreChannels, чтобы повторные render passes начинали constraint stack с одной исходной позы. Это предотвращает накопление результата между проходами.
- collectMatrices в ModelFormRenderer имеет отдельный путь: evaluateChannels → ModelIKRuntime.apply → FormPoseEvents.MODEL_POSE(Pass.MATRICES) → captureMatrices. Он не равен всему render stack: встроенные physics/limits здесь не повторяются в той же последовательности. Не утверждай, что одна вставка в renderModel покрыла всю интеграцию.
- public evaluateChannels(entity, transition) и solveIK(model, entityWorld, transition) используются внешними сэмплерами и запеканием. Проследи все такие вызовы.
- C/cubic/ModelInstance.java — один глобально кэшируемый ИЗМЕНЯЕМЫЙ asset на model id. Несколько форм по очереди перезаписывают его скелет. Здесь хранится channels-token с form/entity/time/frame/poseVersion. Нельзя держать в общей модели один runtime-сплайн или решённую позу без правильного контекста владельца.
- C/forms/renderers/utils/RenderFrame.java, MatrixCache.java; M/forms/forms/Form.java — кадр, матрицы и poseVersion. setRuntimeValue само по себе не вызывает postNotify, поэтому нельзя считать static poseVersion единственным признаком изменения анимированных точек.
- C/api/client/events/FormPoseEvents.java — существующие контракты аддонов: Pass.RENDER/MATRICES, MODEL_POSE, PARENT_FRAME, CLAIM_CHAIN, PIVOT_OFFSETS. В callbacks запрещено продвигать симуляцию при каждом render pass и изменять сохранённую анимацию. Сохрани смысл «после animation/IK» для потребителей; если добавляешь spline к этапу IK, учитывай это.
- C/film/FilmMatrices.java, BaseFilmController.java, FilmControllerContext.java — матрицы фильма и применение tracks. BaseFilmController очищает transient overrides и применяет FormProperties через TrackContext.frame.
- C/film/IKBake.java — запекание обычного IK на scratch entities с отдельной выборкой каналов, solveIK, преобразованием результата в PoseTransform и отключением chains ключами. Сейчас вход ограничен root ModelForm и обычными IK chains. Это образец, а не готовая поддержка spline или всех nested forms.

Дорожки и анимационные состояния:

- M/film/replays/FormProperties.java — центральное хранилище tracks, создание, применение/reset и сериализация; bone tracks применяются после остальных. Современный формат — tracks[] с kind/form/subject/prop/channel, старые адреса продолжают читаться. Неизвестные tracks/factories сохраняются как foreign data.
- M/film/replays/tracks/TrackId.java, TrackKind.java, TrackBehaviours.java, TrackContext.java, TrackBlend.java, TrackDescriptor.java — адрес, диспетчеризация, контекст, смешивание и описание дорожки. TrackId учитывает stable ID body parts и legacy indices; адреса должны переживать переименование/перестановку.
- M/film/replays/tracks/behaviours/PropertyTrack.java, BoneTrack.java, ControlsTrack.java, TargetTrack.java, BoneConstraintTrack.java — образцы применения runtime-значений и сброса.
- ВАЖНО: ControlsTrack.apply прекращается при !context.solvers(). TrackContext.of(form), используемый animation states, выставляет solvers=false. Простое копирование IK_CONTROLS создаст дорожку, которая видна, но не исполняется в состояниях. Новым spline-данным нужна корректная общая модель применения/reset/blend для фильма и states.
- M/forms/FormUtils.java:getProperty обходит пути body parts и прямые basic properties. Он НЕ является универсальным резолвером произвольного вложенного ValueGroup. Спрятать точки в form.splines и ожидать, что обычный PropertyTrack сам их найдёт, недостаточно.
- C/film/replays/tracks/TrackCatalog.java — один каталог для фильма и states. Здесь создаются descriptors, seed values, вложенность и доступные дорожки. C/film/replays/tracks/TrackStyle.java и C/api/client/editor/TrackCategories.java — отображение и категории.
- M/utils/keyframes/Keyframe.java, KeyframeChannel.java, KeyframeSegment.java; M/utils/keyframes/factories/IKeyframeFactory.java, KeyframeFactories.java, TransformKeyframeFactory.java, ChainKeyframeFactory.java, IKKeyframeFactory.java — интерполяция, глубокие копии, factory registration, чтение ключей.
- M/forms/states/AnimationState.java, AnimationStates.java, StatePlayer.java — состояния, blending и resetProperties при освобождении параметров.
- C/ui/film/replays/UIReplaysEditor.java, UIReplaysEditorUtils.java; C/ui/forms/editors/states/keyframes/UIAnimationStateEditor.java, UIAnimationStateKeyframes.java — оба редактора. Общие buildSheets, поиск/выбор track, часть formPath, автоключи.
- C/ui/framework/elements/input/keyframes/factories/UIKeyframeFactory.java — регистрация редакторов factory; UIIKKeyframeFactory.java, UIChainKeyframeFactory.java, UITransformKeyframeFactory.java — примеры. getEditableTransform в UIReplaysEditorUtils распознаёт конкретные типы редакторов; новый редактор сам собой не получит gizmo.
- C/ui/film/controller/FilmKeyframeInsertion.java — вставка ключей в зависимости от текущей цели/категории; общая вставка actor keys не является автоматическим способом ключевать spline points.

Редактор и viewport:

- C/ui/forms/editors/forms/UIModelForm.java — регистрация панелей модели и блокировка вращения IK-костей.
- C/ui/forms/editors/panels/UIModelIKFormPanel.java и UIBoneListFormPanel.java — searchable bone tree, UIBonePicker с пипеткой, пресеты, copy→edit→set и обновление полей. Продвинутые секции уже умеют сворачиваться.
- C/ui/utils/bones/UIBonePicker.java, UIBonePickerContextMenu.java, UIBoneTreeList.java; C/ui/utils/BoneSelection.java — существующий язык выбора костей.
- C/api/client/editor/FormEditorTool.java — панель может предоставить getGizmoTransform и getGizmoOrigin. Это полезная точка для spline point editor; пока охватывает панель обычной формы, state editing имеет приоритет, а film/state picking нужно связать отдельно.
- C/ui/forms/editors/UIFormEditor.java, C/ui/forms/editors/forms/UIForm.java и C/ui/forms/editors/utils/UIFormRenderer.java — входы drag, выбор, вычисление origin, preview.
- C/ui/framework/elements/input/UIPropTransform.java; C/ui/framework/elements/input/drag/TransformGesture.java, TransformSpace.java — общий жест, числовой ввод, локальное/родительское/global пространство, отмена и callbacks.
- C/ui/utils/Gizmo.java, GizmoDrag.java, GizmoInteraction.java, GizmoViewport.java, GizmoJacobian.java — общая механика viewport, матрицы camera/viewport, ray dragging, экранный размер, priority picking. Переиспользуй её, не создавай другую систему горячих клавиш.
- C/film/FilmTarget.java, C/ui/film/controller/UIFilmController.java, FilmEditorController.java, FilmStencilPicker.java; C/api/client/events/FilmGizmoEvents.java — единая активная цель и одинаковое размещение gizmo в визуальном и picking проходах. Нынешние FilmTarget.Kind — NONE/ROOT/ANCHOR/BONE; не маскируй spline point под несуществующую bone name.
- C/ui/utils/StencilFormFramebuffer.java и C/ui/framework/elements/utils/StencilMap.java — существующий picking. Выбор точек нужно адресовать явно, без коллизий с handle IDs и костями.
- C/cubic/ik/ModelIKDebug.java и C/cubic/render/DebugOverlay.java — визуальный язык линий/маркеров и пример синхронизации renderStencil с видимыми маркерами. Редактирование spline должно быть доступно независимо от глобального debug toggle.
- C/ui/forms/editors/UIFormUndoHandler.java, C/ui/film/utils/UIFilmUndoHandler.java, C/ui/film/utils/undo/ValueChangeUndo.java — история через value notifications, группировка и восстановление UI.
- C/ui/UIKeys.java; I:/Projects/IT/Java/bbs-mod-wemppy/bbs-fs/src/client/resources/assets/bbs/assets/strings/en_us.json и ru_ru.json — основной BBS UI и локализация. Не путай с Minecraft lang в src/main/resources. Проверь принятую в ветке обработку остальных локализаций.

Смежные функции не заменяют Spline IK. C/ui/film/controller/MotionPath.java визуализирует траекторию уже вычисленной анимации; M/camera/clips/overwrite/PathClip.java задаёт движение камеры; M/utils/keyframes/BezierUtils.java и M/utils/interps/AutoBezier.java решают интерполяцию значений по времени. Наличие слова spline/Bezier не означает готовый пространственный solver с таблицей длины дуги.

4. Рекомендуемое устройство данных и владение

Сначала зафиксируй короткий технический дизайн: где живёт структура, где авторские точки, как они анимируются, в каком пространстве вычисляются и когда solver применяется. Затем реализуй. Не трать весь заход на документирование.

Разумный исходный вариант — настройки цепочки на ModelForm/FormBone и принадлежащие форме кривые с постоянными ID. Новая цепочка автоматически получает свою кривую; существующую кривую этой формы можно назначить из списка. UI не должен требовать предварительного ручного создания отдельного объекта сплайна. Общая библиотека мировых spline-объектов для первого законченного решения не нужна.

Раздели:

- Структуру: ID цепочки/кривой, имя для отображения, root/tip или однозначный способ построить путь по hierarchy, ordered IDs точек, режим длины, опорное пространство, параметры bind/rest.
- Анимируемую позу: положение каждой точки, её roll при выбранной модели twist, влияние и другие реально нужные численные параметры.
- Runtime: скомпилированную цепочку, rest lengths/directions/roll corrections, samples, таблицу длины дуги, рассчитанные frames/offsets/scale. Runtime не сериализуется.

Рассмотри небольшие специализированные классы наподобие SplineIKDefinition, SplinePoint/SplinePose, SplineEvaluator, SplineIKSolver и ModelSplineIKRuntime. Это пример разделения ответственности, не требование создать каждый класс. Чистая математика без Minecraft UI/OpenGL особенно полезна для численных проверок.

Постоянные ID точек обязательны. Индекс в списке определяет порядок вдоль кривой, но не идентичность анимационной дорожки. Переименование, вставка и удаление соседней точки не переназначают уже созданные ключи. ValueStableList/StableIds могут дать готовую основу. Copy/duplicate кривой получает новые ID с согласованным remap внутренних ссылок; копия формы должна оставаться независимой.

Не дублируй состояние в трёх несвязанных объектах «панель/дорожка/solver». Авторские данные должны иметь один источник, runtime overrides — отдельный понятный жизненный цикл. Для изменения авторских compound values — deep copy и set/notifications. Интерполируемые вектора/карты не должны делиться между независимыми ключами или актёрами.

При анимации фиксируй топологию вне времени: ключуются положения существующих точек, а не количество/порядок точек на каждом кадре. Вставка новой точки в уже анимированную кривую должна иметь определённую семантику, сохранять чужие ключи и Undo. Если сохраняешь форму анимации при вставке, вычисли значения новой точки на нужных ключевых временах; если точная форма не сохраняется, явно обозначь это действие и не выдавай его за shape-preserving subdivide. Удаление точки и её keys — единая отменяемая операция; повреждённые/осиротевшие данные не переназначать молча.

5. Solver: математика, пространство и устойчивость

Кривая. Для базового режима подходят интерполирующий cubic spline с автоматическими касательными или cubic Bézier с auto handles. Выбери по удобству и качеству сильных изгибов, обоснуй. Centripetal Catmull–Rom — кандидат для режима «двигаю точки, кривая проходит через них», а не обязательная формула. Две точки дают предсказуемую прямую; совпадающие точки не вызывают деление на ноль. Не вводи manual handles, если базовая задача решается auto-режимом; ручные касательные можно добавить как компактное продвинутое управление при реальной необходимости.

Параметризация. Строй положение/касательную кривой и таблицу накопленной длины. Кости распределяются с учётом исходных пропорций сегментов, а не равными шагами параметра t. Требуется ограниченная стоимость sampling/refinement. Число samples не должно жёстко равняться числу костей: короткая цепочка тоже может следовать сильно изогнутой кривой.

Геометрия цепочки. Получай единственный путь root→tip через родителей; не захватывай соседние ветви. У костей BBS нет гарантированной Blender-оси Y и явно заданного bone length. Нужны точки суставов, rest directions, bind orientations и правило конечного сегмента. N костей с N пивотами дают только N−1 известных интервалов. Последнюю кость нужно ориентировать осмысленно: tangent/virtual tip с понятной автоматически выбранной длиной и возможностью уточнения при необходимости. Не требуй обязательного изменения пользовательской модели ради пустой end bone.

Rest/bind. Не измеряй длину от результата прошлого solve. Зафиксируй, какая поза считается опорной, как учитываются rest rotations, pose translation и scale, и когда выполняется явный rebind/reset. Создание кривой по текущей позе должно предсказуемо совпадать с видимой цепочкой в пределах выбранной аппроксимации. На прямой «Змее» не должно быть скачка. Повторное открытие UI не выполняет скрытый rebind.

Пространство. Рекомендуемый базовый режим — кривая в пространстве родителя корневой кости до собственного spline solve; для верхнего корня — пространство модели. Тогда перенос формы и анимация родителя двигают кривую вместе с моделью. Данные модели, transform формы, body-part anchor, actor world transform, preview camera и экранные координаты — разные пространства. Определи единицы хранения и используй явные преобразования. Мировой режим/привязка control point к чужой кости — дополнительные возможности, а не причина строить общую dependency graph заранее.

Корень. Явно выбери, следует ли root первой точке или сохраняет исходное положение со смещением кривой. Для режима полного укладывания на сплайн естественно следование первой точке; для прикреплённого хвоста полезно закреплённое начало. Не показывай оба режима без понятной разницы. Любое смещение root записывается в evaluated результат и смешивается по влиянию, не меняя сохранённый FK transform.

Длина и stretch. Обязательны два понятных поведения: «Сохранять длину» и «Подогнать под кривую». Укажи, считается ли исходная длина с authored scale; по умолчанию уважай настроенный масштаб. В режиме сохранения длины фактические расстояния суставов не должны сжиматься просто потому, что ты взял равные шаги по длине дуги: длина дуги и хорда между суставами различны. Выбери согласованный алгоритм, например последовательный поиск следующего пересечения кривой со сферой длины сегмента с ограниченным refinement, либо другой проверяемый способ. Для очень короткой/сложенной кривой определи остаток цепочки: конечное продолжение по касательной или явная ограниченность решения. Не допускай скачка на другую ветвь самопересечения.

Fit mode должен действительно давать заявленное размещение цепочки. Изменение расстояния пивотов, масштаб геометрии и сохранение объёма — разные операции. Нельзя назвать «растяжением геометрии» простой сдвиг суставов с дырками. В текущем BBS cubic offset сдвигает потомков; существующий BOBJ offset сдвигает skinning matrix и оставляет nominal bone matrices на прежних местах. Учитывай эти различия: использование старого offset само по себе не обеспечивает совпадения отображения, anchors и gizmo в новом режиме.

Если нужен отдельный evaluated scale/transform, введи его минимально и проведи через весь lifecycle: reset, snapshot/restore, render, matrix capture, skinning, необходимые geometry-cache signatures и bake. Не записывай его в авторские current.scale/pose channels во время render. Не применяй scale вдоль условной Y к произвольно ориентированной кости и не умножай масштаб всех потомков повторно. Поперечная компенсация/сохранение объёма — полезная продвинутая опция, если она получается корректной и не раздувает систему; по умолчанию сохраняй толщину. Отдельный новый mesh-deformer не требуется.

Ориентация. Рассчитывай устойчивый moving frame вдоль кривой. Parallel transport / rotation-minimizing frame — разумная отправная точка. Frenet frame ломается на прямых и точках перегиба; постоянный world-up тоже может внезапно переворачиваться. Начальный normal выбирай из bind/rest frame или явно заданного up/reference с устойчивым fallback. Учитывай roll исходных костей и преобразуй результат из пространства кривой в локальное пространство каждого родителя.

Twist. Нужен простой способ скручивания цепочки: например, roll в control points с плавным распределением по длине, общий offset и удобный конец/начало. Не делай несколько одновременно действующих неизвестно как параметров «twist/roll/rotation/up». Пользователь должен видеть направление вращения. Скаляры twist должны сохранять обороты 360°/720°; quaternion shortest-path не должен уничтожать заданный полный оборот. Продольный twist влияет на ориентацию, а не на положение осевой линии.

Influence. 0 даёт точное исходное поведение, 1 — spline result, промежуточные значения смешивают вычисленную позу с evaluated входом данного этапа. Смешивай rotations корректно, а translation/scale — согласованно. Выключение после нескольких кадров сразу восстанавливает исходный FK без остаточных offsets/scale. Не требуй, чтобы при influence=0.5 вся цепочка математически лежала на кривой: это смешанная поза, что должно быть понятно.

Детерминизм. При одинаковых входах результат одинаков независимо от предыдущего просмотренного кадра, направления scrubbing и количества render passes. Пространственный transport frame можно рассчитывать вдоль кривой в одном solve; это не повод зависеть от состояния прошлого кадра. Нулевые/почти нулевые касательные, 180° разворот, cusp, self-intersection, zero length, NaN/Infinity, singular transforms и слишком большие значения должны завершаться ограниченным безопасным результатом/понятным отказом, а не зависанием или заражением всех matrices.

6. Порядок вычислений и совместимость

Начальный кандидат на порядок: channels → существующий IK → Spline IK → существующее событие MODEL_POSE/physics → финальные limits → render. Выбери окончательный порядок по коду, связанным цепочкам и пользовательскому поведению. Объясни приоритеты. Не переставляй старые этапы ради новой функции без необходимости.

Для новой системы введи один общий вычислительный вход, который используют render, collectMatrices, сэмплеры и bake. В отдельных проходах могут быть разные доступные данные; не запрашивай рекурсивно финальные матрицы того же solver, чтобы найти его собственные control points. Опорные входы должны считываться до его изменений.

Явно разберись с перекрытиями: две spline-цепочки на одной кости, обычный IK и spline на одних костях, parent chain → child chain, physics и limits поверх spline. Для первой версии допустимо запрещать неоднозначное одновременное владение с ясной ошибкой и указанием костей. Допустим также детерминированный документированный порядок. Недопустим скрытый last-writer-wins, зависимость от порядка HashMap или молчаливое отключение старого рига. Если constraints уводят кость от кривой, интерфейс должен позволять понять причину; не обещай одновременно несовместимые hard constraints.

Проверь rendering в мире и preview, stencil pass, attachment matrices/anchors, гизмо, bone picking, onion skin и motion path, несколько вызовов в одном кадре, экспорт/запись по штатному render path. Сохрани корректность с Iris при наличии в тестовом клиенте. Не рисуй управляющие маркеры поверх обычного экспорта: editor overlay должен следовать текущему режиму редактора.

Если реализуешь запекание, используй тот же solve, а не другую формулу. По умолчанию включи в законченный результат команду запечь Spline IK в костные keys по аналогии с существующим Bake IK: диапазон, шаг, влияние, twist, translation/stretch, согласованное отключение только выбранных spline chains в диапазоне и единый Undo. Проверь совпадение до/после, границы диапазона и nested formPath. Не записывай всю evaluated позу как additive PoseTransform без вычитания исходных layers. Если текущая архитектура требует существенного отдельного расширения bake, обоснуй это заранее и явно обозначь границы результата; не выдавай старую кнопку Bake IK за поддержку spline.

7. Tracks, keyframes и сериализация

Spline points и параметры должны анимироваться в фильме И в animation states, включая blending/выход из состояния. Не оставляй функциональность только в одном UI.

Предпочтительный UX дорожек: компактная группа цепочки/кривой, в ней параметры и точки с читаемыми именами; показывать только созданные spline entities, а не пустые сотни tracks всех костей. Выбор точки во viewport выбирает правильный редактор/дорожку, а выбор дорожки показывает точку. Используй существующую категорию IK, если отдельная категория не даёт очевидной пользы. Не меняй глобально устройство таймлайна.

Сравни два варианта: специализированные TrackKind для позы кривой/точек и параметров либо обоснованное расширение property resolver. Нынешние FormUtils.getProperty и ControlsTrack имеют описанные ограничения, поэтому выбор должен быть реальной интеграцией, а не случайно работающим набором строк. Не обязательно делать отдельную keyframe factory для каждого скаляра, если штатная numeric factory подходит.

Обязательно проверь TrackId round-trip, factory registration, TrackCatalog descriptors/seed values, UIKeyframeFactory registration, categories/style/search, nested body parts, selected target, insertion, auto-keyframe, duplicate/copy/paste/interpolation/Undo/Redo. Вставка первой точки/первого ключа должна брать видимое значение на текущем времени, а не превращать кривую в набор нулей.

Сделай единую логику авторских и временных значений. Смена кадра, удаление ключей/track, выключение цепочки, выход из state, изменение формы или модели не оставляют старый spline override. При blending не игнорируй коэффициент состояния. Позы ключей копируются глубоко; временные результаты factory не сохраняются по ссылке для следующего актёра.

Формат данных должен быть компактным, расширяемым и нейтральным для старых форм. Сохраняются ID, структура/порядок точек, authored значения, привязки и осмысленные режимы; не samples, solved matrices и UI selection. Добавь явную версию только там, где она действительно нужна для формата, и дефолты для отсутствующих полей.

Проверь save/load формы, фильма, animation states, пресетов/copy-paste и полное закрытие/открытие клиента. Неизвестные/временно отсутствующие bones, models, spline IDs должны корректно диагностироваться и не уничтожаться при чтении. На rename/delete моделей или костей применяй существующие механизмы обновления ссылок, либо сохраняй невалидную ссылку с понятной диагностикой. Переименование отображаемого названия точки не меняет её ID.

Форма без spline должна сохраняться и работать как раньше. Старый BoneIKIO и режим classic не переписывай под новую схему. Новая функция относится к ModelForm/IModel; MobForm не предоставляет тот же вычислительный скелет и не должен получать неработающие настройки по умолчанию.

8. UI и работа во viewport

Сделай доступную команду «Добавить Spline IK» в существующем контексте модели/IK. Конкретное место выбери по текущему UI; не добавляй большой самостоятельный редактор. Основной путь:

1) Пользователь выбирает конечную кость или границы цепочки через знакомый bone picker/tree, при возможности — существующее выделение во viewport.
2) UI явно показывает root → tip и количество костей, подсвечивает цепь, позволяет быстро исправить начало. Не угадывает ветку по порядку объектов в JSON.
3) «Создать сплайн по цепочке» строит осмысленную кривую с небольшим числом точек (обычно 3–4, с адаптацией к длине/изгибу), включает результат и выбирает точку для движения. Есть выбор существующей кривой этой формы.
4) Пользователь двигает точки гизмо, сразу видит изменение цепочки, ставит ключи штатным способом. Для создания базовой цепочки не нужна ручная настройка упоров/up/оси/iterations.

В основном блоке нужны выбор цепочки/сплайна, включение, влияние, режим длины и список/выбор точек. Twist и дополнительные параметры расположи компактно; редко нужные настройки — в закрытом advanced-разделе. Поля недопустимой конфигурации объясняют причину рядом с местом ввода. Подсветка ошибок и disabled состояние сами по себе не заменяют объяснение.

Во viewport показывай кривую, точки, выбранный segment/chain, начало и направление. Отличай control polygon от вычисленной кривой и фактической цепи, если рисуешь все три. Маркеры должны оставаться удобными для попадания при зуме и масштабе формы; opacity/depth должны позволять понимать пространство. Показ через геометрию при редактировании можно оформить знакомой опцией. Незавершённый hover не должен красть bone selection.

Выбранная точка использует стандартные translation handles, осевые ограничения, числовой ввод, текущие пользовательские keybinds, Enter/ЛКМ для подтверждения и Esc/штатную отмену. Не хардкодь G/R/S: в проекте есть свои пресеты. Один drag — одна запись Undo; отмена восстанавливает старт, static/runtime values и ключи. Отдельно проверь ввод через трекпады и последовательные короткие драги.

Добавление точки в выбранный сегмент, удаление, понятный порядок, минимум две точки, reset к исходной форме и duplicate кривой должны работать через обычные действия UI. Для минимально полезного multi-select, если он реализуется, используй существующие соглашения и применяй общую дельту к выбранным точкам; не заставляй multi-select быть условием простой правки одной точки.

У active edit target должен быть явный тип: bone/form/replay/spline point/handle. Не используй «фальшивые кости» для control points и не требуй, чтобы в модели была геометрия точки ради stencil. Render и picking применяют одну и ту же матрицу точки. Если расширяешь FormEditorTool или другой публичный API, сохраняй совместимость и проверь api/bbs-api.txt.

Обычное вращение кости, которым теперь управляет Spline IK, должно вести себя последовательно с существующим IK: UI объясняет solver ownership, а FK-значения остаются доступной исходной позой. Не лечи трудные drag-сценарии запретом всех действий. У control point должен быть прямой линейный отклик в своём пространстве; не вычисляй его drag Jacobian по кости после solve, которую он сам двигает.

В фильме/state ясно показывай, редактируется ли исходная форма, выбранный ключ или текущий кадр с автоключами. Не допускай ситуации, когда точка видна в одном месте, drag пишет статическое значение, а активный ключ немедленно его перекрывает. Проверяй небольшой viewport (включая 854×480), свёрнутые панели, смену актёра/части/состояния и восстановление selection после Undo.

9. Производительность и жизненный цикл

Не вычисляй заново неизменную иерархию, rest frames и curve sampling для каждого bone/потребителя. Раздели кэш топологии/rest, кэш формы кривой и evaluated результаты. Контекст ключа включает фактического владельца, model identity/revision, evaluated параметры/точки, опорный transform и время/epoch по необходимости. Не полагайся только на имя модели, identity mutable map или static poseVersion.

Значения анимации способны меняться без обычных уведомлений. Cache invalidation нужен при drag, scrub в обе стороны, state blending, изменении числа точек, bone hierarchy/model reload, Undo/Redo, переключении режима длины, scale и bind. Два актёра одного ModelInstance не делят позу. Для direct sampling/bake используй существующий механизм инвалидирования RenderFrame и не оставляй кэш последнего образца живому клиенту.

Математика должна иметь ограниченную стоимость, повторно использовать разумные рабочие буферы и не производить сериализацию/строки/большие карты в цикле по bones на каждый кадр. Пустая/отключённая система должна быстро завершаться. Не держи глобальные сильные ссылки на умершие формы без очистки.

Измеряй отдельно solver, rebuild curve samples, overlays и общий frame time. M/utils/profiler/BBSProfiler.java уже даёт counters и timers; используй его или небольшой отключаемый замер. Сначала оцени baseline, затем сравни одинаковую сцену с Spline IK on/off, отдельно с overlay. Приведи p50/p95 или другой повторяемый отчёт с размерами цепей/числом актёров, а не субъективное «лагов нет». Не переписывай соседние системы ради теоретического ускорения.

10. Обязательные реальные тесты через ai_helper

Помощник находится в I:/Projects/IT/Java/bbs-mod-wemppy/ai_helper. Перед использованием прочитай его AGENTS.md и README.md, при необходимости docs/irlights-benchmark.md, но не переноси специфичные требования IRLights benchmark на обычный spline-test.

Проверенное состояние на момент исследования: в bbs-fs/run/mods лежит ai-helper-0.2.0+mc1.20.4.jar. MCP-инструменты helper в предыдущей сессии не были доступны, но его Python CLI доступен. Команда health получила отказ соединения на localhost:25599. Это только снимок состояния API, не результат проверки Spline IK и не доказательство отсутствия любого процесса Minecraft. Не наследуй его как текущее состояние.

При запуске из каталога bbs-fs рабочие команды выглядят так:

    python ../ai_helper/tools/mc.py health
    python ../ai_helper/tools/mc.py launch --project I:/Projects/IT/Java/bbs-mod-wemppy/bbs-fs
    python ../ai_helper/tools/mc.py wait-world
    python ../ai_helper/tools/mc.py screen
    python ../ai_helper/tools/mc.py shot spline-ik-baseline
    python ../ai_helper/tools/mc.py log -n 60 -l ERROR+ --text
    python ../ai_helper/tools/bbs.py status
    python ../ai_helper/tools/bbs.py open SplineIK_Test
    python ../ai_helper/tools/bbs.py seek SplineIK_Test 40
    python ../ai_helper/tools/bbs.py play SplineIK_Test
    python ../ai_helper/tools/bbs.py pause SplineIK_Test

Фильм SplineIK_Test сначала нужно действительно создать, сейчас его наличие не проверено. Аналогично не выдумывай готовых fixture IDs. CLI также поддерживает type, click, key, script, world, stop, restart. Проверяй --help/код текущей версии. mc.py берёт проект из cwd/AIH_PROJECT/--project, поэтому не запускай случайно клиент самого helper.

Если helper нужно пересобрать/установить, используй именно -Pmc=1.20.4 и install --mc 1.20.4; его default build сейчас 1.21.11/Java21 и для этого BBS не подходит. Для install укажи --project каталога helper как источник JAR. Не выбирай просто самый новый файл из build/libs.

В run/config/aihelper.json был autoJoin в ai_timeline_review. Для своей проверки используй отдельный мир/фильм SplineIK, сохрани действующую конфигурацию и пользовательские данные. Не останавливай чужой клиент и не пересобирай его используемый classpath. Управляй своим тестовым клиентом через helper; для изменения Java останавливай его и запускай свежую сборку. Если во время чужого живого клиента нужна компиляция — используй изолированный output согласно актуальной памяти проекта. При запуске через Start-Process на Windows фоновому процессу задай WindowStyle Hidden.

Обнаруженные ограничения helper нужно решить честно:

- В src/mc1204/java/dev/qualet/aihelper/core/Input.java guiClick вызывает screen.mouseClicked и сразу mouseReleased. Полноценного pointer move/drag маршрута в изученной версии нет. Этим нельзя подтвердить успешное перетаскивание точки.
- raw keyboard events передаются с modifiers=0 и не меняют физическое GLFW-состояние, которое читают Window/Screen modifier checks. Старый неудачный Ctrl+click был ограничением тестового ввода, а не доказанным багом BBS.
- StateReader.screen перечисляет прямые vanilla ClickableWidget. Внутренний BBS UI может не появиться в списке; пустой widgets не означает пустой экран.
- BbsBridge поддерживает status/open/seek/pause/play, а не произвольную мутацию spline/rig. Нельзя выдумывать существующие endpoints для solver.

При необходимости минимально доработай тестовый мост ai_helper: pointer down/move/up/cancel, согласованный cursor/hover/modifier state либо узкие BBS inspection/fixture команды в клиентском потоке. Прочитай инструкции соседнего проекта и отдели его изменения в отчёте. Не добавляй в production solver ветки «для теста». Инспекция рассчитанных matrices полезна, но прямой вызов setPoint не считается проверкой пользовательского drag. Screenshot с конечной позой не доказывает работу Undo/сохранения/ввода. Реально проходи UI-сценарии и сохраняй доказательства.

Основная модель:
E:/Animations/RIGs & models/Змея/model.bbs.json
E:/Animations/RIGs & models/Змея/rattlesnake_texture.png

Модель разрешено изменять для тестирования: структуру, количество костей и нужные данные. Перед первым изменением сохрани исходник/backup, опиши цель каждого варианта и сохраняй проверяемую связь с исходной «Змеёй». Предпочти отдельные варианты для короткой/длинной цепочки, чтобы не потерять исходный regression case. Не записывай локальные абсолютные пути E:/... в production код/сериализацию формы.

Факты о модели из исследования:

- model.bbs.json версии 0.7.2, 10 groups, текстура 64×64. SHA256 исходного JSON: 47E338A79031BBD8236C4423E492C9150F41E607BEB58099AEAEAF3F76F17F3F. Перепроверь, если файл изменился.
- All — корень. Основная последовательная ветка: All → bone → bone2 → bone3 → bone4 → bone5 → bone6 → bone7 → bone8. bone9 — отдельный ребёнок All.
- Основной первый тест — восемь костей bone..bone8 с родителем All, чтобы не захватить bone9. В обычных IK терминах это tip=bone8, chain length=8; Spline UI может использовать явные root/tip.
- Пивоты bone..bone8 имеют одинаковые X=-0.8775, Y=1.5795; Z=-22.55, -15.53, -8.51, -1.49, 5.53, 12.55, 19.57, 26.59. Семь интервалов по 7.02 px = 0.43875 блока. Не считай, что из этого автоматически известна длина последнего сегмента.
- У bone8 нет child tip marker, но есть геометрия. Это обязательная проверка обработки последней кости. All тоже содержит геометрию; автоматически включать его как очередной сегмент не следует.
- Есть walk, idle, bite, shake. walk вращает основную цепь, idle затрагивает bone9, bite содержит трансформации All. Используй их для реальной проверки coexistence с actions/FK и движения parent frame. Не удаляй анимации, чтобы скрыть конфликт solver.

Доступ к ассетам: HEAD 480176851 сохраняет глобальную библиотеку основной, мировую — дополнительной. BBSMod.getAssetsFolder/getAssetsPath относятся к основной библиотеке, стандартный dev-путь run/config/bbs/assets. Найди фактически загружаемый источник и исключи одноимённые теневые копии модели. Для тестовой копии используй отдельное имя в assets/models и штатную загрузку; само существование E:/... не значит, что клиент уже видит эту папку. Не меняй библиотечную политику проекта ради теста.

Рабочий цикл обязателен на протяжении реализации: изменение → сборка/свежий клиент → сценарий через ai_helper → снимки/данные/log → анализ → исправление → повтор. Не оставляй весь игровой прогон на самый конец и не перекладывай его на меня.

Матрица проверок:

| Область | Конкретные сценарии и ожидаемые свойства |
| --- | --- |
| Базовый UX | Создание цепочки на исходной «Змее», auto spline, назначение существующего, выбор точек, drag по трём осям/плоскости, числовой ввод, отмена, Undo/Redo, новые keys. Результат действительно проходит через UI. |
| Геометрия | Прямая, C, S, пространственная спираль, перегиб, почти 180°, совпадающие точки, самопересечение, сильно неодинаковые расстояния между точками; последняя bone8 вращается предсказуемо. |
| Размер | 1 кость с определённым virtual endpoint, 2, исходные 8, варианты 16/32/64/128; 2/3/4 и больше points. Нулевая/невалидная chain безопасно отклоняется. |
| Длины | Preserve/Fit, кривая короче/длиннее цепи, неодинаковые bone lengths, root movement/pinning, фактические расстояния joints, реальное поведение geometry scale и швов. |
| Ориентация | Twist 0/±90/180/360/720°, прямая и перегиб, включение/выключение, source Euler/quaternion, rest rotations и направление кости не по Y. Нет необъяснимых flips. |
| Пространства | Transform формы/All/актёра, body part с anchor, scale 0.5/2, nonuniform и mirror, near-zero scale с безопасной обработкой. Видимая точка, pick и gizmo совпадают. |
| Влияние/FK | 0/0.5/1, переходы влияния, walk/idle/bite/shake, pose/overlay/bone tracks; weight=0 эквивалентен исходной сцене. |
| Время | Playback, pause, повтор одного тика, прыжок вперёд/назад, subframe interpolation, states и их blend/выход, удаление track. Результат детерминирован и старые overrides исчезают. |
| Состояние данных | Save/load формы/фильма/state, полный перезапуск, duplicate/copy/paste, вставка/удаление/rename точки после ключевания, Undo topology changes; ID и keys не перескакивают. |
| Конфликты | Отсутствующая bone/curve/model, неверный root-tip, циклы ссылок, перекрытия IK/spline/physics/limits, повреждённые numeric values. Отказ локален и объясним. |
| Общий asset | Два и несколько актёров с одной моделью и разными curves; переключение редактора, thumbnail/preview/stencil passes не переносят чужую позу. |
| Матрицы/рендер | Attach к spline-driven кости, gizmo/picking, onion skin, motion path, экспорт через реальный pipeline; BOBJ отдельный smoke-тест после cubic. |
| Bake | Совпадение результата в диапазоне, stretch/twist/influence, отключение только запечённой цепочки, восстановление вне диапазона, Undo и nested form если поддерживается командой. |
| Производительность | Сравнимые baseline/on/off, разные размеры chains и число актёров, overlay отдельно, повторяемые замеры solver и кадра; без постоянной перестройки неизменных curves и течи памяти. |

Результат игровых тестов должен подкрепляться скриншотами, логами и, где полезно, числовыми данными. Основной риг — «Змея». Синтетические математические тесты и отдельный BOBJ fixture дополняют его, а не заменяют.

11. Порядок реализации и критерии готовности

Этап 1: сверить код/ветку, снять baseline исходной «Змеи» в тестовом клиенте через helper, подготовить backup/фикстуры, определить данные, пространство, default UX и критерии численной точности. Уточнить возможности helper для drag до того, как рассчитывать на такую проверку.

Этап 2: минимальный законченный вертикальный проход — данные цепочки/точек, solver для cubic, применение в render И matrices, создание auto spline, выбор и перемещение точки через штатный gizmo. Сразу реальный прогон на исходной восьмикостной цепочке, фиксы по результатам.

Этап 3: устойчивые frames/twist, два режима длины, influence, root/terminal semantics, сильные изгибы и пространства, несколько цепочек/форм, BOBJ. Повторные сценарии через helper и точечные математические проверки выявленных рисков.

Этап 4: полноценные tracks/keyframes и их UI в фильме и states, auto-key, deep copy, сброс runtime overrides, stable ID при topology edits, пресеты/duplicate/save-load/restart, Undo/Redo. Проверить интерфейс именно глазами пользователя.

Этап 5: bake, взаимодействие с anchors/limits/physics/обычным IK, все вспомогательные потребители matrices, regression checks и производительность. Дополнительные функции не должны отодвигать завершение обязательного сценария.

Этап 6: финальный build, свежий клиент с действительно новым кодом, сквозной пользовательский сценарий через ai_helper, очистка временной диагностической нагрузки, краткая документация использования и отчёт.

Для сборки проверяй обе части: gradlew.bat compileJava compileClientJava apiCheck. Итог — успешный gradlew.bat build на текущей ветке, а не только compileJava. Не подавляй ошибки пайпом, сохраняй exit code и лог. В src/test сейчас есть manual sanity main-классы; обычный test без JUnit не означает, что все они исполнились. build.gradle отдельно подключает migrationTest и anchorInterpolationTest к check. Для новых математических тестов обеспечь реальный запуск и сообщи команду/результат. Достаточны целевые тесты на arc/length, frames, deterministic evaluation, serialization/IDs и конкретные regressions; не нужна масса проверок, повторяющих строки реализации.

Публичный addon API контролируется api/bbs-api.txt, apiCheck и ADDONS.md. Изменение контракта оцени осмысленно; не обновляй snapshot только ради зелёного результата при случайной поломке.

Готово означает:

- «Змею» можно настроить и анимировать через законченный UI без ручного JSON.
- Точки выбираются и двигаются во viewport формы, фильма и states; ключи, автоключи, Esc и Undo/Redo работают в правильном контексте.
- Solver устойчив на заявленных размерах/формах, influence/twist/length ведут себя согласно понятным правилам, отсутствуют накопления и загрязнение исходного FK.
- Render и необходимые matrices-потребители согласованы; несколько актёров одной модели независимы.
- Данные выдерживают копирование, перестановку/вставку точек, сохранение и полный перезапуск. Старые сцены без spline и прежний IK сохраняют своё поведение.
- Систему реально проверили через ai_helper на исходной и целевых вариантах «Змеи», после исправлений повторили сценарии. Проверки BOBJ/взаимодействий и производительность подтверждены отдельно в пределах заявленной поддержки.
- Полная сборка и обязательные проверки успешны; нет незавершённых кнопок, заглушек, тихо неработающих дорожек и production-кода для подмены тестов.

В финальном отчёте кратко опиши путь пользователя, ключевые архитектурные решения, отклонения от предложенного подхода, изменения тестовой модели/ai_helper, выполненные команды/сценарии и ссылки на доказательства. Различай «прочитано в коде», «прошёл математический тест» и «проверено в живом клиенте». Если внешний блокер реально мешает части проверки, назови его точно и не объявляй эту часть готовой. Не заверши работу обещанием «потом подключить UI/ключи/сохранение»: это части текущей задачи.
