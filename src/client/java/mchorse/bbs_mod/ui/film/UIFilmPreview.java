package mchorse.bbs_mod.ui.film;

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
import mchorse.bbs_mod.graphics.GuiQuadMesh;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.film.controller.UIMotionPathContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIOnionSkinContextMenu;
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
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UIFilmPreview extends UIElement
{
    private List<AudioClip> clips = new ArrayList<>();
    private UIFilmPanel panel;

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

    public UIFilmPreview(UIFilmPanel filmPanel)
    {
        this.panel = filmPanel;

        this.icons = UI.row(0, 0);
        this.icons.row().resize();
        this.icons.relative(this).x(0.5F).y(1F).anchor(0.5F, 1F);

        /* Preview buttons */
        this.onionSkin = new UIIcon(Icons.ONION_SKIN, (b) -> this.openOnionSkin());
        this.onionSkin.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE);
        this.motionPath = new UIIcon(Icons.CURVES, (b) -> this.openMotionPath());
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
        this.flight.tooltip(UIKeys.CAMERA_EDITOR_KEYS_MODES_FLIGHT);
        this.control = new UIIcon(Icons.POSE, (b) -> this.panel.getController().toggleControl());
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
        this.recordReplay.tooltip(UIKeys.FILM_REPLAY_RECORD);
        this.recordReplay.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_INSTANT_KEYFRAMES, this.panel.getController().isInstantKeyframes(), () ->
            {
                this.panel.getController().toggleInstantKeyframes();
            });

            menu.action(Icons.MOVE_TO, UIKeys.FILM_REPLAY_TELEPORT_TO_PLAYER, () -> this.panel.getController().insertPlayerFrame());
        });
        this.recordVideo = new UIIcon(Icons.VIDEO_CAMERA, (b) ->
        {
            if (this.panel.checkShowNoCamera())
            {
                return;
            }

            if (!FFMpegUtils.checkFFMPEG())
            {
                UIMessageOverlayPanel panel = new UIMessageOverlayPanel(UIKeys.GENERAL_WARNING, UIKeys.GENERAL_FFMPEG_ERROR_DESCRIPTION);
                UIIcon guide = new UIIcon(Icons.HELP, (bb) -> UIUtils.openWebLink(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE_LINK.get()));

                guide.tooltip(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE, Direction.LEFT);
                panel.icons.add(guide);

                UIOverlay.addOverlay(this.getContext(), panel);

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
                UIOverlay.addOverlay(this.getContext(), panel, 430, 380);
            });

            menu.action(Icons.VIDEO_CAMERA, UIKeys.FILM_RENDER_QUEUE, this::exportQueueFromTabs);
            menu.action(Icons.SOUND, UIKeys.FILM_RENDER_AUDIO, this::renderAudio);
            menu.action(Icons.REFRESH, UIKeys.FILM_RESET_REPLAYS, this.panel.recorder.resetReplays, () ->
            {
                this.panel.recorder.resetReplays = !this.panel.recorder.resetReplays;
            });
        });

        this.icons.add(this.onionSkin, this.motionPath, this.plause, this.teleport, this.flight, this.control, this.perspective, this.recordReplay, this.recordVideo);
        this.add(this.icons);
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
        if (this.panel.checkShowNoCamera())
        {
            return;
        }

        if (!FFMpegUtils.checkFFMPEG())
        {
            UIMessageOverlayPanel panel = new UIMessageOverlayPanel(UIKeys.GENERAL_WARNING, UIKeys.GENERAL_FFMPEG_ERROR_DESCRIPTION);
            UIIcon guide = new UIIcon(Icons.HELP, (bb) -> UIUtils.openWebLink(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE_LINK.get()));

            guide.tooltip(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE, Direction.LEFT);
            panel.icons.add(guide);

            UIOverlay.addOverlay(this.getContext(), panel);

            return;
        }

        this.panel.startQueueExportFromOpenTabs();
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
            if (this.panel.getController().orbitGizmo.mouseClicked(context, area))
            {
                return true;
            }

            return this.panel.replayEditor.clickViewport(context, area);
        }

        return super.subMouseClicked(context);
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

    @Override
    public void render(UIContext context)
    {
        Texture texture = BBSRendering.getTexture();
        Area area = this.getViewport();
        Camera camera = this.panel.getCamera();

        camera.copy(this.panel.getWorldCamera());

        /* 1.21.11 port: in 1.21.1 UIFilmPanel.renderInWorld() captured the actual render matrices into
         * lastProjection (RenderSystem.getProjectionMatrix) and lastView (WorldRenderContext.positionMatrix),
         * and getCamera() carried them for the RMB world-pick ray (CameraUtils.getMouseDirection in
         * UIReplaysEditor.clickViewport). Those RenderSystem/WorldRenderContext APIs were removed in 1.21.11,
         * so that capture is stubbed and lastView/lastProjection stay identity — which made the click ray map
         * to the wrong world position. Reconstruct the same matrices from the live world-camera state instead:
         * the perspective projection matches the off-screen render aspect (video width/height, identical to
         * getViewport()) and updateView() rebuilds the rotation-only view from the world camera's rotation. */
        camera.updatePerspectiveProjection(BBSRendering.getVideoWidth(), BBSRendering.getVideoHeight());
        camera.updateView();
        context.batcher.flush();

        if (texture != null)
        {
            context.batcher.texturedBox(texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }

        /* The navigation ball replaces the axes crosshair in the corner */
        if (!this.panel.getController().orbitGizmo.isActive())
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

        /* Current window resolution label (bottom-right, same style as replay name) */
        int resW = BBSRendering.getVideoWidth();
        int resH = BBSRendering.getVideoHeight();
        String resLabel = resW + " × " + resH;
        int resLabelW = context.batcher.getFont().getWidth(resLabel);
        int resLabelH = context.batcher.getFont().getHeight();
        int resX = area.ex() - 4;
        int resY = area.ey() - resLabelH - 5;
        context.batcher.textCard(resLabel, resX - resLabelW, resY, Colors.WHITE, Colors.A50);

        this.panel.getController().renderHUD(context, area);

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

        Area a = this.icons.area;

        /* Render icon bar */
        int barShade = BBSSettings.isLightTheme() ? (Colors.A50 | 0xFFFFFF) : Colors.A50;
        context.batcher.gradientVBox(a.x, a.y, a.ex(), a.ey(), 0, barShade);

        if (this.panel.isFlying()) UIDashboardPanels.renderHighlight(context.batcher, this.flight.area, Direction.BOTTOM);
        if (this.panel.getController().isControlling()) UIDashboardPanels.renderHighlight(context.batcher, this.control.area, Direction.BOTTOM);
        if (this.panel.getController().isRecording()) UIDashboardPanels.renderHighlight(context.batcher, this.recordReplay.area, Direction.BOTTOM);
        if (this.panel.recorder.isRecording()) UIDashboardPanels.renderHighlight(context.batcher, this.recordVideo.area, Direction.BOTTOM);
        if (this.panel.getController().getOnionSkin().enabled.get()) UIDashboardPanels.renderHighlight(context.batcher, this.onionSkin.area, Direction.BOTTOM);
        if (this.panel.getController().getMotionPath().enabled.get()) UIDashboardPanels.renderHighlight(context.batcher, this.motionPath.area, Direction.BOTTOM);
        if (this.panel.getController().isControlling())
        {
            String s = UIKeys.FILM_CONTROLLER_CONTROL_MODE_TOOLTIP.format(KeyCodes.getName(Keys.FILM_CONTROLLER_TOGGLE_CONTROL.getMainKey())).get();
            int w = context.batcher.getFont().getWidth(s);
            int height = context.batcher.getFont().getHeight();

            context.batcher.textCard(s, a.mx(w), a.y - height - 5);
        }

        context.batcher.clip(this.area, context);
        super.render(context);
        context.batcher.unclip(context);
    }

    private void renderCursor(UIContext context)
    {
        /* The camera-orientation crosshair in the preview corner. 1.21.1 leaned on
         * RenderSystem.renderCrosshair (removed): three axis lines under the camera's pitch/yaw.
         * Rebuilt as recorded GUI quads (GuiQuadMesh, the orbit nav-sphere's own mechanism): the
         * endpoints are the rotated axes projected orthographically, drawn far-to-near. */
        net.minecraft.client.render.Camera mcCamera = MinecraftClient.getInstance().gameRenderer.getCamera();

        float cx = this.area.x + 16;
        float cy = this.area.ey() - 12;

        /* The 1.21.1 modelview: rotX(-pitch) * rotY(yaw) * scale(-1,-1,-1), GUI y-down. */
        Matrix4f m = new Matrix4f()
            .rotateX(MathUtils.toRad(-mcCamera.getPitch()))
            .rotateY(MathUtils.toRad(mcCamera.getYaw()))
            .scale(-1F, -1F, -1F);

        float[][] axes = {{10F, 0F, 0F}, {0F, 10F, 0F}, {0F, 0F, 10F}};
        int[] colors = {0xFFFF3333, 0xFF33FF33, 0xFF3333FF};
        Vector3f[] ends = new Vector3f[3];
        Integer[] order = {0, 1, 2};

        for (int i = 0; i < 3; i++)
        {
            ends[i] = m.transformDirection(new Vector3f(axes[i][0], axes[i][1], axes[i][2]));
        }

        /* Far-to-near, so the axis pointing at the viewer reads on top. */
        java.util.Arrays.sort(order, (a, b) -> Float.compare(ends[a].z, ends[b].z));

        Matrix3x2fc matrix = context.batcher.getContext().getMatrices();
        GuiQuadMesh builder = new GuiQuadMesh();

        for (int i : order)
        {
            float ex = ends[i].x;
            float ey = ends[i].y;
            float length = (float) Math.sqrt(ex * ex + ey * ey);

            if (length < 0.001F)
            {
                continue;
            }

            /* A 1px-thick quad from the centre to the endpoint. */
            float px = -ey / length * 0.5F;
            float py = ex / length * 0.5F;
            int color = colors[i];

            builder.vertex(matrix, cx - px, cy - py).color(color);
            builder.vertex(matrix, cx + px, cy + py).color(color);
            builder.vertex(matrix, cx + ex + px, cy + ey + py).color(color);
            builder.vertex(matrix, cx + ex - px, cy + ey - py).color(color);
        }

        if (!builder.isEmpty())
        {
            context.batcher.drawQuadMesh(builder);
        }
    }
}
