package mchorse.bbs_mod.ui.film.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;

import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIRecordOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

public class UIFilmController extends UIElement implements GizmoViewport
{
    public static final int CAMERA_MODE_CAMERA = 0;
    public static final int CAMERA_MODE_FREE = 1;
    public static final int CAMERA_MODE_ORBIT = 2;
    public static final int CAMERA_MODE_FIRST_PERSON = 3;
    public static final int CAMERA_MODE_THIRD_PERSON_BACK = 4;
    public static final int CAMERA_MODE_THIRD_PERSON_FRONT = 5;

    public final UIFilmPanel panel;

    public FilmEditorController editorController;
    private Map<String, Integer> actors;

    /* Character control */
    private IEntity controlled;

    /** Keying the replay by hand at the cursor. */
    public final FilmKeyframeInsertion keyframes = new FilmKeyframeInsertion(this);

    /** Whose motion path stays on screen regardless of the selection. */
    public final MotionPathPin motionPathPin = new MotionPathPin(this);

    /** What the editor draws over the preview. */
    private final FilmControllerHud hud = new FilmControllerHud(this);

    /** The mouse while an actor is driven by hand: look around, or a gamepad stick. */
    public final ActorMouseControl mouse = new ActorMouseControl();

    /* Recording state */
    private IEntity previousEntity;
    private Form playerForm;
    private boolean instantKeyframes;

    /** Shooting a take: the countdown, what is being written, and how it is committed. */
    public final FilmRecordingController recorder = new FilmRecordingController(this);

    /* Replay and group picking */
    private final GizmoInteraction gizmo = new GizmoInteraction(this);

    /** The pick buffer the viewport is hit-tested against, and what it currently reports. */
    public final FilmStencilPicker picker = new FilmStencilPicker(this);

    public final OrbitFilmCameraController orbit = new OrbitFilmCameraController(this);
    public final OrbitViewGizmo orbitGizmo = new OrbitViewGizmo(this);
    private int pov;
    private boolean paused;

    private WorldRenderContext worldRenderContext;

    public UIFilmController(UIFilmPanel panel)
    {
        this.panel = panel;
        this.setPov(BBSSettings.editorCameraMode.get());

        IKey category = UIKeys.FILM_CONTROLLER_KEYS_CATEGORY;

        Supplier<Boolean> hasActor = () -> this.getCurrentEntity() != null;
        Supplier<Boolean> hasTwoOrMoreReplays = () -> this.panel.getData() != null && this.panel.getData().replays.getList().size() >= 2;

        this.keys().register(Keys.FILM_CONTROLLER_START_RECORDING, this::pickRecording).active(hasActor).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_INSERT_FRAME, () ->
        {
            this.keyframes.insertFrame();
            UIUtils.playClick();
        }).active(hasActor).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_CONTROL, this::toggleControl).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ORBIT_MODE, this::toggleOrbitMode).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TELEPORT_ORBIT, this::teleportOrbitPivotToReplay).strict().active(() -> this.getPovMode() == CAMERA_MODE_ORBIT).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_ATTACH_ORBIT, () ->
        {
            this.toggleOrbitAttachment();
            UIUtils.playClick();
        }).strict().active(() -> this.getPovMode() == CAMERA_MODE_ORBIT).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ORTHO, () ->
        {
            this.orbit.toggleOrtho();
            UIUtils.playClick();
        }).strict().active(() -> this.getPovMode() == CAMERA_MODE_ORBIT).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_REPLAY_MENU, this::toggleReplayMenu).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_MOVE_REPLAY_TO_CURSOR, () ->
        {
            Area area = this.panel.preview.getViewport();
            UIContext context = this.getContext();
            World world = MinecraftClient.getInstance().world;
            Camera camera = this.panel.getCamera();

            Vector3f rayOffset = new Vector3f();
            Vector3f rayDirection = camera.getMouseRay(context.mouseX, context.mouseY, area.x, area.y, area.w, area.h, rayOffset);

            HitResult result = RayTracing.rayTrace(
                world,
                RayTracing.fromVector3d(new Vector3d(camera.position).add(rayOffset.x, rayOffset.y, rayOffset.z)),
                RayTracing.fromVector3f(rayDirection),
                512F
            );

            if (result.getType() == HitResult.Type.BLOCK)
            {
                this.panel.replayEditor.moveReplay(result.getPos().x, result.getPos().y, result.getPos().z);
            }
        }).active(hasActor).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_RESTART_ACTIONS, this.panel::restartActions).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ONION_SKIN, () ->
        {
            this.getOnionSkin().enabled.toggle();

            UIUtils.playClick();
        }).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_MOTION_PATH, () ->
        {
            this.getMotionPath().enabled.toggle();

            UIUtils.playClick();
        }).strict().active(() -> !this.panel.hasSelectedClip()).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_MOTION_PATH_PIN, () ->
        {
            this.motionPathPin.toggle();

            UIUtils.playClick();
        }).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_OPEN_REPLAYS, () ->
        {
            this.panel.showPanel(this.panel.replayEditor);
        }).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_PREV_REPLAY, () -> this.switchReplay(-1)).active(hasTwoOrMoreReplays).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_NEXT_REPLAY, () -> this.switchReplay(1)).active(hasTwoOrMoreReplays).category(category);

        this.noCulling();
    }

    private void switchReplay(int direction)
    {
        List<Replay> list = this.panel.getData().replays.getList();

        int index = list.indexOf(this.getReplay());
        int newIndex = MathUtils.cycler(index + direction, list);
        Replay replay = list.get(newIndex);

        this.panel.replayEditor.setReplay(replay);
        UIUtils.playClick();
    }

    public boolean isInstantKeyframes()
    {
        return this.instantKeyframes;
    }

    public void toggleInstantKeyframes()
    {
        this.instantKeyframes = !this.instantKeyframes;
    }

    public boolean isPaused()
    {
        return this.paused;
    }

    public void setPaused(boolean paused)
    {
        this.paused = paused;
    }

    void toggleMousePointer(boolean disable)
    {
        ActorMouseControl.togglePointer(disable);
    }

    public ValueOnionSkin getOnionSkin()
    {
        return BBSSettings.editorOnionSkin;
    }

    public ValueMotionPath getMotionPath()
    {
        return BBSSettings.editorMotionPath;
    }

    /**
     * A motion path target pinned so it keeps showing regardless of what's
     * selected. When nothing is pinned the path follows the selection (the
     * selected replay's bone, or its root coordinates). The pinned replay is
     * held live (not by id), so it self-clears once the replay is gone.
     */
    int getTick()
    {
        return this.panel.getCursor();
    }

    Replay getReplay()
    {
        return this.panel.replayEditor.getReplay();
    }

    int getCurrentReplayIndex()
    {
        if (this.panel.getData() == null)
        {
            return -1;
        }

        Replay replay = this.getReplay();

        return replay == null ? -1 : this.panel.getData().replays.getList().indexOf(replay);
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.picker.getStencil();
    }

    /** The world render pass in progress, for the companions that draw inside it. */
    WorldRenderContext worldRenderContext()
    {
        return this.worldRenderContext;
    }

    GizmoInteraction gizmo()
    {
        return this.gizmo;
    }

    public IEntity getCurrentEntity()
    {
        Replay replay = this.getReplay();

        return replay == null ? null : this.getEntities().get(replay.getId());
    }

    public int getPovMode()
    {
        return this.pov;
    }

    public void setPov(int pov)
    {
        /* Shift-cycling down from 0 (or a setting saved by an older build) may hand a value
         * outside 0..5; floorMod wraps it instead of letting -1 leak into the settings and
         * unlight every checkmark in the mode menu. */
        this.pov = Math.floorMod(pov, 6);
        this.orbit.enabled = this.pov > 1;

        BBSSettings.editorCameraMode.set(this.pov);
    }

    private int getMouseMode()
    {
        return this.mouse.getMode();
    }

    /**
     * Steps the mouse to that mode. Look mode needs the server to run the mod — it turns the
     * real player's head, and a vanilla server would fight the client over it — so off one the
     * walk skips straight to the first stick and says why.
     */
    void setMouseMode(int mode)
    {
        if (!ClientNetwork.isIsBBSModOnServer() && mode == 0)
        {
            mode = 1;

            this.getContext().notifyError(UIKeys.FILM_CONTROLLER_SERVER_WARNING);
        }

        this.mouse.setMode(mode, this.controlled);
    }

    public void createEntities()
    {
        this.stopRecording();

        if (this.controlled != null)
        {
            this.toggleControl();
        }

        this.editorController = new FilmEditorController(this.panel.getData(), this);
        this.editorController.createEntities();

        Map<String, IEntity> entities = this.panel.getRunner().getContext().entities;

        entities.clear();
        entities.putAll(this.editorController.getEntities());
    }

    public Map<String, IEntity> getEntities()
    {
        return this.editorController == null ? Collections.emptyMap() : this.editorController.getEntities();
    }

    public Map<String, Integer> getActors()
    {
        return this.actors;
    }

    public void updateActors(Map<String, Integer> actors)
    {
        this.actors = actors;
    }

    /* Character control state */

    public IEntity getControlled()
    {
        return this.controlled;
    }

    public boolean isControlling()
    {
        return this.controlled != null;
    }

    public void toggleControl()
    {
        this.getContext().unfocus();

        if (this.panel.replayEditor.isVisible())
        {
            this.panel.replayEditor.pickReplayCategory();
        }

        boolean replacePlayer = ClientNetwork.isIsBBSModOnServer();
        Map<String, IEntity> entities = this.getEntities();

        if (this.controlled != null)
        {
            if (replacePlayer && this.previousEntity != null)
            {
                this.controlled.setForm(this.playerForm);

                entities.put(CollectionUtils.getKey(entities, this.controlled), this.previousEntity);
                this.previousEntity = null;
            }

            this.controlled = null;
        }
        else if (this.panel.replayEditor.replaysList.replays.isSelected())
        {
            this.controlled = this.getCurrentEntity();

            if (replacePlayer && this.controlled != null)
            {
                MCEntity player = Morph.getMorph(MinecraftClient.getInstance().player).entity;

                this.playerForm = player.getForm();
                this.previousEntity = this.controlled;

                player.copy(this.controlled);
                PlayerUtils.teleport(this.controlled.getX(), this.controlled.getY(), this.controlled.getZ(), this.controlled.getHeadYaw(), this.controlled.getBodyYaw(), this.controlled.getPitch());
                entities.put(CollectionUtils.getKey(entities, this.controlled), player);

                this.controlled = player;
            }
        }

        this.setMouseMode(this.mouse.getMode());
        this.toggleMousePointer(this.controlled != null);

        if (this.controlled == null && this.isRecording())
        {
            this.stopRecording();
        }
    }

    private boolean canControl()
    {
        UIContext context = this.getContext();

        return this.controlled != null && context != null && !UIOverlay.has(context);
    }

    /* Recording */

    public boolean isRecording()
    {
        return this.recorder.isRecording();
    }

    public int getRecordingCountdown()
    {
        return this.recorder.getRecordingCountdown();
    }

    public List<String> getRecordingGroups()
    {
        return this.recorder.getRecordingGroups();
    }

    public boolean isTransformRecording()
    {
        return this.recorder.isTransformRecording();
    }

    public void startRecording(List<String> groups)
    {
        this.recorder.startRecording(groups);
    }

    public void stopRecording()
    {
        this.recorder.stopRecording();
    }

    public void pickRecording()
    {
        this.recorder.pickRecording();
    }

    public boolean isPlaying()
    {
        boolean playing = !UIOverlay.has(this.getContext()) && this.panel.isRunning();

        if (this.isPaused())
        {
            playing = true;
        }

        return playing;
    }

    /* Input handling */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.canControl())
        {
            return true;
        }

        boolean gizmoShown = this.canShowGizmo();

        /* Gizmo handles beat everything (rendered on top). The trackball
         * sphere is deferred to the very end so its flat screen disc doesn't
         * override actor markers and the viewport's other picks. Both are gated
         * on the gizmo actually being shown — otherwise the sphere grabs clicks
         * even with no bone selected (nothing rendered). */
        if (gizmoShown && this.gizmo.mouseClickedHandle(context))
        {
            return true;
        }

        /* Alt pick the replay */
        if (context.mouseButton == 0 && this.picker.getHoveredReplayIndex() >= 0)
        {
            this.pickReplay(this.picker.getHoveredReplayIndex());

            return true;
        }

        if (gizmoShown && this.gizmo.mouseClickedSphere(context))
        {
            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.picker.getStencil();
    }

    @Override
    public Matrix4f getGizmoProjection()
    {
        return this.panel.lastProjection;
    }

    @Override
    public Area getGizmoArea()
    {
        return this.panel.preview.getViewport();
    }

    @Override
    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        float gizmoTransition = this.isPlaying() ? context.getTransition() : 0F;

        return UIReplaysEditorUtils.startFilmGizmo(this.panel, context, stencilIndex, gizmoTransition);
    }

    @Override
    public void pickGizmoForm(UIContext context, Form form, String bone)
    {
        this.panel.replayEditor.pickFormWithOffers(context, form, bone);
    }

    private void pickReplay(int index)
    {
        this.panel.replayEditor.setReplay(this.panel.getData().replays.getList().get(index));

        if (!this.panel.replayEditor.isVisible())
        {
            this.panel.showPanel(this.panel.replayEditor);
        }
    }

    public void stopGizmoInteraction()
    {
        this.gizmo.stop();
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.canControl())
        {
            return true;
        }

        boolean consumed = this.gizmo.mouseReleased(context);

        consumed = this.orbitGizmo.mouseReleased(context) || consumed;

        this.stopGizmoInteraction();

        this.panel.replayEditor.releaseViewport(context, this.orbit.wasDragged());
        this.orbit.stop();

        if (this.panel.isFlying() && context.mouseButton == 2)
        {
            this.panel.dashboard.orbit.release();
        }

        return consumed || super.subMouseReleased(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.canControl())
        {
            if (this.isControlling() && context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.toggleControl();
                UIUtils.playClick();

                return true;
            }
            else if (context.getKeyAction() == KeyAction.PRESSED && context.getKeyCode() >= GLFW.GLFW_KEY_1 && context.getKeyCode() <= GLFW.GLFW_KEY_6)
            {
                /* Switch mouse input mode */
                this.setMouseMode(context.getKeyCode() - GLFW.GLFW_KEY_1);

                return true;
            }

            InputUtil.Key utilKey = InputUtil.fromKeyCode(context.getKeyCode(), context.getScanCode());

            if (this.canControlWithKeyboard(utilKey))
            {
                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    private boolean canControlWithKeyboard(InputUtil.Key utilKey)
    {
        if (!ClientNetwork.isIsBBSModOnServer())
        {
            return false;
        }

        GameOptions options = MinecraftClient.getInstance().options;

        return options.forwardKey.getDefaultKey() == utilKey
            || options.backKey.getDefaultKey() == utilKey
            || options.leftKey.getDefaultKey() == utilKey
            || options.rightKey.getDefaultKey() == utilKey
            || options.sneakKey.getDefaultKey() == utilKey
            || options.sprintKey.getDefaultKey() == utilKey
            || options.jumpKey.getDefaultKey() == utilKey;
    }

    public Icon getOrbitModeIcon()
    {
        return this.getOrbitModeIcon(this.getPovMode());
    }

    /** The camera modes in the order the picker lists them. */
    private static final List<Integer> CAMERA_MODES = List.of(
        CAMERA_MODE_CAMERA, CAMERA_MODE_FREE, CAMERA_MODE_ORBIT,
        CAMERA_MODE_FIRST_PERSON, CAMERA_MODE_THIRD_PERSON_BACK, CAMERA_MODE_THIRD_PERSON_FRONT
    );

    public static IKey getOrbitModeLabel(int povMode)
    {
        if (povMode == UIFilmController.CAMERA_MODE_FREE) return UIKeys.FILM_REPLAY_ORBIT_FREE;
        else if (povMode == UIFilmController.CAMERA_MODE_ORBIT) return UIKeys.FILM_REPLAY_ORBIT_ORBIT;
        else if (povMode == UIFilmController.CAMERA_MODE_FIRST_PERSON) return UIKeys.FILM_REPLAY_ORBIT_FIRST_PERSON;
        else if (povMode == UIFilmController.CAMERA_MODE_THIRD_PERSON_BACK) return UIKeys.FILM_REPLAY_ORBIT_THIRD_PERSON_BACK;
        else if (povMode == UIFilmController.CAMERA_MODE_THIRD_PERSON_FRONT) return UIKeys.FILM_REPLAY_ORBIT_THIRD_PERSON_FRONT;

        return UIKeys.FILM_REPLAY_ORBIT_CAMERA;
    }

    public Icon getOrbitModeIcon(int povMode)
    {
        if (povMode == UIFilmController.CAMERA_MODE_FREE) return Icons.REFRESH;
        else if (povMode == UIFilmController.CAMERA_MODE_ORBIT) return Icons.ORBIT;
        else if (povMode == UIFilmController.CAMERA_MODE_FIRST_PERSON) return Icons.VISIBLE;
        else if (povMode == UIFilmController.CAMERA_MODE_THIRD_PERSON_BACK) return Icons.ARROW_UP;
        else if (povMode == UIFilmController.CAMERA_MODE_THIRD_PERSON_FRONT) return Icons.ARROW_DOWN;

        return Icons.CAMERA;
    }

    public void teleportOrbitPivotToReplay()
    {
        this.orbit.teleportPivotToReplay();
    }

    public void toggleOrbitAttachment()
    {
        this.orbit.toggleAttachment();
    }

    public boolean zoomOrbit(double mouseWheel)
    {
        return this.orbit.zoom(mouseWheel);
    }

    public void toggleOrbitMode()
    {
        if (this.controlled != null)
        {
            this.setPov(this.pov + (Window.isShiftPressed() ? -1 : 1));

            return;
        }

        UIChoiceMenu.of(CAMERA_MODES)
            .current(this.getPovMode())
            .icon(this::getOrbitModeIcon)
            .label(UIFilmController::getOrbitModeLabel)
            .open(this.getContext(), this::setPov);
    }

    public void toggleReplayMenu()
    {
        if (this.controlled != null)
        {
            return;
        }

        UISimpleContextMenu menu = new UISimpleContextMenu();

        menu.actions.scroll.scrollItemSize = 30;

        this.getContext().replaceContextMenu((manager) ->
        {
            manager.custom(menu);
            manager.autoKeys();

            for (Replay replay : this.panel.getData().replays.getList())
            {
                int color = this.getReplay() == replay ? BBSSettings.primaryColor(0) : 0;

                manager.action(new ReplayContextAction(replay, IKey.raw(replay.getName()), () ->
                {
                    this.panel.replayEditor.setReplay(replay, false, UIReplaysEditor.OrbitReaction.SWITCH);

                    UIReplayList list = this.panel.replayEditor.replaysList.replays;

                    list.scrollToReplay(replay);

                    UIUtils.playClick();
                }, color));
            }
        });
    }

    public void handleCamera(Camera camera, float transition)
    {
        if (this.orbit.enabled)
        {
            int mode = this.getPovMode();

            if (mode == CAMERA_MODE_ORBIT)
            {
                this.orbit.setup(camera, transition);
            }
            else if (mode != CAMERA_MODE_FREE)
            {
                this.handleFirstThirdPerson(camera, transition, mode);
            }

            /* While flying, the FOV is driven live by the flight camera, so don't overwrite it */
            if (!this.panel.isFlying())
            {
                camera.fov = BBSSettings.getFov();
            }
        }
    }

    private void handleFirstThirdPerson(Camera camera, float transition, int mode)
    {
        IEntity controller = this.getCurrentEntity();

        if (controller == null)
        {
            return;
        }

        if (mode == CAMERA_MODE_FIRST_PERSON)
        {
            ActorCamera.firstPerson(camera, controller, transition);
        }
        else
        {
            ActorCamera.thirdPerson(camera, controller, transition, mode == CAMERA_MODE_THIRD_PERSON_BACK);
        }
    }

    /* Update */

    public void update()
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        RunnerCameraController runner = this.panel.getRunner();

        this.recorder.update(runner);

        if (this.editorController != null)
        {
            this.editorController.update();
        }

        if (this.canControl())
        {
            this.updateControls();
        }
    }

    private void updateControls()
    {
        this.mouse.applyTo(this.controlled);

        if (this.instantKeyframes)
        {
            this.keyframes.insertFrame();
        }
    }

    /* Render */

    public void renderHUD(UIContext context, Area area)
    {
        this.hud.render(context, area);
    }

    public void startRenderFrame(float tickDelta)
    {
        if (this.editorController != null)
        {
            this.editorController.startRenderFrame(tickDelta);
        }
    }

    public void renderFrame(WorldRenderContext context)
    {
        this.worldRenderContext = context;

        RenderSystem.enableDepthTest();

        if (this.editorController != null)
        {
            this.editorController.render(context);

            int povMode = this.panel.getController().getPovMode();

            if (povMode != UIFilmController.CAMERA_MODE_CAMERA && BBSSettings.recordingCameraPreview.get())
            {
                Recorder.renderCameraPreview(this.panel.getRunner().getPosition(), context.camera(), context.matrixStack());
            }
        }

        this.renderOrbitCenterMarker(context);

        ValueMotionPath motionPath = this.getMotionPath();

        if (motionPath.enabled.get() && !this.isRecording())
        {
            boolean pinned = this.motionPathPin.isPinned();
            Replay replay = pinned ? this.motionPathPin.getReplay() : this.getReplay();
            Pair<String, Boolean> bone = pinned ? this.motionPathPin.getBone() : this.getBone();

            MotionPath.render(context, motionPath, this, replay, bone, replay == null ? 0F : replay.getTick(this.getTick()));
        }

        this.mouse.trackCursor(this.canControl(), ClientNetwork.isIsBBSModOnServer());

        RenderSystem.disableDepthTest();
    }

    private void renderOrbitCenterMarker(WorldRenderContext context)
    {
        if (this.getPovMode() != CAMERA_MODE_ORBIT || !BBSSettings.editorOrbitCenterMarker.get())
        {
            return;
        }

        Vector3d center = this.orbit.getOrbitCenter(this.getCurrentTransition());

        if (center == null)
        {
            return;
        }

        net.minecraft.client.render.Camera camera = context.camera();
        double x = center.x - camera.getPos().x;
        double y = center.y - camera.getPos().y;
        double z = center.z - camera.getPos().z;
        float distanceScale = BBSSettings.getAxesDistanceScale((float) Math.sqrt(x * x + y * y + z * z));
        MatrixStack stack = context.matrixStack();

        stack.push();
        stack.translate(x, y, z);
        stack.scale(distanceScale, distanceScale, distanceScale);
        Draw.coolerAxes(stack, 0.12F, 0.007F);
        stack.pop();

        RenderSystem.enableDepthTest();
    }

    private float getCurrentTransition()
    {
        UIContext context = this.getContext();

        return context == null ? 0F : context.getTransition();
    }

    public Pair<String, Boolean> getBone()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor != null ? keyframeEditor.getBone() : null;
    }

    /** The space the bone gizmo should be drawn in (active transform's space). */
    public TransformSpace getBoneSpace()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor != null ? keyframeEditor.getBoneSpace() : TransformSpace.LOCAL;
    }

    /** The film camera's world&rarr;camera rotation, for reorienting the gizmo into a space. */
    public Matrix4f getGizmoView()
    {
        return this.panel.getCamera().view;
    }

    /** Whether the selected keyframe is the form's anchor track, so its transform gets a gizmo. */
    public boolean isAnchorGizmo()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor != null && keyframeEditor.isFormAnchorTrack();
    }

    public boolean getAnchorLocal()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor != null && keyframeEditor.getAnchorLocal();
    }

    /**
     * Whether the preview gizmo is actually drawn right now — the same gate the
     * renderer uses ({@link BaseFilmController#render}): axes enabled, not
     * recording, and a bone selected. The gizmo interaction must honour it, or
     * its trackball sphere keeps grabbing clicks (and blocking actor markers)
     * after a keyframe is deselected and nothing is rendered.
     */
    boolean canShowGizmo()
    {
        return UIBaseMenu.shouldRenderAxes() && !this.isRecording() && (this.getBone() != null || this.isAnchorGizmo());
    }

}
