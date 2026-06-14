package mchorse.bbs_mod.ui.particles;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.forms.renderers.ParticleFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.DataTab;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UIDataTabs;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.layout.ILayoutSource;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextEditor;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeAppearanceSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeCollisionSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeCurvesSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeExpirationSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeGeneralSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeInitializationSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeLifetimeSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeLightingSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeMotionSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeRotationSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeRateSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeShapeSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeSpaceSection;
import mchorse.bbs_mod.ui.particles.utils.MolangSyntaxHighlighter;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.presets.PresetManager;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class UIParticleSchemePanel extends UIDataDashboardPanel<ParticleScheme>
{
    /**
     * Default particle placeholder that comes with the engine.
     */
    public static final Link PARTICLE_PLACEHOLDER = Link.assets("particles/default_placeholder.json");

    public UITextEditor textEditor;
    public UIParticleSchemeRenderer renderer;
    public UIScrollView generalView;
    public UIScrollView emitterView;
    public UIScrollView particleView;
    public UIScrollView appearanceView;
    public UIDockLayout dock;
    public UIParticleSelectionPanel selectionPanel;

    public List<UIParticleSchemeSection> sections = new ArrayList<>();

    private UICopyPasteController layoutPresetsController;
    private String molangId;

    public UIParticleSchemePanel(UIDashboard dashboard)
    {
        super(dashboard);
        this.enableTabs();

        this.renderer = new UIParticleSchemeRenderer();

        this.textEditor = new UITextEditor(null).highlighter(new MolangSyntaxHighlighter());
        this.textEditor.background();

        this.generalView = this.createSectionView();
        this.emitterView = this.createSectionView();
        this.particleView = this.createSectionView();
        this.appearanceView = this.createSectionView();

        /* Dockable layout: section groups (tabbed), MoLang and 3D preview each their own panel,
         * sharing the docking system with the film editor. */
        this.dock = new UIDockLayout();
        this.dock.relative(this.editor).w(1F).h(1F);
        this.dock.source(this.createLayoutSource())
            .frameless("preview")
            .gate(() -> this.data != null);
        this.dock.addPanel("general", this.wrapScroll(this.generalView), Icons.GEAR);
        this.dock.addPanel("emitter", this.wrapScroll(this.emitterView), Icons.BUBBLE);
        this.dock.addPanel("particle", this.wrapScroll(this.particleView), Icons.PARTICLE);
        this.dock.addPanel("appearance", this.wrapScroll(this.appearanceView), Icons.MATERIAL);
        this.dock.addPanel("molang", this.textEditor, Icons.CODE);
        this.dock.addPanel("preview", this.renderer, Icons.VIDEO_CAMERA);
        this.dock.mount();
        this.editor.add(this.dock);

        this.selectionPanel = new UIParticleSelectionPanel(this);
        this.selectionPanel.relative(this).y(UIDataTabs.TABS_HEIGHT_PX).wTo(this.iconBar.area).h(1F, -UIDataTabs.TABS_HEIGHT_PX);
        this.add(this.selectionPanel);

        this.overlay.namesList.setFileIcon(Icons.PARTICLE);

        UIIcon restart = new UIIcon(Icons.TRASH, (b) ->
        {
            this.renderer.setScheme(this.data);
        });
        restart.tooltip(UIKeys.SNOWSTORM_RESTART_EMITTER, Direction.LEFT);

        this.iconBar.add(restart);

        this.layoutPresetsController = new UICopyPasteController(PresetManager.PARTICLE_LAYOUTS, "_CopyParticleLayout")
            .supplier(this::getLayoutPresetData)
            .consumer(this::applyLayoutFromPreset);

        UIIcon presets = new UIIcon(Icons.LAYOUT, (b) ->
        {
            UIContext context = this.getContext();

            this.layoutPresetsController.openPresets(context, context.mouseX, context.mouseY);
        });
        presets.tooltip(UIKeys.FILM_LAYOUT_PRESETS, Direction.LEFT);

        UIIcon lock = new UIIcon(() -> this.dock.isLocked() ? Icons.LOCKED : Icons.UNLOCKED, (b) -> this.dock.toggleLock());
        lock.tooltip(() -> (this.dock.isLocked() ? UIKeys.FILM_LAYOUT_UNLOCK : UIKeys.FILM_LAYOUT_LOCK).get(), Direction.LEFT);

        UIIcon resetLayout = new UIIcon(Icons.REFRESH, (b) -> this.dock.resetLayout());
        resetLayout.tooltip(UIKeys.FILM_LAYOUT_RESET, Direction.LEFT);

        this.iconBar.add(presets);
        this.iconBar.add(lock);
        this.iconBar.add(resetLayout);

        /* Ctrl+Tab / Ctrl+Shift+Tab cycle the tabs of the dock stack under the cursor (like the film editor). */
        this.keys().register(Keys.FILM_CONTROLLER_NEXT_DOCK_TAB, () ->
        {
            if (this.dock.cycleDockStackTab(1))
            {
                UIUtils.playClick();
            }
        }).category(UIKeys.SNOWSTORM_TITLE);
        this.keys().register(Keys.FILM_CONTROLLER_PREV_DOCK_TAB, () ->
        {
            if (this.dock.cycleDockStackTab(-1))
            {
                UIUtils.playClick();
            }
        }).category(UIKeys.SNOWSTORM_TITLE);

        /* General tab */
        this.addSection(this.generalView, new UIParticleSchemeGeneralSection(this));
        this.addSection(this.generalView, new UIParticleSchemeCurvesSection(this));
        this.addSection(this.generalView, new UIParticleSchemeSpaceSection(this));
        this.addSection(this.generalView, new UIParticleSchemeInitializationSection(this));
        /* Emitter tab */
        this.addSection(this.emitterView, new UIParticleSchemeRateSection(this));
        this.addSection(this.emitterView, new UIParticleSchemeLifetimeSection(this));
        this.addSection(this.emitterView, new UIParticleSchemeShapeSection(this));
        /* Particle tab */
        UIParticleSchemeMotionSection motionSection = new UIParticleSchemeMotionSection(this);
        UIParticleSchemeRotationSection rotationSection = new UIParticleSchemeRotationSection(this);

        motionSection.link(rotationSection);
        rotationSection.link(motionSection);

        this.addSection(this.particleView, motionSection);
        this.addSection(this.particleView, rotationSection);
        this.addSection(this.particleView, new UIParticleSchemeExpirationSection(this));
        /* Appearance tab */
        this.addSection(this.appearanceView, new UIParticleSchemeAppearanceSection(this));
        this.addSection(this.appearanceView, new UIParticleSchemeLightingSection(this));
        this.addSection(this.appearanceView, new UIParticleSchemeCollisionSection(this));

        this.fill(null);
    }

    public void editMoLang(String id, Consumer<String> callback, MolangExpression expression)
    {
        /* The MoLang editor is its own dock panel (always present); editing just swaps its target. */
        this.molangId = id;
        this.textEditor.callback = callback;
        this.textEditor.setText(expression == null ? "" : expression.toString());
    }

    @Override
    protected IKey getTitle()
    {
        return UIKeys.SNOWSTORM_TITLE;
    }

    @Override
    public ContentType getType()
    {
        return ContentType.PARTICLES;
    }

    @Override
    public Icon getTabIcon(DataTab tab)
    {
        return tab != null && tab.dataId == null ? Icons.SEARCH : Icons.PARTICLE;
    }

    public void dirty()
    {
        this.renderer.emitter.setupVariables();
    }

    /**
     * Rebuild the preview emitter from scratch. Needed after structural changes (e.g. switching a
     * motion axis between dynamic and parametric), since already-spawned particles keep the manual
     * flags from their spawn and would otherwise lag behind the new mode.
     */
    public void restartEmitter()
    {
        if (this.data != null)
        {
            this.renderer.setScheme(this.data);
        }
    }

    private MapType getLayoutPresetData()
    {
        MapType data = new MapType();

        data.put("particle_layout", this.dock.getLayoutRoot().toData());

        return data;
    }

    private void applyLayoutFromPreset(MapType data, int mouseX, int mouseY)
    {
        BaseType layoutData = data.get("particle_layout");

        if (layoutData == null)
        {
            return;
        }

        this.dock.applyLayoutRoot(EditorLayoutNode.fromData(layoutData));
    }

    private ILayoutSource createLayoutSource()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        return new ILayoutSource()
        {
            @Override
            public BaseValue value()
            {
                return layout;
            }

            @Override
            public EditorLayoutNode getRoot()
            {
                return layout.getParticleLayoutRoot();
            }

            @Override
            public void setRoot(EditorLayoutNode root)
            {
                layout.setParticleLayoutRoot(root);
            }

            @Override
            public List<EditorLayoutNode.SplitterNode> getSplitters()
            {
                return layout.getParticleSplitters();
            }

            @Override
            public List<EditorLayoutNode.SplitterNode> getSplittersForWrite()
            {
                return layout.getParticleSplittersForWrite();
            }

            @Override
            public EditorLayoutNode getDefault()
            {
                return EditorLayoutNode.defaultParticleLayout();
            }
        };
    }

    private UIScrollView createSectionView()
    {
        UIScrollView view = UI.scrollView(20, 10);
        view.scroll.cancelScrolling().opposite().scrollSpeed *= 3;

        return view;
    }

    /**
     * Wrap a content element (column/scroll layout) in a plain container before docking it.
     * The dock resets each panel's flex (which would wipe a column layout's {@code flex.post}),
     * so the actual content must live one level down where the dock never touches it.
     */
    private UIElement wrapScroll(UIElement content)
    {
        UIElement panel = new UIElement();

        content.relative(panel).w(1F).h(1F);
        panel.add(content);

        return panel;
    }

    private void addSection(UIScrollView view, UIParticleSchemeSection section)
    {
        this.sections.add(section);
        view.add(section);
    }

    @Override
    protected void fillData(ParticleScheme data)
    {
        this.editMoLang(null, null, null);

        this.selectionPanel.setVisible(data == null);

        if (this.data != null)
        {
            this.renderer.setScheme(this.data);

            for (UIParticleSchemeSection section : this.sections)
            {
                section.setScheme(this.data);
            }

            this.generalView.resize();
            this.emitterView.resize();
            this.particleView.resize();
            this.appearanceView.resize();
        }
        else
        {
            this.renderer.setScheme(null);
        }

        /* Dock gate shows/hides the preview + sections panels based on data presence. */
        this.dock.setupFlex(true);
    }

    @Override
    public void fillNames(Collection<String> names)
    {
        super.fillNames(names);

        if (this.selectionPanel != null)
        {
            this.selectionPanel.fillNames(names);
        }
    }

    @Override
    public void forceSave()
    {
        super.forceSave();

        ParticleFormRenderer.lastUpdate = System.currentTimeMillis();
    }

    @Override
    public void fillDefaultData(ParticleScheme data)
    {
        super.fillDefaultData(data);

        try (InputStream asset = BBSMod.getProvider().getAsset(PARTICLE_PLACEHOLDER))
        {
            MapType map = DataToString.mapFromString(IOUtils.readText(asset));

            ParticleScheme.PARSER.fromData(data, map);
        }
        catch (Exception e)
        {}
    }

    @Override
    public void appear()
    {
        super.appear();

        this.textEditor.updateHighlighter();
    }

    @Override
    protected boolean shouldAutoOpenListOnFirstResize()
    {
        return false;
    }

    @Override
    public void close()
    {
        if (this.renderer.emitter != null)
        {
            this.renderer.emitter.particles.clear();
        }
    }

    @Override
    public void resize()
    {
        super.resize();

        if (this.dock != null)
        {
            this.dock.setupFlex(true);
        }
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        if (this.iconBar.isVisible())
        {
            int bg = this.selectionPanel != null && this.selectionPanel.isVisible() ? Colors.A100 : Colors.A50;

            this.iconBar.area.render(context.batcher, bg);
            context.batcher.gradientHBox(this.iconBar.area.x - 6, this.iconBar.area.y, this.iconBar.area.x, this.iconBar.area.ey(), 0, 0x29000000);
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.molangId != null)
        {
            FontRenderer font = context.batcher.getFont();
            int w = font.getWidth(this.molangId);

            context.batcher.textCard(this.molangId, this.textEditor.area.ex() - 6 - w, this.textEditor.area.ey() - 6 - font.getHeight());
        }
    }
}