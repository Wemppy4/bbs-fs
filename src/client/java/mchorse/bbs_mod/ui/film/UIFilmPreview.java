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
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.ScreenshotRecorder;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector2i;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class UIFilmPreview extends UIElement
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

    public UIElement icons;

    public UIIcon onionSkin;
    public UIIcon motionPath;
    public UIIcon plause;
    public UIIcon teleport;
    public UIIcon flight;
    public UIIcon control;
    public UIIcon perspective;
    public UIIcon recordReplay;
    public UIIcon recordVideo;

    /** Whether the icon bar is currently shown - see {@link #updateIconsVisibility(UIContext)} */
    private boolean iconsVisible = true;

    /** The side of the axes crosshair's block, matching {@link RenderSystem#renderCrosshair}'s reach. */
    private static final int CURSOR_SIZE = 24;

    /** Everything drawn over the frame goes through here - see {@link PreviewHud}. */
    private final PreviewHud hud = new PreviewHud();

    /** Where the axes crosshair or the navigation ball landed this frame, for drawing and clicking. */
    private final Area navBlock = new Area();

    public UIFilmPreview(UIFilmPanel filmPanel)
    {
        this.panel = filmPanel;
        this.placementGizmo = new UIPlacementGizmo(filmPanel);

        this.icons = UI.row(0, 0);
        this.icons.row().resize();
        this.icons.relative(this).x(0.5F).y(1F).anchor(0.5F, 1F);

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
                menu.action(Icons.FRUSTUM, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORTHO, controller.orbit.isOrtho(), controller.orbit::toggleOrtho);
            }
        });
        this.recordReplay = new UIIcon(Icons.SPHERE, (b) -> this.panel.getController().pickRecording());
        this.recordReplay.highlight(() -> this.panel.getController().isRecording(), Direction.BOTTOM);
        this.recordReplay.tooltip(UIKeys.FILM_REPLAY_RECORD);
        this.recordReplay.context((menu) ->
        {
            menu.action(Icons.KEY, UIKeys.FILM_AUTO_KEYFRAME, BBSSettings.autoKeyframe.get(), this::toggleAutoKeyframe);

            menu.action(Icons.DOWNLOAD, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_INSTANT_KEYFRAMES, this.panel.getController().isInstantKeyframes(), () ->
            {
                this.panel.getController().toggleInstantKeyframes();
            });

            menu.action(Icons.MOVE_TO, UIKeys.FILM_REPLAY_TELEPORT_TO_PLAYER, () -> this.panel.getController().keyframes.insertPlayerFrame());
        });
        this.recordVideo = new UIIcon(Icons.VIDEO_CAMERA, (b) ->
        {
            if (!this.canExport())
            {
                return;
            }

            int duration = this.panel.getData().camera.calculateDuration();
            UIFilmPanel.applyExportSizeToBBS();
            BBSRendering.scheduleAfterNextExportFrame(() ->
            {
                this.panel.recorder.startRecording(duration, BBSRendering.getTexture().id, BBSRendering.getVideoWidth(), BBSRendering.getVideoHeight());
            });
        });
        this.recordVideo.tooltip(UIKeys.CAMERA_TOOLTIPS_RECORD);
        this.recordVideo.context((menu) ->
        {
            menu.action(Icons.CAMERA, UIKeys.FILM_SCREENSHOT, () ->
            {
                ScreenshotRecorder recorder = BBSModClient.getScreenshotRecorder();
                File output = Window.isAltPressed() ? null : recorder.getScreenshotFile();

                UIFilmPanel.applyExportSizeToBBS();
                BBSRendering.scheduleAfterNextExportFrame(() ->
                {
                    Texture texture = BBSRendering.getTexture();
                    int w = BBSRendering.getVideoWidth();
                    int h = BBSRendering.getVideoHeight();
                    recorder.takeScreenshot(output, texture.id, w, h);
                    this.panel.restorePreviewSize();

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

            menu.action(Icons.FILM, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEOS, () -> this.panel.recorder.openMovies());
            menu.action(Icons.GEAR, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEO_SETTINGS, () ->
            {
                UISettingsOverlayPanel panel = new UISettingsOverlayPanel();

                panel.showCategory("bbs", "video");
                UIOverlay.addOverlay(this.getContext(), panel, 430, 400);
            });

            menu.action(Icons.VIDEO_CAMERA, UIKeys.FILM_RENDER_QUEUE, this::exportQueueFromTabs);
            menu.action(Icons.SOUND, UIKeys.FILM_RENDER_AUDIO, this::renderAudio);
            menu.action(Icons.REFRESH, UIKeys.FILM_RESET_REPLAYS, this.panel.recorder.resetReplays, () ->
            {
                this.panel.recorder.resetReplays = !this.panel.recorder.resetReplays;
            });
        });

        this.icons.add(this.onionSkin, this.motionPath, this.teleport, this.flight, this.plause, this.control, this.perspective, this.recordReplay, this.recordVideo);
        this.add(this.icons);

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
        UIUtils.playClick();
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
        if (this.panel.checkShowNoCamera())
        {
            return false;
        }

        if (!FFMpegUtils.checkFFMPEG())
        {
            UIMessageOverlayPanel panel = new UIMessageOverlayPanel(UIKeys.GENERAL_WARNING, UIKeys.GENERAL_FFMPEG_ERROR_DESCRIPTION);
            UIIcon guide = new UIIcon(Icons.HELP, (bb) -> UIUtils.openWebLink(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE_LINK.get()));

            guide.tooltip(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE, Direction.LEFT);
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
        File videos = BBSRendering.getVideoFolder();
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

    public Area getViewport()
    {
        int width = BBSRendering.getVideoWidth();
        int height = BBSRendering.getVideoHeight();
        int w = this.area.w;
        int h = this.area.h;

        Camera camera = new Camera();

        camera.copy(this.panel.getWorldCamera());
        camera.updatePerspectiveProjection(width, height);

        Vector2i size = Vectors.resize(width / (float) height, w, h);
        Area area = new Area();

        area.setSize(size.x, size.y);
        area.setPos(this.area.mx() - area.w / 2, this.area.my() - area.h / 2);

        return area;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Area area = this.getViewport();

        if (area.isInside(context))
        {
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
            return this.panel.getController().zoomOrbit(context.mouseWheel);
        }

        return super.subMouseScrolled(context);
    }

    /**
     * The icon bar can be set to stay out of the way until the mouse comes over the
     * preview. A context menu opened from an icon takes the mouse out of the preview,
     * so while any menu is up the bar keeps the visibility it had when it opened.
     */
    private void updateIconsVisibility(UIContext context)
    {
        if (!BBSSettings.editorPreviewIconsAutoHide.get())
        {
            this.iconsVisible = true;
        }
        else if (!context.hasContextMenu())
        {
            this.iconsVisible = this.area.isInside(context.mouseX, context.mouseY);
        }

        this.icons.setVisible(this.iconsVisible);
    }

    @Override
    public void render(UIContext context)
    {
        Texture texture = BBSRendering.getTexture();
        Area area = this.getViewport();
        Camera camera = this.panel.getCamera();

        camera.copy(this.panel.getWorldCamera());
        camera.view.set(this.panel.lastView);
        camera.projection.set(this.panel.lastProjection);
        context.batcher.flush();

        if (texture != null)
        {
            context.batcher.texturedBox(texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }

        this.hud.begin(area);
        this.hud.reserve(PreviewHud.Anchor.BOTTOM_CENTER, area.ey() - this.icons.area.y);

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

        boolean needGuides = BBSSettings.editorRuleOfThirds.get()
            || BBSSettings.editorCenterLines.get()
            || BBSSettings.editorCrosshair.get();
        if (needGuides)
        {
            if (BBSSettings.editorRuleOfThirds.get())
            {
                int guidesColor = BBSSettings.editorGuidesColor.get();

                context.batcher.box(area.x + area.w / 3 - 1, area.y, area.x + area.w / 3, area.y + area.h, guidesColor);
                context.batcher.box(area.x + area.w - area.w / 3, area.y, area.x + area.w - area.w / 3 + 1, area.y + area.h, guidesColor);

                context.batcher.box(area.x, area.y + area.h / 3 - 1, area.x + area.w, area.y + area.h / 3, guidesColor);
                context.batcher.box(area.x, area.y + area.h - area.h / 3, area.x + area.w, area.y + area.h - area.h / 3 + 1, guidesColor);
            }

            if (BBSSettings.editorCenterLines.get())
            {
                int guidesColor = BBSSettings.editorGuidesColor.get();
                int x = area.mx();
                int y = area.my();

                context.batcher.box(area.x, y, area.ex(), y + 1, guidesColor);
                context.batcher.box(x, area.y, x + 1, area.ey(), guidesColor);
            }

            if (BBSSettings.editorCrosshair.get())
            {
                int x = area.mx() + 1;
                int y = area.my() + 1;

                context.batcher.box(x - 4, y - 1, x + 3, y, Colors.setA(Colors.WHITE, 0.5F));
                context.batcher.box(x - 1, y - 4, x, y + 3, Colors.setA(Colors.WHITE, 0.5F));
            }
        }

        this.placementGizmo.render(context, area);

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

        this.updateIconsVisibility(context);

        if (this.iconsVisible)
        {
            Area a = this.icons.area;

            /* Render icon bar */
            int barShade = BBSSettings.lightSurfaces() ? (Colors.A50 | 0xFFFFFF) : Colors.A50;
            context.batcher.gradientVBox(a.x, a.y, a.ex(), a.ey(), 0, barShade);
        }

        /* Outside the bar's visibility: hiding the icons must not take away the one line
         * that says how to leave control mode. */
        if (this.panel.getController().isControlling())
        {
            String s = UIKeys.FILM_CONTROLLER_CONTROL_MODE_TOOLTIP.format(KeyCodes.getName(Keys.FILM_CONTROLLER_TOGGLE_CONTROL.getMainKey())).get();

            this.hud.label(context, PreviewHud.Anchor.BOTTOM_CENTER, s, Colors.WHITE, Colors.A50);
        }

        /* Everything the corners collected this frame goes down here, each zone's wash first. */
        this.hud.flush(context);

        context.batcher.clip(this.area, context);
        super.render(context);
        context.batcher.unclip(context);
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
