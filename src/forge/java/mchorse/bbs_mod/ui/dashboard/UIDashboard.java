package mchorse.bbs_mod.ui.dashboard;

import net.minecraft.client.renderer.GlStateManager;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.OrbitCamera;
import mchorse.bbs_mod.camera.controller.OrbitCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.api.client.events.RegisterDashboardPanelsEvent;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.IFlightSupported;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.dashboard.panels.UIEditorDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureManagerPanel;
import mchorse.bbs_mod.ui.dashboard.utils.UIOrbitCamera;
import mchorse.bbs_mod.ui.dashboard.utils.UIOrbitCameraKeys;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.onboarding.Onboarding;
import mchorse.bbs_mod.ui.onboarding.TourAnchors;
import mchorse.bbs_mod.ui.utility.UIUtilityOverlayPanel;
import mchorse.bbs_mod.ui.utility.audio.UIAudioEditorPanel;
import mchorse.bbs_mod.ui.utils.InterfaceBlur;
import mchorse.bbs_mod.ui.utils.UIChalkboard;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.graphics.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class UIDashboard extends UIBaseMenu
{
    /**
     * The parts of the dashboard that haven't been built yet — the panels, and the add-on
     * event that follows them.
     *
     * <p>Building every panel takes long enough to be felt, and paying for all of it at the
     * moment the user presses the key is what makes the first open feel stuck. So building is
     * queued here instead, for {@link DashboardWarmup} to work through a step at a time while
     * the player is still in the world, and for {@link #finishBuilding()} to flush at once if
     * the user gets there first.</p>
     */
    private final Deque<Runnable> buildSteps = new ArrayDeque<>();

    private UIDashboardPanels panels;

    public UIIcon settings;
    public UIIcon selectors;

    /* Camera data */
    public final UIOrbitCamera orbitUI = new UIOrbitCamera();
    public final UIOrbitCameraKeys orbitKeysUI = new UIOrbitCameraKeys(this);
    public final OrbitCamera orbit = this.orbitUI.orbit;
    public final OrbitCameraController camera = new OrbitCameraController(this.orbit, 5);

    private UISettingsOverlayPanel settingsPanel;
    private int lastPerspective;
    private final java.util.Map<String, UIDashboardPanel> panelIds = new java.util.LinkedHashMap<>();

    private UIChalkboard chalkboard;

    public UIDashboard()
    {
        super();

        this.orbitUI.setControl(true);

        /* Setup panels */
        this.panels = new UIDashboardPanels();
        this.panels.getEvents().register(UIDashboardPanels.PanelEvent.class, (e) ->
        {
            this.orbitUI.setControl(this.panels.isFlightSupported());

            if (this.panels.panel instanceof IFlightSupported panel)
            {
                this.orbit.setFovRoll(panel.supportsRollFOVControl());
            }

            this.copyCurrentEntityCamera();
            Onboarding.panelShown(e.panel);
        });
        this.panels.full(this.viewport);
        this.registerPanels();

        this.main.add(this.panels);

        this.settingsPanel = new UISettingsOverlayPanel();

        this.settings = new UIIcon(Icons.SETTINGS, (b) -> this.openSettings());
        this.chalkboard = new UIChalkboard();
        this.chalkboard.full(this.getRoot());

        /* Pinned through the bar, not into it: the bar is what knows which way its icons point */
        this.panels.pin(this.settings, UIKeys.CONFIG_TITLE);
        for (DashboardPanelRegistry.PinnedAction entry : DashboardPanelRegistry.pinned())
        {
            UIIcon button = new UIIcon(entry.icon, b -> entry.action.accept(this.context));
            this.panels.pin(button, entry.title);
            if (entry.id.equals("selectors")) this.selectors = button;
        }
        this.getRoot().prepend(this.orbitUI);
        this.getRoot().add(this.orbitKeysUI);
        this.getRoot().add(this.chalkboard);

        /* Register keys */
        IKey category = UIKeys.DASHBOARD_CATEGORY;

        this.main.keys().register(Keys.CYCLE_PANELS, this::cyclePanels).category(category);
        this.overlay.keys().register(Keys.TOGGLE_VISIBILITY, () ->
        {
            if (this.panels.panel != null && this.panels.panel.canToggleVisibility())
            {
                this.main.toggleVisible();
                this.resize(this.width, this.height);

                if (!this.main.isVisible())
                {
                    DashboardPanelRegistry.applyExportSize(this.panels.panel);
                }
            }
        }).category(category);
        this.overlay.keys().register(Keys.TOGGLE_DEBUG, () ->
        {
            boolean enabled = !(BBSSettings.ikDebug.enabled.get() || BBSSettings.physicsDebug.enabled.get());

            BBSSettings.ikDebug.enabled.set(enabled);
            BBSSettings.physicsDebug.enabled.set(enabled);
        }).category(category);
        this.overlay.keys().register(Keys.OPEN_SETTINGS, () ->
        {
            if (UIOverlay.has(this.context))
            {
                return;
            }

            this.openSettings();
        }).category(category);
        this.overlay.keys().register(Keys.OPEN_UTILITY_PANEL, () ->
        {
            if (UIOverlay.has(this.context))
            {
                return;
            }

            /* Just tall enough for the panel's own content, and never taller than the screen -
             * an overlay only has its position bounded, so an oversized one gets cut off. */
            int height = Math.min(280, (int) (this.height * 0.9F));

            UIOverlay.addOverlay(this.context, new UIUtilityOverlayPanel(UIKeys.UTILITY_TITLE, null), Math.min(460, (int) (this.width * 0.95F)), height);
        });


        /* What the tour of this screen points at; the landing card belongs to whichever panel is up */
        TourAnchors.register("dashboard.taskbar", () -> this.panels.taskBar);
        TourAnchors.register("dashboard.landing", () -> this.panels.panel instanceof UIEditorDashboardPanel panel && panel.landing != null ? panel.landing.getCard() : null);
    }

    public void openSettings()
    {
        UIOverlay.addOverlay(this.context, this.settingsPanel, 430, 400);
    }

    public void copyCurrentEntityCamera()
    {
        Entity cameraEntity = Minecraft.getMinecraft().getRenderViewEntity();
        if (cameraEntity == null) return;
        Vec3d eyePos = cameraEntity.getPositionEyes(1F);
        Camera camera = new Camera();

        camera.position.set(eyePos.x, eyePos.y, eyePos.z);
        camera.rotation.set(MathUtils.toRad(cameraEntity.rotationPitch), MathUtils.toRad(cameraEntity.getRotationYawHead() - 180), 0);
        camera.fov = MathUtils.toRad(Minecraft.getMinecraft().gameSettings.fovSetting);

        this.orbit.setup(camera);
        this.camera.setup(BBSModClient.getCameraController().camera, 0F);
    }

    private void cyclePanels()
    {
        List<UIDashboardPanel> panels = this.panels.panels;

        if (panels.isEmpty()) return;
        int direction = Window.isShiftPressed() ? -1 : 1;
        int index = panels.indexOf(this.panels.panel);
        int newIndex = MathUtils.cycler(index + direction, panels);

        this.setPanel(panels.get(newIndex));
        UIUtils.playClick();
    }

    public UIDashboardPanels getPanels()
    {
        return this.panels;
    }

    @Override
    public boolean canPause()
    {
        return this.panels.panel != null && this.panels.panel.canPause();
    }

    @Override
    public boolean canRefresh()
    {
        return this.panels.panel != null && this.panels.panel.canRefresh();
    }

    @Override
    public void onOpen(UIBaseMenu oldMenu)
    {
        super.onOpen(oldMenu);

        this.lastPerspective = Minecraft.getMinecraft().gameSettings.thirdPersonView;

        Minecraft.getMinecraft().gameSettings.thirdPersonView = 0;

        if (oldMenu != this)
        {
            this.panels.open();

            /* Which panel is up is a question about the screen being opened, not about it being
             * built — the film panel is simply where a dashboard that has never been opened starts */
            this.setPanel(this.panels.panel == null ? this.defaultPanel() : this.panels.panel);
        }

        BBSModClient.getCameraController().add(this.camera);

        Onboarding.dashboardOpened(this);
    }

    @Override
    public IUIElement getPointerOwner()
    {
        return this.orbitUI.isFreeLook() ? this.orbitUI : null;
    }

    @Override
    public void onClose(UIBaseMenu nextMenu)
    {
        super.onClose(nextMenu);

        if (nextMenu != this)
        {
            this.panels.close();
        }

        this.orbit.reset();

        /* The mouse goes back to being a cursor while the dashboard is away — the flight is
         * kept, and the panel takes the mouse again on the frame it's back on screen. */
        this.orbitUI.setFreeLook(false);

        BBSModClient.getCameraController().remove(this.camera);

        Minecraft.getMinecraft().gameSettings.thirdPersonView = this.lastPerspective;
    }

    @Override
    protected void closeMenu()
    {
        super.closeMenu();

        if (!this.main.isVisible())
        {
            this.main.setVisible(true);
        }
    }

    protected void registerPanels()
    {
        /* These modules are already backed by real native services. Other modules register
         * their own original panels before dashboard warmup, in the original bar order. */
        DashboardPanelRegistry.register("textures", 5, UIKeys.TEXTURES_TOOLTIP, Icons.MATERIAL, UITextureManagerPanel::new);
        DashboardPanelRegistry.register("audio", 6, UIKeys.AUDIO_TITLE, Icons.SOUND, UIAudioEditorPanel::new);
        Onboarding.registerShown(UITextureManagerPanel.class, mchorse.bbs_mod.ui.onboarding.Tours.TEXTURES);
        Onboarding.registerOpened(UIAudioEditorPanel.class, mchorse.bbs_mod.ui.onboarding.Tours.AUDIO);
        for (DashboardPanelRegistry.Entry entry : DashboardPanelRegistry.entries())
        {
            this.buildStep(entry.id, () ->
            {
                UIDashboardPanel panel = entry.factory.apply(this);
                this.panelIds.put(entry.id, panel);
                DashboardPanelRegistry.bind(panel, entry);
                this.panels.registerPanel(panel, entry.title, entry.icon);
            });
        }
        this.buildStep("add-ons", () -> BBSMod.events.post(new RegisterDashboardPanelsEvent(this)));
    }

    public UIDashboardPanel getPanel(String id) { return this.panelIds.get(id); }
    private UIDashboardPanel defaultPanel()
    {
        UIDashboardPanel film = this.panelIds.get("film");
        return film != null ? film : this.panels.panels.isEmpty() ? null : this.panels.panels.get(0);
    }

    /** Queue up a part of the dashboard to be built later — see {@link #buildSteps}. */
    private void buildStep(String name, Runnable step)
    {
        this.buildSteps.add(() ->
        {
            long start = System.nanoTime();

            step.run();

            /* Building is spread out now, but it is still the whole cost of the first open —
             * the log names the part to blame instead of leaving a mystery */
            System.out.println(String.format("Dashboard panel \"%s\" built in %.1f ms", name, (System.nanoTime() - start) / 1_000_000D));
        });
    }

    /**
     * Build one queued part of the dashboard, if anything is left to build. Returns whether
     * there is more after this one.
     */
    public boolean buildNextStep()
    {
        Runnable step = this.buildSteps.poll();

        if (step != null)
        {
            step.run();
        }

        return !this.buildSteps.isEmpty();
    }

    /** Whether the dashboard is built in full, i.e. nothing is queued any more. */
    public boolean isFullyBuilt()
    {
        return this.buildSteps.isEmpty();
    }

    /** Build everything that is still queued, right now — the dashboard is needed this frame. */
    public void finishBuilding()
    {
        while (this.buildNextStep());
    }

    public <T> T getPanel(Class<T> clazz)
    {
        return this.panels.getPanel(clazz);
    }

    public void setPanel(UIDashboardPanel panel)
    {
        this.panels.setPanel(panel);
    }

    @Override
    public void update()
    {
        super.update();

        if (this.panels.panel != null)
        {
            this.panels.panel.update();
        }
    }

    @Override
    protected void preRenderMenu(UIRenderingContext context)
    {
        if (!this.main.isVisible())
        {
            if (this.panels.panel != null)
            {
                this.panels.panel.renderPanelBackground(this.context);
            }

            return;
        }

        if (this.panels.panel != null && this.panels.panel.needsBackground())
        {
            this.background(context);
        }
        else
        {
            context.batcher.gradientVBox(0, 0, this.width, this.height / 8, Colors.A25, 0);
            context.batcher.gradientVBox(0, this.height - this.height / 8, this.width, this.height, 0, Colors.A25);
        }
    }

    private void background(UIRenderingContext context)
    {
        Link background = BBSSettings.backgroundImage.get();
        int color = BBSSettings.backgroundColor.get();

        /* The world shows through the tint (and through the image, tinted) — blur it, unless
         * the tint is solid and there is nothing to see */
        if (background != null || Colors.getA(color) < 1F)
        {
            InterfaceBlur.applyUnder();
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        if (background == null)
        {
            context.batcher.box(0, 0, this.width, this.height, color);
        }
        else
        {
            context.batcher.texturedBox(context.getTextures().getTexture(background), color, 0, 0, this.width, this.height, 0, 0, this.width, this.height, this.width, this.height);
        }
    }

    @Override
    public void startRenderFrame(float tickDelta)
    {
        super.startRenderFrame(tickDelta);

        if (this.panels.panel != null)
        {
            this.panels.panel.startRenderFrame(tickDelta);
        }
    }

    public void renderInWorld(WorldRenderContext context)
    {
        super.renderInWorld(context);

        if (this.panels.panel != null)
        {
            this.panels.panel.renderInWorld(context);
        }
    }
}
