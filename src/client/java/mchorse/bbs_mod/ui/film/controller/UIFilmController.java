package mchorse.bbs_mod.ui.film.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
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
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.TransformSpace;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
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
    private static final int REPLAY_STENCIL_OFFSET = Gizmo.STENCIL_MAX + 1;

    public final UIFilmPanel panel;

    public FilmEditorController editorController;
    private Map<String, Integer> actors;

    /* Character control */
    private IEntity controlled;
    private final Vector2i lastMouse = new Vector2i();
    private int mouseMode;
    private final Vector2f mouseStick = new Vector2f();

    /* Recording state */
    private IEntity previousEntity;
    private Form playerForm;
    private int recordingTick;
    private boolean recording;
    private int recordingCountdown;
    private List<String> recordingGroups;
    private BaseType recordingOld;
    private boolean instantKeyframes;

    /* Replay and group picking */
    private int hoveredReplayIndex = -1;
    private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private StencilMap stencilMap = new StencilMap();
    private final GizmoInteraction gizmo = new GizmoInteraction(this);

    public final OrbitFilmCameraController orbit = new OrbitFilmCameraController(this);
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
            this.insertFrame();
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
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_REPLAY_MENU, this::toggleReplayMenu).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_MOVE_REPLAY_TO_CURSOR, () ->
        {
            Area area = this.panel.preview.getViewport();
            UIContext context = this.getContext();
            World world = MinecraftClient.getInstance().world;
            Camera camera = this.panel.getCamera();

            HitResult result = RayTracing.rayTrace(
                world,
                RayTracing.fromVector3d(camera.position),
                RayTracing.fromVector3f(camera.getMouseDirection(context.mouseX, context.mouseY, area.x, area.y, area.w, area.h)),
                512F
            );

            if (result.getType() == HitResult.Type.BLOCK)
            {
                this.panel.replayEditor.moveReplay(result.getPos().x, result.getPos().y, result.getPos().z);
            }
        }).active(hasActor).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_RESTART_ACTIONS, () ->
        {
            this.panel.notifyServer(ActionState.RESTART);
            this.createEntities();
        }).category(category);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ONION_SKIN, () ->
        {
            this.getOnionSkin().enabled.toggle();

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

    private void toggleMousePointer(boolean disable)
    {
        net.minecraft.client.util.Window window = MinecraftClient.getInstance().getWindow();

        if (disable)
        {
            GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        }
        else
        {
            GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    public ValueOnionSkin getOnionSkin()
    {
        return BBSSettings.editorOnionSkin;
    }

    private int getTick()
    {
        return this.panel.getCursor();
    }

    private Replay getReplay()
    {
        return this.panel.replayEditor.getReplay();
    }

    private int getCurrentReplayIndex()
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
        return this.stencil;
    }

    public IEntity getCurrentEntity()
    {
        int idx = this.getCurrentReplayIndex();

        return idx < 0 ? null : this.getEntities().get(idx);
    }

    public int getPovMode()
    {
        return this.pov % 6;
    }

    public void setPov(int pov)
    {
        this.pov = pov;
        this.orbit.enabled = this.getPovMode() > 1;

        BBSSettings.editorCameraMode.set(this.getPovMode());
    }

    private int getMouseMode()
    {
        return this.mouseMode % 6;
    }

    private void setMouseMode(int mode)
    {
        if (!ClientNetwork.isIsBBSModOnServer() && mode == 0)
        {
            mode = 1;

            this.getContext().notifyError(UIKeys.FILM_CONTROLLER_SERVER_WARNING);
        }

        this.mouseMode = mode;

        if (this.controlled != null)
        {
            /* Restore value of the mouse stick */
            int index = this.getMouseMode() - 1;

            if (index >= 0)
            {
                float[] variables = this.controlled.getExtraVariables();

                this.mouseStick.set(variables[index * 2 + 1], variables[index * 2]);
            }
        }
    }

    private boolean isMouseLookMode()
    {
        return this.getMouseMode() == 0;
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

        IntObjectMap<IEntity> entities = this.panel.getRunner().getContext().entities;

        entities.clear();
        entities.putAll(this.editorController.getEntities());
    }

    public IntObjectMap<IEntity> getEntities()
    {
        return this.editorController == null ? new IntObjectHashMap<>() : this.editorController.getEntities();
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
            this.panel.replayEditor.pickPlayerCategory();
        }

        boolean replacePlayer = ClientNetwork.isIsBBSModOnServer();
        IntObjectMap<IEntity> entities = this.getEntities();

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

        this.setMouseMode(this.mouseMode);
        this.toggleMousePointer(this.controlled != null);

        if (this.controlled == null && this.recording)
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

    public boolean isPlaying()
    {
        boolean playing = !UIOverlay.has(this.getContext()) && this.panel.isRunning();

        if (this.isPaused())
        {
            playing = true;
        }

        return playing;
    }

    public boolean isRecording()
    {
        return this.recording;
    }

    public int getRecordingCountdown()
    {
        return this.recordingCountdown;
    }

    public List<String> getRecordingGroups()
    {
        return this.recordingGroups;
    }

    private boolean hasTransformRecordingGroup()
    {
        return this.recordingGroups != null && this.recordingGroups.contains(ReplayKeyframes.GROUP_TRANSFORM);
    }

    public boolean isTransformRecording()
    {
        return this.recording
            && this.recordingCountdown <= 0
            && this.hasTransformRecordingGroup();
    }

    public void startRecording(List<String> groups)
    {
        if (groups != null && groups.contains("outside"))
        {
            MinecraftClient.getInstance().setScreen(null);

            Replay replay = this.panel.replayEditor.getReplay();
            int index = this.panel.getData().replays.getList().indexOf(replay);

            if (index >= 0)
            {
                BBSModClient.getFilms().startRecording(this.panel.getData(), index, this.panel.getCursor());
            }

            return;
        }

        this.recordingTick = this.getTick();
        this.recording = true;
        this.recordingCountdown = Math.max(0, TimeUtils.toTick(BBSSettings.recordingCountdown.get()));
        this.recordingGroups = groups;
        boolean transformRecording = groups != null && groups.contains(ReplayKeyframes.GROUP_TRANSFORM);

        this.recordingOld = transformRecording ? this.getReplay().properties.toData() : this.getReplay().keyframes.toData();

        if (transformRecording)
        {
            if (this.controlled != null)
            {
                this.toggleControl();
            }

            this.setMouseMode(0);
        }
        else if (groups != null)
        {
            if (groups.contains(ReplayKeyframes.GROUP_LEFT_STICK))
            {
                this.setMouseMode(1);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_RIGHT_STICK))
            {
                this.setMouseMode(2);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_TRIGGERS))
            {
                this.setMouseMode(3);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_EXTRA1))
            {
                this.setMouseMode(4);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_EXTRA2))
            {
                this.setMouseMode(5);
            }
            else
            {
                this.setMouseMode(0);
            }
        }

        if (!transformRecording && this.controlled == null)
        {
            this.toggleControl();
        }

        this.toggleMousePointer(!transformRecording && this.controlled != null);
    }

    public void stopRecording()
    {
        if (!this.recording)
        {
            return;
        }

        boolean transformRecording = this.hasTransformRecordingGroup();

        this.recording = false;
        this.recordingGroups = null;

        if (!transformRecording && this.controlled != null)
        {
            this.toggleControl();
        }

        this.panel.setCursor(this.recordingTick);

        if (this.panel.getRunner().isRunning())
        {
            this.panel.togglePlayback();
        }

        if (this.recordingCountdown > 0)
        {
            return;
        }

        Replay replay = this.getReplay();

        if (replay != null && this.recordingOld != null)
        {
            if (transformRecording)
            {
                for (KeyframeChannel<?> channel : replay.properties.properties.values())
                {
                    if (PerLimbService.isPoseBoneChannel(channel.getId()))
                    {
                        channel.simplify();
                    }
                }

                BaseType newData = replay.properties.toData();

                replay.properties.fromData(this.recordingOld);
                replay.properties.preNotify();
                replay.properties.fromData(newData);
                replay.properties.postNotify();

                if (this.panel.replayEditor.getReplay() == replay)
                {
                    this.panel.replayEditor.setReplay(replay, false, UIReplaysEditor.OrbitReaction.SWITCH);
                }
            }
            else
            {
                for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
                {
                    channel.simplify();
                }

                BaseType newData = replay.keyframes.toData();

                replay.keyframes.fromData(this.recordingOld);
                replay.keyframes.preNotify();
                replay.keyframes.fromData(newData);
                replay.keyframes.postNotify();
            }

            this.recordingOld = null;
        }

        this.setMouseMode(ClientNetwork.isIsBBSModOnServer() ? 0 : 1);
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
        if (context.mouseButton == 0 && this.hoveredReplayIndex >= 0)
        {
            this.pickReplay(this.hoveredReplayIndex);

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
        return this.stencil;
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
        this.panel.replayEditor.pickForm(form, bone);
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

    public void pickRecording()
    {
        if (this.panel.replayEditor.getReplay() == null)
        {
            return;
        }

        if (this.recording)
        {
            this.stopRecording();

            return;
        }

        this.toggleMousePointer(false);

        UIRecordOverlayPanel panel = new UIRecordOverlayPanel(
            UIKeys.FILM_CONTROLLER_RECORD_TITLE,
            UIKeys.FILM_CONTROLLER_RECORD_DESCRIPTION,
            this::startRecording,
            this.panel.replayEditor.getCategory() == UIReplaysEditor.ReplayCategory.POSE
        );
        UIIcon icon = new UIIcon(Icons.UPLOAD, (b) -> panel.submit(Arrays.asList("outside")));

        icon.tooltip(UIKeys.FILM_GROUPS_OUTSIDE);
        panel.bar.add(icon);
        panel.keys().register(Keys.RECORDING_GROUP_OUTSIDE, icon::clickItself);

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    public Icon getOrbitModeIcon()
    {
        return this.getOrbitModeIcon(this.getPovMode());
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

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.autoKeys();

            menu.action(this.getOrbitModeIcon(0), UIKeys.FILM_REPLAY_ORBIT_CAMERA, this.pov == CAMERA_MODE_CAMERA, () -> this.setPov(0));
            menu.action(this.getOrbitModeIcon(1), UIKeys.FILM_REPLAY_ORBIT_FREE, this.pov == CAMERA_MODE_FREE, () -> this.setPov(1));
            menu.action(this.getOrbitModeIcon(2), UIKeys.FILM_REPLAY_ORBIT_ORBIT, this.pov == CAMERA_MODE_ORBIT, () -> this.setPov(2));
            menu.action(this.getOrbitModeIcon(3), UIKeys.FILM_REPLAY_ORBIT_FIRST_PERSON, this.pov == CAMERA_MODE_FIRST_PERSON, () -> this.setPov(3));
            menu.action(this.getOrbitModeIcon(4), UIKeys.FILM_REPLAY_ORBIT_THIRD_PERSON_BACK, this.pov == CAMERA_MODE_THIRD_PERSON_BACK, () -> this.setPov(4));
            menu.action(this.getOrbitModeIcon(5), UIKeys.FILM_REPLAY_ORBIT_THIRD_PERSON_FRONT, this.pov == CAMERA_MODE_THIRD_PERSON_FRONT, () -> this.setPov(5));
        });
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

        Vector3d position = new Vector3d();
        Vector3f rotation = new Vector3f();
        float distance = 5F;

        position.set(controller.getPrevX(), controller.getPrevY(), controller.getPrevZ());
        position.lerp(new Vector3d(controller.getX(), controller.getY(), controller.getZ()), transition);
        position.y += controller.getEyeHeight();

        rotation.set(controller.getPrevPitch(), controller.getPrevHeadYaw(), 0);
        rotation.lerp(new Vector3f(controller.getPitch(), controller.getHeadYaw(), 0), transition);

        rotation.x = MathUtils.toRad(rotation.x);
        rotation.y = MathUtils.toRad(rotation.y);

        if (mode == CAMERA_MODE_FIRST_PERSON)
        {
            camera.position.set(position);
            camera.rotation.set(rotation.x, rotation.y + MathUtils.PI, 0F);

            return;
        }

        boolean back = mode == CAMERA_MODE_THIRD_PERSON_BACK;
        Vector3f rotate = Matrices.rotation(rotation.x * (back ? 1 : -1), (back ? 0F : MathUtils.PI) - rotation.y);
        World world = MinecraftClient.getInstance().world;

        HitResult result = RayTracing.rayTraceEntity(
            world,
            RayTracing.fromVector3d(position),
            RayTracing.fromVector3f(rotate),
            distance
        );

        if (result.getType() == HitResult.Type.BLOCK)
        {
            distance = (float) position.distance(result.getPos().x, result.getPos().y, result.getPos().z) - 0.1F;
        }

        rotate.mul(distance);
        position.add(rotate);

        camera.position.set(position);
        camera.rotation.set(rotation.x * (back ? -1 : 1), rotation.y + (back ? 0 : MathUtils.PI), 0);
    }

    public void insertFrame()
    {
        Replay replay = this.getReplay();

        if (replay == null)
        {
            return;
        }

        UIReplaysEditor.ReplayCategory category = this.panel.replayEditor.getCategory();

        if (category == UIReplaysEditor.ReplayCategory.MODEL)
        {
            return;
        }

        if (category == UIReplaysEditor.ReplayCategory.POSE)
        {
            UIReplaysEditorUtils.insertPoseKeyframesAtTick(replay, this.getTick());
            return;
        }

        /* PLAYER */
        if (Window.isCtrlPressed())
        {
            this.toggleMousePointer(false);

            UIRecordOverlayPanel panel = new UIRecordOverlayPanel(
                UIKeys.FILM_CONTROLLER_INSERT_FRAME_TITLE,
                UIKeys.FILM_CONTROLLER_INSERT_FRAME_DESCRIPTION,
                (groups) ->
                {
                    BaseValue.edit(replay.keyframes, (keyframes) ->
                    {
                        keyframes.record(this.getTick(), this.getCurrentEntity(), groups);
                    });
                }
            );

            panel.onClose((event) -> this.toggleMousePointer(this.controlled != null));

            UIOverlay.addOverlay(this.getContext(), panel);
        }
        else
        {
            List<String> chosenGroups = Arrays.asList(ReplayKeyframes.GROUP_POSITION, ReplayKeyframes.GROUP_ROTATION);

            if (this.mouseMode == 1) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_LEFT_STICK);
            else if (this.mouseMode == 2) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_RIGHT_STICK);
            else if (this.mouseMode == 3) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_TRIGGERS);
            else if (this.mouseMode == 4) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_EXTRA1);
            else if (this.mouseMode == 5) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_EXTRA2);

            final List<String> groups = chosenGroups;

            BaseValue.edit(replay.keyframes, (keyframes) ->
            {
                keyframes.record(this.getTick(), this.getCurrentEntity(), groups);
            });
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

        this.handleRecording(runner);

        if (this.editorController != null)
        {
            this.editorController.update();
        }

        if (this.canControl())
        {
            this.updateControls();
        }
    }

    private void handleRecording(RunnerCameraController runner)
    {
        if (this.recording)
        {
            if (this.recordingCountdown > 0)
            {
                this.recordingCountdown -= 1;

                if (this.recordingCountdown <= 0)
                {
                    this.panel.togglePlayback();
                }
            }

            if (this.recordingCountdown <= 0)
            {
                boolean stopped = !runner.isRunning();

                if (BBSSettings.editorLoop.get())
                {
                    Vector2i loop = this.panel.getLoopingRange();
                    int min = loop.x;
                    int max = loop.y;
                    int ticks = this.panel.getCursor();

                    if (min >= 0 && max >= 0 && min < max && (ticks >= max - 1 || ticks < min) || stopped)
                    {
                        this.stopRecording();
                    }
                }
                else if (stopped)
                {
                    this.stopRecording();
                }
            }
        }
    }

    private void updateControls()
    {
        IEntity controller = this.controlled;

        if (!this.isMouseLookMode())
        {
            int index = this.getMouseMode() - 1;
            float[] extraVariables = controller.getExtraVariables();

            extraVariables[index * 2] = this.mouseStick.y;
            extraVariables[index * 2 + 1] = this.mouseStick.x;
        }

        if (this.instantKeyframes)
        {
            this.insertFrame();
        }
    }

    /* Render */

    public void renderHUD(UIContext context, Area area)
    {
        FontRenderer font = context.batcher.getFont();
        int mode = this.getMouseMode();

        if (this.controlled != null)
        {
            /* Render helpful guides for sticks and triggers controls */
            if (mode > 0)
            {
                String label = UIKeys.FILM_GROUPS_LEFT_STICK.get();

                if (mode == 2)
                {
                    label = UIKeys.FILM_GROUPS_RIGHT_STICK.get();
                }
                else if (mode == 3)
                {
                    label = UIKeys.FILM_GROUPS_TRIGGERS.get();
                }
                else if (mode == 4)
                {
                    label = UIKeys.FILM_GROUPS_EXTRA_1.get();
                }
                else if (mode == 5)
                {
                    label = UIKeys.FILM_GROUPS_EXTRA_2.get();
                }

                context.batcher.textCard(label, area.x + 5, area.ey() - 5 - font.getHeight(), Colors.WHITE, BBSSettings.primaryColor(Colors.A100));

                int ww = (int) (Math.min(area.w, area.h) * 0.75F);
                int hh = ww;
                int x = area.x + (area.w - ww) / 2;
                int y = area.y + (area.h - hh) / 2;
                int color = Colors.setA(Colors.WHITE, 0.5F);

                context.batcher.outline(x, y, x + ww, y + hh, color);

                int bx = area.x + area.w / 2 + (int) ((this.mouseStick.y) * ww / 2);
                int by = area.y + area.h / 2 + (int) ((this.mouseStick.x) * hh / 2);

                context.batcher.box(bx - 4, by - 4, bx + 4, by + 4, color);
            }
        }

        /* Render recording overlay */
        if (this.recording)
        {
            int x = area.x + 5 + 16;
            int y = area.y + 5;

            context.batcher.icon(Icons.SPHERE, Colors.RED | Colors.A100, x, y, 1F, 0F);

            if (this.recordingCountdown <= 0)
            {
                context.batcher.textCard(UIKeys.FILM_CONTROLLER_TICKS.format(this.getTick()).get(), x + 3, y + 4, Colors.WHITE, Colors.A50);
            }
            else
            {
                context.batcher.textCard(String.valueOf(this.recordingCountdown / 20F), x + 3, y + 4, Colors.WHITE, Colors.A50);
            }
        }

        int x = area.ex() - 4;
        int y = area.y + 5;

        if (this.panel.isFlying())
        {
            String label = UIKeys.FILM_CONTROLLER_SPEED.format(this.panel.dashboard.orbit.speed.getValue()).get();
            int w = font.getWidth(label);

            context.batcher.textCard(label, x - w, y, Colors.WHITE, Colors.A50);

            y += font.getHeight() + 7;
        }

        Replay replay = this.panel.replayEditor.getReplay();

        if (replay != null)
        {
            String label = replay.getName();
            int w = font.getWidth(label);

            context.batcher.textCard(label, x - w, y, Colors.WHITE, Colors.A50);

            Form form = replay.form.get();

            if (form != null)
            {
                x -= w + 35;
                y -= 5;

                context.batcher.clip(x, y - 10, 40, 40, context);

                y -= 10;

                FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);

                context.batcher.unclip(context);
            }
        }

        this.renderPickingPreview(context, area);

        this.orbit.handleOrbiting(context);
    }

    private void renderPickingPreview(UIContext context, Area area)
    {
        if (this.panel.isFlying() || this.worldRenderContext == null)
        {
            return;
        }

        boolean altPressed = Window.isAltPressed();

        RenderSystem.depthFunc(GL11.GL_LESS);

        /* Cache the global stuff */
        MatrixStackUtils.cacheMatrices();

        RenderSystem.setProjectionMatrix(this.panel.lastProjection, VertexSorter.BY_Z);
        RenderSystem.setInverseViewRotationMatrix(new Matrix3f(this.panel.lastView).invert());

        /* Render the stencil */
        MatrixStack worldStack = this.worldRenderContext.matrixStack();

        worldStack.push();
        worldStack.loadIdentity();
        MatrixStackUtils.multiply(worldStack, this.panel.lastView);
        this.renderStencil(this.worldRenderContext, this.getContext(), altPressed);
        worldStack.pop();

        /* Return back to orthographic projection */
        MatrixStackUtils.restoreMatrices();

        RenderSystem.depthFunc(GL11.GL_ALWAYS);

        this.hoveredReplayIndex = -1;

        if (this.canShowGizmo())
        {
            this.gizmo.update(context);
            this.gizmo.renderSphereHighlight(context);
        }

        if (!this.stencil.hasPicked())
        {
            return;
        }

        int index = this.stencil.getIndex();
        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        Pair<Form, String> pair = this.stencil.getPicked();
        int w = texture.width;
        int h = texture.height;

        ShaderProgram previewProgram = BBSShaders.getPickerPreviewProgram();
        Supplier<ShaderProgram> getPickerPreviewProgram = BBSShaders::getPickerPreviewProgram;
        GlUniform target = previewProgram.getUniform("Target");

        if (target != null)
        {
            target.set(index);
        }

        GlUniform highlight = previewProgram.getUniform("HighlightColor");

        if (highlight != null)
        {
            int color = BBSSettings.stencilHighlightColor.get();
            highlight.set(Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));
        }

        RenderSystem.enableBlend();
        context.batcher.texturedBox(getPickerPreviewProgram, texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, h, w, 0, w, h);

        if (altPressed)
        {
            int selectedReplayIndex = this.getCurrentReplayIndex();
            int stencilIndex = index - REPLAY_STENCIL_OFFSET;

            if (stencilIndex >= 0 && stencilIndex < this.panel.getData().replays.getList().size() && stencilIndex != selectedReplayIndex)
            {
                this.hoveredReplayIndex = stencilIndex;

                String label = this.panel.getData().replays.getList().get(stencilIndex).getName();

                context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
            }
            else if (pair != null && pair.a != null)
            {
                String label = pair.a.getFormIdOrName();

                if (!pair.b.isEmpty())
                {
                    label += " - " + pair.b;
                }

                context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
            }
        }
        else if (pair != null && pair.a != null)
        {
            String label = pair.a.getFormIdOrName();

            if (!pair.b.isEmpty())
            {
                label += " - " + pair.b;
            }

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }
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

        Mouse mouse = MinecraftClient.getInstance().mouse;
        int x = (int) mouse.getX();
        int y = (int) mouse.getY();

        if (this.canControl())
        {
            if (this.isMouseLookMode() && ClientNetwork.isIsBBSModOnServer())
            {
                float cursorDeltaX = (x - this.lastMouse.x) / 2F;
                float cursorDeltaY = (y - this.lastMouse.y) / 2F;

                MinecraftClient.getInstance().player.changeLookDirection(cursorDeltaX, cursorDeltaY);
            }
            else
            {
                /* Control sticks and triggers variables */
                float sensitivity = 100F;

                float xx = (y - this.lastMouse.y) / sensitivity;
                float yy = (x - this.lastMouse.x) / sensitivity;

                this.mouseStick.add(xx, yy);
                this.mouseStick.x = MathUtils.clamp(this.mouseStick.x, -1F, 1F);
                this.mouseStick.y = MathUtils.clamp(this.mouseStick.y, -1F, 1F);
            }
        }

        this.lastMouse.set(x, y);

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

    public Pair<String, TransformSpace> getBone()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor != null ? keyframeEditor.getBone() : null;
    }

    /** Whether the selected keyframe is the form's anchor track, so its transform gets a gizmo. */
    public boolean isAnchorGizmo()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor != null && keyframeEditor.isFormAnchorTrack();
    }

    public TransformSpace getAnchorSpace()
    {
        UIKeyframeEditor keyframeEditor = this.panel.replayEditor.keyframeEditor;

        return keyframeEditor == null ? TransformSpace.PARENT : keyframeEditor.getAnchorSpace();
    }

    /**
     * Whether the preview gizmo is actually drawn right now — the same gate the
     * renderer uses ({@link BaseFilmController#render}): axes enabled, not
     * recording, and a bone selected. The gizmo interaction must honour it, or
     * its trackball sphere keeps grabbing clicks (and blocking actor markers)
     * after a keyframe is deselected and nothing is rendered.
     */
    private boolean canShowGizmo()
    {
        return UIBaseMenu.shouldRenderAxes() && !this.isRecording() && (this.getBone() != null || this.isAnchorGizmo());
    }

    private void renderStencil(WorldRenderContext renderContext, UIContext context, boolean altPressed)
    {
        Area viewport = this.panel.preview.getViewport();

        if (!viewport.isInside(context) || this.controlled != null)
        {
            this.stencil.clearPicking();

            return;
        }

        IEntity entity = this.getCurrentEntity();

        if ((entity == null || (this.pov == CAMERA_MODE_FIRST_PERSON && entity == this.getCurrentEntity())) && !altPressed)
        {
            return;
        }

        this.ensureStencilFramebuffer();

        boolean isPlaying = this.isPlaying();
        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();

        this.stencilMap.setup();
        this.stencil.apply();

        if (altPressed)
        {
            List<Replay> replays = this.panel.getData().replays.getList();
            int selectedReplayIndex = this.getCurrentReplayIndex();
            Pair<String, TransformSpace> bone = this.getBone();

            for (Map.Entry<Integer, IEntity> entry : this.getEntities().entrySet())
            {
                Replay replay = CollectionUtils.getSafe(replays, entry.getKey());

                if (replay == null)
                {
                    continue;
                }

                FilmControllerContext filmContext = FilmControllerContext.instance
                    .setup(this.getEntities(), entry.getValue(), replay, renderContext)
                    .transition(isPlaying ? renderContext.tickDelta() : 0)
                    .stencil(this.stencilMap)
                    .relative(replay.relative.get());

                if (entry.getKey() == selectedReplayIndex)
                {
                    this.stencilMap.objectIndex = replays.size() + REPLAY_STENCIL_OFFSET;
                    this.stencilMap.setIncrement(true);

                    filmContext
                        .bone(bone == null ? null : bone.a, bone == null ? TransformSpace.PARENT : bone.b)
                        .anchorGizmo(this.isAnchorGizmo(), this.getAnchorSpace());
                }
                else
                {
                    this.stencilMap.objectIndex = entry.getKey() + REPLAY_STENCIL_OFFSET;
                    this.stencilMap.setIncrement(false);
                }

                BaseFilmController.renderEntity(filmContext);
            }
        }
        else
        {
            Replay replay = this.panel.replayEditor.getReplay();
            Pair<String, TransformSpace> bone = this.getBone();

            this.stencilMap.setIncrement(true);

            BaseFilmController.renderEntity(FilmControllerContext.instance
                .setup(this.getEntities(), entity, replay, renderContext)
                .transition(isPlaying ? renderContext.tickDelta() : 0)
                .stencil(this.stencilMap)
                .relative(replay.relative.get())
                .bone(bone == null ? null : bone.a, bone == null ? TransformSpace.PARENT : bone.b)
                .anchorGizmo(this.isAnchorGizmo(), this.getAnchorSpace()));
        }

        int x = (int) ((context.mouseX - viewport.x) / (float) viewport.w * mainTexture.width);
        int y = (int) ((1F - (context.mouseY - viewport.y) / (float) viewport.h) * mainTexture.height);

        this.stencil.pick(x, y);
        this.stencil.unbind(this.stencilMap);

        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
    }

    private void ensureStencilFramebuffer()
    {
        this.stencil.setup(Link.bbs("stencil_film"));

        Texture mainTexture = this.stencil.getFramebuffer().getMainTexture();
        int w = BBSRendering.getVideoWidth();
        int h = BBSRendering.getVideoHeight();

        if (mainTexture.width != w || mainTexture.height != h)
        {
            this.stencil.resizeGUI(w, h);
        }
    }
}
