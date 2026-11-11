package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.controller.UIMotionPathContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIOnionSkinContextMenu;
import mchorse.bbs_mod.ui.film.controller.OrbitFilmCameraController;
import mchorse.bbs_mod.ui.film.controller.OrbitViewGizmo;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScreenOverlay;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.ScreenshotRecorder;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.VideoExportSettings;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.IrisShaderPacks;
import mchorse.bbs_mod.utils.iris.ShaderOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The film editor's preview: the world preview with the film's tools on it - its icons, the
 * gizmos, the orbit, the recording's marks, the audio waveform.
 */
public class UIFilmPreview extends UIWorldPreview
{
    /**
     * Layers an addon put over the preview.
     *
     * <p>Factories rather than elements: a preview is built anew every time a film editor is
     * opened, and an element belongs to exactly one of them.</p>
     */
    private static final List<Function<UIFilmPreview, UIElement>> OVERLAYS = new ArrayList<>();

    private List<AudioClip> clips = new ArrayList<>();
    private UIFilmPanel panel;
    private UIPlacementGizmo placementGizmo;

    public UIIcon onionSkin;
    public UIIcon motionPath;
    public UIIcon plause;
    public UIIcon teleport;
    public UIIcon flight;
    public UIIcon control;
    public UIIcon perspective;
    public UIIcon autoKeyframe;
    public UIIcon recordReplay;
    public UIIcon recordVideo;
    public UIIcon shaders;

    /** The side of the axes crosshair's block, matching {@link RenderSystem#renderCrosshair}'s reach. */
    private static final int CURSOR_SIZE = 24;

    /** Until when the wall cut's depth is shown next to the orbit centre, after Alt + wheel. */
    private long wallCutDepthShownUntil;

    private static final float WALL_CUT_STEP = 0.1F;
    private static final long WALL_CUT_DEPTH_SHOWN = 1500L;

    /** Where the axes crosshair or the navigation ball landed this frame, for drawing and clicking. */
    private final Area navBlock = new Area();

    public UIFilmPreview(UIFilmPanel filmPanel)
    {
        this.panel = filmPanel;
        this.placementGizmo = new UIPlacementGizmo(() -> filmPanel.cameraEditor, filmPanel::getCursor);

        /* Preview buttons */
        this.onionSkin = new UIIcon(Icons.ONION_SKIN, (b) -> this.openOnionSkin());
        this.onionSkin.highlight(() -> this.panel.getController().getOnionSkin().enabled.get(), Direction.BOTTOM);
        this.onionSkin.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE);
        this.motionPath = new UIIcon(Icons.CURVES, (b) -> this.openMotionPath());
        this.motionPath.highlight(() -> this.panel.getController().getMotionPath().enabled.get(), Direction.BOTTOM);
        this.motionPath.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_TITLE);
        this.plause = new UIIcon(() -> this.panel.isRunning() ? Icons.PAUSE : Icons.PLAY, (b) -> this.panel.togglePlayback());
        this.plause.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE);
        this.plause.context((menu) ->
        {
            menu.action(Icons.PLAY, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAY_FILM, () ->
            {
                if (!this.panel.checkShowNoCamera())
                {
                    this.panel.dashboard.closeThisMenu();

                    Films.playFilm(this.panel.getData().getId(), true);
                }
            });

            menu.action(Icons.PAUSE, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_FREEZE_PAUSED, !this.panel.getController().isPaused(), () ->
            {
                this.panel.getController().setPaused(!this.panel.getController().isPaused());
            });
        });
        this.teleport = new UIIcon(Icons.MOVE_TO, (b) -> this.panel.teleportToCamera());
        this.teleport.tooltip(UIKeys.FILM_TELEPORT_TITLE);
        this.teleport.context((menu) ->
        {
            menu.action(Icons.MOVE_TO, UIKeys.FILM_TELEPORT_CONTEXT_PLAYER, this.panel.playerToCamera, () -> this.panel.setPlayerToCamera(!this.panel.playerToCamera));
            menu.action(Icons.VISIBLE, UIKeys.FILM_TELEPORT_CONTEXT_SPECTATOR, BBSSettings.editorPlayerFollowsSpectator.get(), () -> BBSSettings.editorPlayerFollowsSpectator.set(!BBSSettings.editorPlayerFollowsSpectator.get()));
            menu.action(Icons.COPY, UIKeys.CAMERA_PANELS_CONTEXT_COPY_POSITION, () ->
            {
                Position current = new Position(this.panel.getCamera());

                Map<String, Double> map = new LinkedHashMap<>();

                UICameraUtils.copyPoint(map, current.point);
                UICameraUtils.copyAngle(map, current.angle);

                Window.setClipboard(UICameraUtils.mapToString(map));
            });

            Map<String, Double> map = UICameraUtils.stringToMap(Window.getClipboard());

            if (!map.isEmpty())
            {
                menu.action(Icons.PASTE, UIKeys.CAMERA_PANELS_CONTEXT_PASTE_POSITION, () ->
                {
                    Position position = new Position();
                    Point point = UICameraUtils.createPoint(map);
                    Angle angle = UICameraUtils.createAngle(map);

                    if (point != null && angle != null)
                    {
                        position.point.set(point);
                        position.angle.set(angle);
                    }

                    this.panel.cameraEditor.editClip(position);
                });
            }
        });
        this.flight = new UIIcon(Icons.PLANE, (b) -> this.panel.toggleFlight());
        this.flight.highlight(this.panel::isFlying, Direction.BOTTOM);
        this.flight.tooltip(UIKeys.CAMERA_EDITOR_KEYS_MODES_FLIGHT);
        this.control = new UIIcon(Icons.POSE, (b) -> this.panel.getController().toggleControl());
        this.control.highlight(() -> this.panel.getController().isControlling(), Direction.BOTTOM);
        this.control.tooltip(UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_CONTROL);
        this.perspective = new UIIcon(this.panel.getController()::getOrbitModeIcon, (b) -> this.panel.getController().toggleOrbitMode());
        this.perspective.tooltip(UIKeys.FILM_CONTROLLER_KEYS_CHANGE_CAMERA_MODE);
        this.perspective.context((menu) ->
        {
            UIFilmController controller = this.panel.getController();

            if (controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
            {
                menu.action(Icons.MOVE_TO, UIKeys.FILM_REPLAY_ORBIT_TELEPORT_TO_RECORDING, controller::teleportOrbitPivotToReplay);
                menu.action(Icons.LINK, UIKeys.FILM_CONTROLLER_KEYS_ATTACH_ORBIT, controller.orbit.isAttached(), controller::toggleOrbitAttachment);
                if (controller.orbit.canPinToBone())
                {
                    menu.action(Icons.BONE, UIKeys.FILM_CONTROLLER_ORBIT_PIN_TO_BONE, controller.orbit.isPinnedToBone(), controller.orbit::togglePinToBone);
                }

                menu.action(Icons.REFRESH, UIKeys.FILM_CONTROLLER_ORBIT_TURNS_WITH_ANCHOR, controller.orbit.isTurningWithAnchor(), controller.orbit::toggleTurningWithAnchor);
                menu.action(Icons.FRUSTUM, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORTHO, controller.orbit.isOrtho(), controller.orbit::toggleOrtho);
                menu.action(Icons.CUT, UIKeys.FILM_CONTROLLER_ORBIT_WALL_CUT, controller.orbit.isCuttingWalls(), controller.orbit::toggleWallCut);
            }
        });
        this.autoKeyframe = new UIIcon(Icons.KEY, (b) -> this.toggleAutoKeyframe());
        this.autoKeyframe.highlight(() -> BBSSettings.autoKeyframe.get(), Direction.BOTTOM);
        this.autoKeyframe.tooltip(UIKeys.FILM_AUTO_KEYFRAME);
        this.recordReplay = new UIIcon(Icons.SPHERE, (b) -> this.panel.getController().pickRecording());
        this.recordReplay.highlight(() -> this.panel.getController().isRecording(), Direction.BOTTOM);
        this.recordReplay.tooltip(UIKeys.FILM_REPLAY_RECORD);
        this.recordReplay.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_INSTANT_KEYFRAMES, this.panel.getController().isInstantKeyframes(), () ->
            {
                this.panel.getController().toggleInstantKeyframes();
            });

            menu.action(Icons.MOVE_TO, UIKeys.FILM_REPLAY_TELEPORT_TO_PLAYER, () -> this.panel.getController().keyframes.insertPlayerFrame());
        });
        this.shaders = this.createShadersIcon();
        this.recordVideo = new UIIcon(Icons.VIDEO_CAMERA, (b) ->
        {
            if (!this.canExport())
            {
                return;
            }

            int duration = this.panel.getData().camera.calculateDuration();
            VideoExportSettings settings = VideoExportSettings.global();

            applyExportSize(settings);
            BBSRendering.scheduleAfterNextExportFrame(() ->
            {
                this.panel.recorder.startRecording(settings, duration, BBSRendering.getTexture().id, BBSRendering.getVideoWidth(), BBSRendering.getVideoHeight());
            });
        });
        this.recordVideo.tooltip(UIKeys.CAMERA_TOOLTIPS_RECORD);
        this.recordVideo.context((menu) ->
        {
            menu.action(Icons.CAMERA, UIKeys.FILM_SCREENSHOT, () ->
            {
                if (BBSRendering.isHoldingExportFrame() || this.panel.recorder.isExporting())
                {
                    return;
                }

                ScreenshotRecorder recorder = BBSModClient.getScreenshotRecorder();
                File output = Window.isAltPressed() ? null : recorder.getScreenshotFile();
                VideoExportSettings settings = VideoExportSettings.global();

                applyExportSize(settings);
                BBSRendering.scheduleAfterHeldExportFrame(settings.heldFrames(), () ->
                {
                    Texture texture = BBSRendering.getTexture();
                    int w = BBSRendering.getVideoWidth();
                    int h = BBSRendering.getVideoHeight();
                    try
                    {
                        recorder.takeScreenshot(output, texture.id, w, h);
                    }
                    finally
                    {
                        this.applySize();
                    }

                    UIBaseMenu currentMenu = UIScreen.getCurrentMenu();
                    if (currentMenu != null)
                    {
                        UIMessageFolderOverlayPanel overlayPanel = new UIMessageFolderOverlayPanel(
                            UIKeys.FILM_SCREENSHOT_TITLE,
                            UIKeys.FILM_SCREENSHOT_DESCRIPTION,
                            recorder.getScreenshots()
                        );
                        UIOverlay.addOverlay(currentMenu.context, overlayPanel);
                    }
                });
            });

            menu.action(Icons.FOLDER, UIKeys.FILM_OPEN_SCREENSHOTS, () -> UIUtils.openFolder(BBSModClient.getScreenshotRecorder().getScreenshots()));
            menu.action(Icons.FILM, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEOS, () -> this.panel.recorder.openMovies());
            menu.action(Icons.GEAR, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEO_SETTINGS, () ->
            {
                UISettingsOverlayPanel panel = new UISettingsOverlayPanel();

                panel.showCategory("bbs", "video");
                UIOverlay.addOverlay(this.getContext(), panel);
            });

            menu.action(Icons.VIDEO_CAMERA, UIKeys.FILM_RENDER_QUEUE, this::exportQueueFromTabs);
            menu.action(Icons.SOUND, UIKeys.FILM_RENDER_AUDIO, this::renderAudio);
            menu.action(Icons.REFRESH, UIKeys.FILM_RESET_REPLAYS, this.panel.recorder.resetReplays, () ->
            {
                this.panel.recorder.resetReplays = !this.panel.recorder.resetReplays;
            });
        });

        this.addIcon(this.onionSkin, BBSSettings.previewIconOnionSkin);
        this.addIcon(this.motionPath, BBSSettings.previewIconMotionPath);
        this.addIcon(this.teleport, BBSSettings.previewIconTeleport);
        this.addIcon(this.flight, BBSSettings.previewIconFlight);
        this.addIcon(this.plause, BBSSettings.previewIconPlayback);
        this.addIcon(this.control, BBSSettings.previewIconControl);
        this.addIcon(this.perspective, BBSSettings.previewIconPerspective);
        this.addIcon(this.autoKeyframe, BBSSettings.previewIconAutoKeyframe);
        this.addIcon(this.shaders, BBSSettings.previewIconShaders);
        this.addIcon(this.recordReplay, BBSSettings.previewIconRecordReplay);
        this.addIcon(this.recordVideo, BBSSettings.previewIconExport);

        for (Function<UIFilmPreview, UIElement> factory : OVERLAYS)
        {
            this.add(factory.apply(this));
        }
    }

    /** Puts a layer of an addon's over the preview — one that takes the mouse, not just draws. */
    public static void registerOverlay(Function<UIFilmPreview, UIElement> factory)
    {
        OVERLAYS.add(factory);
    }

    /**
     * Auto-keyframing: with it on, every value edit keys the playhead instead of rewriting the
     * keyframe it was made on. Kept in the settings rather than on the film, so it stays where the
     * animator left it across films and sessions, the way looping does.
     */
    private void toggleAutoKeyframe()
    {
        BBSSettings.autoKeyframe.set(!BBSSettings.autoKeyframe.get());
    }

    public void openOnionSkin()
    {
        this.getContext().replaceContextMenu(new UIOnionSkinContextMenu(this.panel, this.panel.getController().getOnionSkin()));
    }

    public void openMotionPath()
    {
        this.getContext().replaceContextMenu(new UIMotionPathContextMenu(this.panel, this.panel.getController().getMotionPath()));
    }

    private void exportQueueFromTabs()
    {
        if (this.canExport())
        {
            this.panel.startQueueExportFromOpenTabs();
        }
    }

    /**
     * Whether an export can start at all: there has to be a camera, and ffmpeg has to be
     * installed. Both refusals say so on screen, ffmpeg's with a link to the guide.
     */
    private boolean canExport()
    {
        if (BBSRendering.isHoldingExportFrame())
        {
            return false;
        }

        if (this.panel.checkShowNoCamera())
        {
            return false;
        }

        if (!FFMpegUtils.checkFFMPEG())
        {
            UIMessageOverlayPanel panel = new UIMessageOverlayPanel(UIKeys.GENERAL_WARNING, UIKeys.GENERAL_FFMPEG_ERROR_DESCRIPTION);
            UIIcon guide = new UIIcon(Icons.HELP, (bb) -> UIUtils.openWebLink(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE_LINK.get()));

            guide.tooltip(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE);
            panel.icons.add(guide);

            UIOverlay.addOverlay(this.getContext(), panel);

            return false;
        }

        return true;
    }

    private void renderAudio()
    {
        Clips camera = this.panel.getData().camera;
        List<AudioClip> audioClips = camera.getClips(AudioClip.class);

        String name = StringUtils.createTimestampFilename() + ".wav";
        File videos = VideoExportSettings.global().getFolder();
        UIContext context = this.getContext();
        Vector2i range = BBSSettings.editorLoop.get() ? this.panel.getLoopingRange() : new Vector2i();

        if (AudioRenderer.renderAudio(new File(videos, name), audioClips, camera.calculateDuration(), 48000, TimeUtils.toSeconds(range.x), TimeUtils.toSeconds(range.y)))
        {
            UIOverlay.addOverlay(context, new UIMessageFolderOverlayPanel(UIKeys.GENERAL_SUCCESS, UIKeys.FILM_RENDER_AUDIO_SUCCESS, videos));
        }
        else
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, UIKeys.FILM_RENDER_AUDIO_ERROR));
        }
    }

    /** With a film open (camera and recording editors alike), the export's frame is marked. */
    @Override
    protected boolean isFramingExport()
    {
        return this.panel.getData() != null;
    }

    @Override
    protected boolean isSizeLocked()
    {
        return this.panel.recorder.isExporting() || super.isSizeLocked();
    }

    /** None of the icons can be used during an export, and the frame is all there is to watch */
    @Override
    protected boolean canShowIcons()
    {
        return !this.panel.recorder.isExporting();
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Area area = this.getViewport();

        if (area.isInside(context))
        {
            if (this.panel.getController().picker.pickTarget(context))
            {
                return true;
            }

            if (this.panel.getController().orbitGizmo.mouseClicked(context, this.navBlock))
            {
                return true;
            }

            if (this.placementGizmo.mouseClicked(context, area))
            {
                return true;
            }

            return this.panel.replayEditor.clickViewport(context, area);
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.placementGizmo.mouseReleased(context))
        {
            return true;
        }

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        Area area = this.getViewport();

        if (area.isInside(context) && !this.panel.isFlying() && this.panel.getController().getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
        {
            OrbitFilmCameraController orbit = this.panel.getController().orbit;

            /* Alt + wheel sets how much is left standing in front of the orbit centre */
            if (Window.isAltPressed() && orbit.isCuttingWalls())
            {
                float depth = orbit.getWallCutDepth() + (float) context.mouseWheel * WALL_CUT_STEP;

                orbit.setWallCutDepth(MathUtils.clamp(Math.round(depth / WALL_CUT_STEP) * WALL_CUT_STEP, 0F, 32F));
                this.wallCutDepthShownUntil = System.currentTimeMillis() + WALL_CUT_DEPTH_SHOWN;

                return true;
            }

            return this.panel.getController().zoomOrbit(context.mouseWheel);
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public void render(UIContext context)
    {
        Area area = this.getViewport();
        Camera camera = this.panel.getCamera();

        camera.copy(this.panel.getWorldCamera());
        camera.view.set(this.panel.lastView);
        camera.projection.set(this.panel.lastProjection);

        this.updateExportBands();
        this.renderFrame(context, area);
        this.updateIconsVisibility(context);
        this.hud.begin(area);
        this.reserveIconBar(area);

        /* First in the top left: while exporting, the export is what the editor is doing */
        this.panel.recorder.renderHud(context, this.hud);

        /* The navigation widget claims the bottom left corner before anything else does, so
         * that whatever the editor stacks there later (the stick guide) ends up above it
         * rather than on top of it. The ball replaces the axes crosshair when it is on, and
         * the block is reserved for whichever of the two is showing. Its click test reads
         * the same block, hence the field. */
        OrbitViewGizmo orbitGizmo = this.panel.getController().orbitGizmo;
        boolean ball = orbitGizmo.isActive();
        int navSize = ball ? orbitGizmo.getSize() : CURSOR_SIZE;

        this.navBlock.copy(this.hud.push(PreviewHud.Anchor.BOTTOM_LEFT, navSize, navSize));

        if (!ball)
        {
            this.renderCursor(context);
        }

        this.renderGuides(context, area);
        this.placementGizmo.render(context, area);
        this.renderWallCutDepth(context, area, camera);

        this.panel.getController().renderHUD(context, this.hud, this.navBlock);

        if (this.panel.replayEditor.isVisible() && BBSSettings.audioWaveformVisibleInPreview.get())
        {
            RunnerCameraController runner = this.panel.getRunner();
            int w = (int) (area.w * BBSSettings.audioWaveformWidth.get());
            int x = area.x(0.5F, w);
            float tick = this.panel.getCursor() + (runner.isRunning() ? context.getTransition() : 0);

            this.clips.clear();

            for (Clip clip : this.panel.getData().camera.get())
            {
                if (clip instanceof AudioClip)
                {
                    this.clips.add((AudioClip) clip);
                }
            }

            int h = BBSSettings.audioWaveformHeight.get();

            if (BBSSettings.audioWaveformPreviewCombined.get())
            {
                AudioRenderer.renderPreviewCombined(context.batcher, this.clips, tick, x, area.y + 10, w, h, context.menu.width, context.menu.height);
            }
            else
            {
                AudioRenderer.renderAll(context.batcher, this.clips, tick, x, area.y + 10, w, h, context.menu.width, context.menu.height);
            }
        }

        this.renderIconBar(context);

        /* Outside the bar's visibility: hiding the icons must not take away the one line
         * that says how to leave control mode. */
        if (this.panel.getController().isControlling())
        {
            String s = UIKeys.FILM_CONTROLLER_CONTROL_MODE_TOOLTIP.format(KeyCodes.getName(Keys.FILM_CONTROLLER_TOGGLE_CONTROL.getMainKey())).get();

            this.hud.label(context, PreviewHud.Anchor.BOTTOM_CENTER, s, Colors.WHITE, Colors.A50);
        }
        /* Free look holds the mouse the same way control mode does, so it owes the same line -
         * the pointer is gone, and this is what says how to get it back. */
        else if (this.panel.dashboard.orbitUI.isFreeLook())
        {
            String s = UIKeys.FILM_CONTROLLER_FREE_LOOK_TOOLTIP.format(KeyCodes.getName(Keys.FLIGHT.getMainKey())).get();

            this.hud.label(context, PreviewHud.Anchor.BOTTOM_CENTER, s, Colors.WHITE, Colors.A50);
        }

        /* Everything the corners collected this frame goes down here, each zone's wash first. */
        this.hud.flush(context);
        this.renderElements(context);
    }

    /** The wall cut's depth, by the orbit centre, for a moment after it was changed. */
    private void renderWallCutDepth(UIContext context, Area area, Camera camera)
    {
        OrbitFilmCameraController orbit = this.panel.getController().orbit;

        if (System.currentTimeMillis() > this.wallCutDepthShownUntil || !orbit.isCuttingWalls())
        {
            return;
        }

        String depth = String.format(Locale.ROOT, "%.1f", orbit.getWallCutDepth());
        String label = UIKeys.FILM_CONTROLLER_ORBIT_WALL_CUT_DEPTH.format(depth).get();
        int h = context.batcher.getFont().getHeight();
        int x = area.mx();
        int y = area.my();
        Vector3d center = orbit.getWallCutCenter();

        if (center != null)
        {
            Matrix4f mvp = new Matrix4f(camera.projection).mul(camera.view);
            Vector3f point = ScreenOverlay.project(mvp, area, new Vector3f(
                (float) (center.x - camera.position.x),
                (float) (center.y - camera.position.y),
                (float) (center.z - camera.position.z)
            ));

            if (point != null)
            {
                x = (int) point.x;
                y = (int) point.y;
            }
        }

        context.batcher.textCard(label, x + 12, y - h / 2);
    }

    private void renderCursor(UIContext context)
    {
        net.minecraft.client.render.Camera /* NECESSARY */ mcCamera = MinecraftClient.getInstance().gameRenderer.getCamera();
        MatrixStack stack = RenderSystem.getModelViewStack();

        stack.push();

        stack.multiplyPositionMatrix(context.batcher.getContext().getMatrices().peek().getPositionMatrix());
        stack.translate(this.navBlock.mx(), this.navBlock.my(), 0F);
        stack.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(mcCamera.getPitch()));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(mcCamera.getYaw()));
        stack.scale(-1F, -1F, -1F);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.renderCrosshair(10);

        stack.pop();
        RenderSystem.applyModelViewMatrix();
    }
}
