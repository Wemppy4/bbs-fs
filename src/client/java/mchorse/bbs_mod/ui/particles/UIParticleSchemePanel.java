package mchorse.bbs_mod.ui.particles;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
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
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.onboarding.TourAnchors;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.layout.ILayoutSource;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextEditor;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIUndoKeys;
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
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.presets.PresetManager;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    public List<UIParticleSchemeSection> sections = new ArrayList<>();

    private UICopyPasteController layoutPresetsController;
    private String molangId;

    /* Undo by whole-scheme snapshots. The sections write raw component fields — the scheme is a
     * ValueGroup only at its shell, the particle data underneath is plain objects — so the value
     * tree never hears about edits and the shared per-value handler has nothing to hook. Instead
     * the panel serializes the scheme on a timer, and any difference against the last snapshot
     * becomes one entry in the SAME shared UndoManager the other editors use. Schemes are small
     * JSONs, so a check twice a second costs nothing. */
    private UndoManager<ParticleScheme> undoManager;
    private MapType undoSnapshot;
    private final Timer undoCheckTimer = new Timer(400);
    private boolean applyingUndo;

    public UIParticleSchemePanel(UIDashboard dashboard)
    {
        super(dashboard);

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
            .locked(!BBSSettings.editorLayoutSettings.isDockUnlocked(ValueEditorLayout.PARTICLE))
            .frameless("preview")
            .gate(() -> this.data != null);
        this.dock.addPanel("general", this.generalView, Icons.GEAR, UIKeys.SNOWSTORM_PANELS_GENERAL);
        this.dock.addPanel("emitter", this.emitterView, Icons.BUBBLE, UIKeys.SNOWSTORM_PANELS_EMITTER);
        this.dock.addPanel("particle", this.particleView, Icons.PARTICLE, UIKeys.SNOWSTORM_PANELS_PARTICLE);
        this.dock.addPanel("appearance", this.appearanceView, Icons.MATERIAL, UIKeys.SNOWSTORM_PANELS_APPEARANCE);
        this.dock.addPanel("molang", this.textEditor, Icons.CODE, UIKeys.SNOWSTORM_PANELS_MOLANG);
        this.dock.addPanel("preview", this.renderer, Icons.VIDEO_CAMERA, UIKeys.SNOWSTORM_PANELS_PREVIEW);

        /* What the tour of this panel points at. The four section views are one place: they
         * share a stack, and whichever tab is up stands for all of them. */
        TourAnchors.register("particles.preview", () -> this.renderer);
        TourAnchors.register("particles.sections", () -> this.generalView, () -> this.emitterView, () -> this.particleView, () -> this.appearanceView);
        TourAnchors.register("particles.molang", () -> this.textEditor);
        this.dock.mount();
        this.editor.add(this.dock);

        this.mountLanding();

        this.add(new UIUndoKeys(this::undo, this::redo).full(this));

        this.overlay.namesList.setFileIcon(Icons.PARTICLE);

        UIIcon restart = new UIIcon(Icons.TRASH, (b) ->
        {
            this.renderer.setScheme(this.data);
        });
        restart.tooltip(UIKeys.SNOWSTORM_RESTART_EMITTER);

        this.layoutPresetsController = new UICopyPasteController(PresetManager.PARTICLE_LAYOUTS, "_CopyParticleLayout")
            .supplier(this::getLayoutPresetData)
            .consumer(this::applyLayoutFromPreset);

        UIIcon presets = new UIIcon(Icons.LAYOUT, (b) ->
        {
            UIContext context = this.getContext();

            this.layoutPresetsController.openPresets(context, context.mouseX, context.mouseY);
        });
        presets.tooltip(UIKeys.FILM_LAYOUT_PRESETS);

        UIIcon lock = new UIIcon(() -> this.dock.isLocked() ? Icons.LOCKED : Icons.UNLOCKED, (b) -> this.toggleLayoutLock());
        lock.tooltip(() -> (this.dock.isLocked() ? UIKeys.FILM_LAYOUT_UNLOCK : UIKeys.FILM_LAYOUT_LOCK).get());

        UIIcon resetLayout = new UIIcon(Icons.REFRESH, (b) -> this.dock.resetLayout());
        resetLayout.tooltip(UIKeys.FILM_LAYOUT_RESET);

        this.actions()
            .action(restart)
            .action(presets)
            .action(resetLayout)
            .layout(lock, this.dock::isLocked);

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
        this.keys().register(Keys.DOCK_MAXIMIZE, () ->
        {
            if (this.dock.toggleMaximizeUnderCursor())
            {
                UIUtils.playClick();
            }
        }).category(UIKeys.SNOWSTORM_TITLE);
        this.keys().register(Keys.DOCK_UNDO_LAYOUT, () ->
        {
            if (this.dock.undoLayout())
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

        this.onAppear(this.textEditor::updateHighlighter);
        this.onClose(this::clearParticles);
    }

    private void clearParticles()
    {
        if (this.renderer.emitter != null)
        {
            this.renderer.emitter.particles.clear();
        }
    }

    public void editMoLang(String id, Consumer<String> callback, MolangExpression expression)
    {
        /* The MoLang editor is its own dock panel (always present); editing just swaps its target. */
        this.molangId = id;
        this.textEditor.callback = callback;
        this.textEditor.setText(expression == null ? "" : expression.toString());
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.SNOWSTORM_TITLE;
    }

    @Override
    public IKey getCreateLabel()
    {
        return UIKeys.SNOWSTORM_LANDING_NEW;
    }

    @Override
    public IKey getListLabel()
    {
        return UIKeys.SNOWSTORM_LANDING_LIST;
    }

    @Override
    public ContentType getType()
    {
        return ContentType.PARTICLES;
    }

    @Override
    public Icon getTabIcon(String id)
    {
        return id == null ? Icons.SEARCH : Icons.PARTICLE;
    }

    public void dirty()
    {
        this.renderer.emitter.setupVariables();
    }

    @Override
    public void update()
    {
        super.update();

        /* Commit pending edits into the history: any drift of the scheme's serialized form
         * against the last snapshot is one undoable step. Timer-paced, so a burst of typing
         * or dragging groups into steps instead of a keystroke-sized trail. */
        if (this.data != null && this.undoManager != null && this.undoCheckTimer.checkRepeat())
        {
            MapType current = ParticleScheme.toData(this.data);

            if (!current.equals(this.undoSnapshot))
            {
                this.undoManager.pushUndo(new SchemeSnapshotUndo(this.undoSnapshot, current));
                this.undoSnapshot = current;
            }
        }
    }

    public void undo()
    {
        if (this.data != null && this.undoManager != null && this.undoManager.undo(this.data))
        {
            UIUtils.playClick();
        }
    }

    public void redo()
    {
        if (this.data != null && this.undoManager != null && this.undoManager.redo(this.data))
        {
            UIUtils.playClick();
        }
    }

    /**
     * Put the scheme into the given serialized state: parse a fresh scheme (components hold plain
     * fields and molang expressions bound to their parser, so patching the live instance in place
     * is not an option) and re-bind the panel to it through the normal {@link #fill} path — the
     * sections, the preview emitter and the MoLang editor all follow the way they do on open.
     */
    private void applySnapshot(MapType state)
    {
        ParticleScheme fresh = ParticleScheme.parse(state.copy().asMap());

        if (fresh == null)
        {
            return;
        }

        fresh.setId(this.data.getId());

        this.applyingUndo = true;
        this.fill(fresh);
        this.undoSnapshot = ParticleScheme.toData(fresh);
        this.applyingUndo = false;
    }

    /** One undoable step of particle editing: the scheme's serialized form before and after. */
    private class SchemeSnapshotUndo implements IUndo<ParticleScheme>
    {
        private final MapType before;
        private final MapType after;

        public SchemeSnapshotUndo(MapType before, MapType after)
        {
            this.before = before;
            this.after = after;
        }

        @Override
        public IUndo<ParticleScheme> noMerging()
        {
            return this;
        }

        @Override
        public boolean isMergeable(IUndo<ParticleScheme> undo)
        {
            /* The timer pacing in update() is the grouping; entries never merge further. */
            return false;
        }

        @Override
        public void merge(IUndo<ParticleScheme> undo)
        {}

        @Override
        public void undo(ParticleScheme context)
        {
            UIParticleSchemePanel.this.applySnapshot(this.before);
        }

        @Override
        public void redo(ParticleScheme context)
        {
            UIParticleSchemePanel.this.applySnapshot(this.after);
        }
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

    private void toggleLayoutLock()
    {
        this.dock.toggleLock();
        BBSSettings.editorLayoutSettings.setDockUnlocked(ValueEditorLayout.PARTICLE, !this.dock.isLocked());
    }

    private ILayoutSource createLayoutSource()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        return new ILayoutSource()
        {
            @Override
            public EditorLayoutNode getRoot()
            {
                return layout.getLayout(ValueEditorLayout.PARTICLE, EditorLayoutNode::defaultParticleLayout);
            }

            @Override
            public void setRoot(EditorLayoutNode root)
            {
                layout.setLayout(ValueEditorLayout.PARTICLE, root);
            }

            @Override
            public EditorLayoutNode getDefault()
            {
                return EditorLayoutNode.defaultParticleLayout();
            }

            @Override
            public Set<String> getHiddenPanels()
            {
                return layout.getHiddenPanels(ValueEditorLayout.PARTICLE);
            }

            @Override
            public void setHiddenPanels(Set<String> hidden)
            {
                layout.setHiddenPanels(ValueEditorLayout.PARTICLE, hidden);
            }
        };
    }

    private UIScrollView createSectionView()
    {
        UIScrollView view = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);
        view.scroll.cancelScrolling().opposite().scrollSpeed *= 3;

        return view;
    }

    private void addSection(UIScrollView view, UIParticleSchemeSection section)
    {
        this.sections.add(section);
        view.add(section);
    }

    @Override
    protected void fillData(ParticleScheme data)
    {
        /* A fresh scheme starts a fresh history; the re-bind an undo itself performs keeps it. */
        if (!this.applyingUndo)
        {
            this.undoManager = data == null ? null : new UndoManager<>(100);
            this.undoSnapshot = data == null ? null : ParticleScheme.toData(data);
        }

        this.editMoLang(null, null, null);

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
    public void resize()
    {
        super.resize();

        /* The dock re-places its own handles while resizing; only the data gate needs a re-check. */
        if (this.dock != null)
        {
            this.dock.refreshVisibility();
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