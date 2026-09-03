package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.film.utils.undo.UIUndoHistoryOverlay;
import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.onboarding.TourAnchors;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UISplitter;
import mchorse.bbs_mod.ui.framework.elements.utils.UIUndoKeys;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Model Editor — a proper data panel (tabs, right icon bar, save) over models. Each tab is an open model;
 * the picker in the icon bar chooses one. The editor area is the preview, with a resizable pane of
 * settings beside it. Models are assets, so create/rename/delete are intentionally off.
 *
 * <p>The pane holds one of the panel's two editors ({@link Editor}), picked by the first buttons of
 * the action bar the way the film panel picks between its camera and replay editors: the config
 * editor over everything the model's {@link ModelConfig} says, and the model editor over the model
 * itself. They share the one preview.</p>
 *
 * <p>What each editor is made of is its own ({@link UIModelConfigEditor}); the panel keeps what they
 * both work on and against: the open config, the live {@link ModelInstance} behind it, the undo over
 * all of it, and the preview ({@link UIModelEditorRenderer}) — which shows what the open editor asks
 * for, with whatever that editor puts on the gizmo.</p>
 */
public class UIModelEditorPanel extends UIDataDashboardPanel<ModelConfig>
{
    /** What the pane is showing: the config of the model, or the model itself. */
    public enum Editor
    {
        CONFIG(Icons.SETTINGS, UIKeys.MODEL_EDITOR_OPEN_CONFIG_EDITOR),
        MODEL(Icons.SHAPES, UIKeys.MODEL_EDITOR_OPEN_MODEL_EDITOR);

        public final Icon icon;
        public final IKey label;

        Editor(Icon icon, IKey label)
        {
            this.icon = icon;
            this.label = label;
        }
    }

    private static final Editor[] EDITORS = Editor.values();

    /** The editor that was open last; kept across models and across leaving and re-entering the panel. */
    private static Editor lastEditor = Editor.CONFIG;

    /** The pane beside the preview, holding whichever editor is open. */
    public UIElement pane;
    public UIModelEditorRenderer renderer;
    public UISplitter splitter;

    /** The editors themselves, in the order of their buttons. */
    private final UIElement[] editors = new UIElement[EDITORS.length];
    private final UIIcon[] editorIcons = new UIIcon[EDITORS.length];

    /** The config editor, the first of them — kept by its own type for what the panel asks of it. */
    private UIModelConfigEditor configEditor;

    private final ModelForm form = new ModelForm();

    /** The model id waiting for its instance to load (models load asynchronously). */
    private String pendingId;

    /** The live model instance the editors are bound to; null until it loads. */
    private ModelInstance bound;

    /** Thumbnail in the preview's corner showing the model as it appears in UI slots (form pickers). */
    private UIElement miniPreview;

    private UIIcon folderIcon;
    private UIIcon historyIcon;
    private UIIcon animationIcon;

    /** Undo/redo: one handler for the panel's lifetime, its stack cleared per tab switch. */
    private UIFormUndoHandler undoHandler;

    /** Each distinct config instance gets the undo pre-callback registered exactly once. */
    private final Set<ModelConfig> hookedConfigs = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Keep the undo stack through the next fill — a live reload re-bind, not a navigation. */
    private boolean preserveUndo;

    public UIModelEditorPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.pane = new UIElement();

        /* What the tour of this panel points at; the pane's parts are built further down */
        TourAnchors.register("model_editor.preview", () -> this.renderer);
        TourAnchors.register("model_editor.settings", () -> this.pane);
        TourAnchors.register("model_editor.bones", () -> this.configEditor.getTab(UIModelConfigEditor.Tab.BONES));

        this.renderer = new UIModelEditorRenderer()
            .target(this::shownTarget)
            .onBoneClick(this::selectBone);
        this.renderer.form = this.form;

        /* Two panes: the preview and, to its right, the settings — each keeping at least 160px. */
        this.splitter = new UISplitter("model_editor.split", false, 280).fromEnd();
        this.splitter.measure(this.editor).range(160, () -> (float) (this.editor.area.w - 160)).onChange(() ->
        {
            this.layoutPanes();
            this.resize();
        });

        this.createEditors();
        this.layoutPanes();

        this.miniPreview = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                int x1 = this.area.x;
                int y1 = this.area.y;
                int x2 = this.area.ex();
                int y2 = this.area.ey();

                context.batcher.box(x1, y1, x2, y2, BBSSettings.deepSurface());
                FormUtilsClient.renderUI(UIModelEditorPanel.this.form, context, x1, y1, x2, y2);
                context.batcher.outline(x1, y1, x2, y2, Colors.setA(Colors.WHITE, 0.2F));

                super.render(context);
            }

            /* It's a thumbnail — swallow clicks so they don't orbit the main viewport underneath. */
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                return this.area.isInside(context);
            }
        };
        this.miniPreview.relative(this.renderer).x(1F, -6).y(6).wh(64, 64).anchor(1F, 0F);
        this.renderer.add(this.miniPreview);

        this.editor.add(this.pane, this.renderer, this.splitter);

        this.showEditor(lastEditor);
        this.syncPreview();

        this.openOverlay.tooltip(UIKeys.FORMS_EDITOR_MODEL_PICK_MODEL);

        this.folderIcon = new UIIcon(Icons.FOLDER, (b) -> this.openModelFolder());
        this.folderIcon.tooltip(UIKeys.FORMS_CATEGORIES_CONTEXT_OPEN_MODEL_FOLDER);

        this.historyIcon = new UIIcon(Icons.UNDO, (b) -> this.openHistory());
        this.historyIcon.tooltip(UIKeys.MODEL_EDITOR_OPEN_HISTORY);

        this.animationIcon = new UIIcon(Icons.PLAY, (b) -> this.openAnimations());
        this.animationIcon.tooltip(UIKeys.MODEL_EDITOR_ANIMATION_PLAY);

        for (Editor pick : EDITORS)
        {
            UIIcon icon = new UIIcon(pick.icon, (b) -> this.openEditor(pick));

            icon.tooltip(pick.label);
            this.editorIcons[pick.ordinal()] = icon;
            this.actions().editor(icon, () -> lastEditor == pick);
        }

        this.actions()
            .action(this.folderIcon)
            .action(this.historyIcon)
            .action(this.animationIcon);

        this.mountLanding();

        this.add(new UIUndoKeys(this::undo, this::redo).full(this));

        this.registerKeybinds();

        this.fill(null);
    }

    /* Editors. Both fill the pane and only one is shown, the way the film panel switches between its
     * camera and replay editors; the preview beside them is shared. */

    /**
     * The two editors, one over the other in the pane. The model editor is an empty pane for now —
     * the model's own things (its groups, its anchor) are what goes into it next.
     */
    private void createEditors()
    {
        this.configEditor = new UIModelConfigEditor(this);
        this.editors[Editor.CONFIG.ordinal()] = this.configEditor;
        this.editors[Editor.MODEL.ordinal()] = new UIElement();

        for (Editor pick : EDITORS)
        {
            UIElement element = this.editorPane(pick);

            element.full(this.pane);
            this.pane.add(element);
        }
    }

    private UIElement editorPane(Editor editor)
    {
        return this.editors[editor.ordinal()];
    }

    /** Open an editor: it's the one shown, and the preview follows it. */
    private void openEditor(Editor editor)
    {
        this.showEditor(editor);
        this.refreshPreview();
    }

    private void showEditor(Editor editor)
    {
        lastEditor = editor;

        for (Editor pick : EDITORS)
        {
            this.editorPane(pick).setVisible(pick == editor);
        }
    }

    /**
     * What the preview shows follows the open editor: the config editor has an opinion per page, and
     * the model editor is about the model itself, so it gets the plain model — nothing worn, nothing
     * held, the orbit view.
     */
    public void syncPreview()
    {
        if (lastEditor == Editor.CONFIG)
        {
            this.configEditor.applyPreview();

            return;
        }

        this.renderer.setFirstPerson(false);
        this.renderer.setEquipment(false, false);
        this.renderer.getEntity().setSneaking(false);
    }

    /** The same, for a change that moves things: the first-person view letterboxes the preview. */
    public void refreshPreview()
    {
        this.syncPreview();
        this.layoutPanes();
        this.resize();
    }

    /** The live instance of the open model, or null until it loads; a watchdog reload swaps it. */
    public ModelInstance getInstance()
    {
        return this.bound;
    }

    /** What the viewport gizmo is on — the config editor's, and nothing while the model editor is up. */
    private ModelSlotTarget shownTarget()
    {
        return lastEditor == Editor.CONFIG ? this.configEditor.shownTarget() : null;
    }

    /**
     * A bone clicked in the viewport goes to the config editor, which picks it where a bone is picked;
     * outside of it the click is left alone, so the orbit starts.
     */
    private boolean selectBone(String bone)
    {
        return lastEditor == Editor.CONFIG && this.configEditor.selectBone(bone);
    }

    /**
     * The preview with the settings pane to its right. In the first-person view the preview becomes
     * the game's frame: a rectangle of the game window's proportions, letterboxed into the room
     * before the settings — the hand sits where the game puts it (the lower right of the window),
     * which a narrower frame would cut off, and a wider one would misplace.
     */
    private void layoutPanes()
    {
        int splitWidth = this.splitter.getPixels();

        this.pane.relative(this.editor).x(1F, -splitWidth).y(0).w(splitWidth).h(1F);
        this.splitter.relative(this.editor).x(1F, -splitWidth).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

        if (!this.renderer.isFirstPerson())
        {
            this.renderer.relative(this.editor).x(0).y(0).w(1F, -splitWidth).h(1F);

            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        float aspect = mc.getWindow().getFramebufferWidth() / (float) Math.max(1, mc.getWindow().getFramebufferHeight());
        int roomW = Math.max(1, this.editor.area.w - splitWidth);
        int roomH = Math.max(1, this.editor.area.h);
        int w = roomW;
        int h = Math.round(w / aspect);

        if (h > roomH)
        {
            h = roomH;
            w = Math.round(h * aspect);
        }

        this.renderer.relative(this.editor).x((roomW - w) / 2).y((roomH - h) / 2).w(w).h(h);
    }

    /**
     * The first-person frame is sized in pixels off the editor's area, so it's laid out again after
     * every pass.
     */
    @Override
    public void resize()
    {
        super.resize();

        if (this.renderer.isFirstPerson())
        {
            this.layoutPanes();
            this.renderer.resize();
        }
    }

    /** The panel's own keys; the ones that act on a page are the config editor's. */
    private void registerKeybinds()
    {
        IKey category = UIKeys.MODEL_EDITOR_TITLE;
        Supplier<Boolean> open = () -> this.data != null;

        this.keys().register(Keys.MODEL_EDITOR_FIND_BONE, this::findBone).active(open).category(category);
        this.keys().register(Keys.MODEL_EDITOR_OPEN_HISTORY, this::openHistory).active(open).category(category);
    }

    /** Ctrl+F searches bones, so it opens the editor that has them. */
    private void findBone()
    {
        this.showEditor(Editor.CONFIG);
        this.configEditor.findBone();
    }

    @Override
    public void render(UIContext context)
    {
        /* A fully solid dark backdrop over the whole panel so the dashboard background doesn't show through
         * the settings pane or behind the preview. deepSurface() is the same solid the mini preview uses. */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.deepSurface());

        /* Light inputs on the deep backdrop, the film editor's and the model block's scoping — the
         * sections drop them back to deep on their raised cards themselves. */
        BBSSettings.lightInputs = true;

        try
        {
            super.render(context);
        }
        finally
        {
            BBSSettings.lightInputs = false;
        }
    }

    @Override
    public ContentType getType()
    {
        return ContentType.MODELS;
    }

    @Override
    public Icon getTabIcon(String id)
    {
        return id == null ? Icons.SEARCH : Icons.POSE;
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.MODEL_EDITOR_TITLE;
    }

    @Override
    public IKey getListLabel()
    {
        return UIKeys.MODEL_EDITOR_LANDING_LIST;
    }

    @Override
    public void requestData(String id)
    {
        this.pendingId = id;
        this.tryLoadPending();
    }

    @Override
    public void update()
    {
        super.update();

        this.tryLoadPending();
        this.checkReload();

        if (this.undoHandler != null)
        {
            this.undoHandler.submitUndo();
        }
    }

    /**
     * When the model's files change on disk (e.g. a bone deleted in Blockbench) the watchdog drops the old
     * {@link ModelInstance} and a fresh one loads under the same id. The preview follows it by id every frame,
     * but the editors' widgets are static — built off the instance — so the bone lists keep the old bones
     * until re-entering the tab. Detect the swap and re-bind + refill so they track the reload live.
     *
     * <p>Gated on a model actually being open here ({@code data != null}): switching to a new tab first
     * auto-saves the model we're leaving, whose {@code config.json} write trips the same watchdog reload —
     * without this gate that reload would yank the old model back over the fresh tab's empty picker.
     */
    private void checkReload()
    {
        if (this.data == null || this.bound == null || this.pendingId != null)
        {
            return;
        }

        String id = this.form.model.get();

        if (id == null || id.isEmpty())
        {
            return;
        }

        ModelInstance instance = BBSModClient.getModels().getModel(id);

        if (instance != null && instance != this.bound)
        {
            /* A reload (our own 60s periodic save writes config.json too, tripping the watchdog) — keep the
             * undo stack: its commands are path-based and resolve fine against the fresh config, so re-binding
             * shouldn't wipe the user's history every time it autosaves. */
            this.preserveUndo = true;
            this.hookedConfigs.remove(this.data);
            this.bound = instance;
            this.fill(instance.config);
        }
    }

    private void tryLoadPending()
    {
        if (this.pendingId == null)
        {
            return;
        }

        ModelInstance instance = BBSModClient.getModels().getModel(this.pendingId);

        if (instance != null)
        {
            this.bound = instance;
            this.form.model.set(this.pendingId);
            this.pendingId = null;
            this.fill(instance.config);
        }
    }

    /** Models are assets, so the data manager is a pure picker — no create/duplicate/rename/remove. */
    @Override
    protected UICRUDOverlayPanel createOverlayPanel()
    {
        return new UIModelOverlayPanel(this.getTitle(), this, this::pickData);
    }

    @Override
    protected void fillData(ModelConfig data)
    {
        if (data == null)
        {
            /* No model open in this tab — drop the (possibly already watchdog-deleted) instance so neither
             * the preview nor checkReload clings to it while the picker is up. */
            this.bound = null;
        }

        this.setupUndo(data);
        this.configEditor.fill(data);

        boolean open = data != null;

        for (UIIcon icon : this.editorIcons)
        {
            icon.setEnabled(open);
        }

        for (UIIcon icon : new UIIcon[] {this.folderIcon, this.historyIcon, this.animationIcon})
        {
            if (icon != null)
            {
                icon.setEnabled(open);
            }
        }
    }

    private void openHistory()
    {
        if (this.data == null || this.undoHandler == null)
        {
            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIUndoHistoryOverlay(UIKeys.MODEL_EDITOR_HISTORY_TITLE, this.undoHandler.getUndoManager(), () -> this.data, this::afterUndo), 200, 0.6F);
    }

    /**
     * Wire (or reset) undo for the config that just got filled in. The handler is created once and kept —
     * so the pre-callback bound into each config stays valid — while its stack is cleared per tab switch.
     * Each distinct config instance gets the pre-callback registered exactly once (tracked by identity).
     */
    private void setupUndo(ModelConfig data)
    {
        if (data == null)
        {
            return;
        }

        if (this.undoHandler == null)
        {
            this.undoHandler = new UIFormUndoHandler(this);
        }
        else if (!this.preserveUndo)
        {
            /* Reset on real navigation (open / tab switch / pick) but keep it across a live reload re-bind. */
            this.undoHandler.reset();
        }

        this.preserveUndo = false;

        if (this.hookedConfigs.add(data))
        {
            data.preCallback(this.undoHandler::handlePreValues);
        }
    }

    public void undo()
    {
        if (this.data != null && this.undoHandler != null && this.undoHandler.getUndoManager().undo(this.data))
        {
            this.afterUndo();
            UIUtils.playClick();
        }
    }

    public void redo()
    {
        if (this.data != null && this.undoHandler != null && this.undoHandler.getUndoManager().redo(this.data))
        {
            this.afterUndo();
            UIUtils.playClick();
        }
    }

    /**
     * An undo/redo restores config values straight through {@code fromData}, which the static widgets and
     * baked geometry don't track. Re-derive the config's caches, re-bake the instance and refill the pages
     * so the whole editor reflects the restored state.
     */
    private void afterUndo()
    {
        this.data.rebuild();
        this.refresh();
        this.configEditor.fill(this.data);
    }

    private void openModelFolder()
    {
        String id = this.form.model.get();

        if (id != null && !id.isEmpty())
        {
            UIUtils.openFolder(BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + id + "/"));
        }
    }

    /** A menu of the model's animations; picking one plays it once over the idle, like a triggered action does. */
    private void openAnimations()
    {
        if (this.bound == null)
        {
            return;
        }

        List<String> names = new ArrayList<>();

        if (this.bound.animations != null)
        {
            for (Animation animation : this.bound.animations.getAll())
            {
                names.add(animation.id);
            }
        }

        names.sort(String::compareToIgnoreCase);

        this.getContext().replaceContextMenu((menu) ->
        {
            if (names.isEmpty())
            {
                menu.action(Icons.NONE, UIKeys.MODEL_EDITOR_ANIMATION_NONE, () -> {});
            }

            for (String name : names)
            {
                menu.action(Icons.PLAY, IKey.raw(name), () -> this.playAnimation(name));
            }

            menu.action(Icons.REFRESH, UIKeys.MODEL_EDITOR_ANIMATION_RESET, this::resetAnimator);
        });
    }

    private void playAnimation(String name)
    {
        if (FormUtilsClient.getRenderer(this.form) instanceof ModelFormRenderer renderer && renderer.getAnimator() != null)
        {
            renderer.getAnimator().playAnimation(name);
        }
    }

    /**
     * Rebuild the live instance's baked state so a config edit that changed the render path shows in the
     * preview without saving: re-resolve welds + derived caches, re-bake VAOs, and reset the renderer's
     * cached animator (the procedural/non-procedural choice). The plain scalar reads (scale, texture,
     * culling...) already update every frame, so they don't go through here.
     */
    public void refresh()
    {
        if (this.bound == null)
        {
            return;
        }

        this.bound.invalidateWelds();
        this.bound.delete();
        this.bound.setup();
        this.resetAnimator();
    }

    private void resetAnimator()
    {
        if (FormUtilsClient.getRenderer(this.form) instanceof ModelFormRenderer renderer)
        {
            renderer.resetAnimator();
        }
    }

}
