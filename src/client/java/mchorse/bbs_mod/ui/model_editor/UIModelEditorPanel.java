package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.cubic.model.config.ArmorSlotValue;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.model.config.WeldValue;
import mchorse.bbs_mod.cubic.weld.CubeFace;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.film.utils.undo.UIUndoHistoryOverlay;
import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.onboarding.TourAnchors;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UISplitter;
import mchorse.bbs_mod.ui.framework.elements.utils.UITabStrip;
import mchorse.bbs_mod.ui.framework.elements.utils.UITextTab;
import mchorse.bbs_mod.ui.framework.elements.utils.UIUndoKeys;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.presets.PresetManager;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Model Editor — a proper data panel (tabs, right icon bar, save) over models. Each tab is an open model;
 * the picker in the icon bar chooses one. The editor area is a resizable settings pane on the left
 * (binding straight to the live model's {@link ModelConfig}, so edits show in the preview at once)
 * and the preview on the right. Models are assets, so create/rename/delete are intentionally off.
 *
 * <p>The pane holds one of the panel's two editors ({@link Editor}), picked by the first buttons of
 * the action bar the way the film panel picks between its camera and replay editors: the config
 * editor over everything the model's {@link ModelConfig} says, and the model editor over the model
 * itself. They share the one preview.</p>
 *
 * <p>The config editor is a strip of icon tabs over one page at a time ({@link Tab}): the general
 * settings, the bones, the welds, the armor, the held items, the first-person hands, the poses.
 * The page that's open decides what the preview shows — the armor is worn on the armor page, the
 * items held on the items page, the first-person view is the first-person page, the model sneaks
 * on the poses page — so there is nothing to toggle by hand.</p>
 *
 * <p>The pages are built exactly once, in the constructor; opening a model only refills their
 * bodies. That's what keeps the fold state, the scroll position and the focused control alive
 * across an edit — a list add/remove refills just that list's container, never the whole page.</p>
 *
 * <p>The preview ({@link UIModelEditorRenderer}) shows what the config describes, with the picked
 * slot's transform (or the picked pose bone) on a gizmo and the bone a row names lit up under the
 * cursor. Every bone picker has the viewport eyedropper, and every plain field answers a right
 * click with "reset".</p>
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

    /** The pages of the config editor, in the order of their tabs. */
    public enum Tab
    {
        GENERAL(Icons.GEAR, UIKeys.FORMS_EDITORS_GENERAL),
        BONES(Icons.LIMB, UIKeys.MODEL_EDITOR_BONES),
        WELDS(Icons.LINK, UIKeys.MODEL_EDITOR_WELDS),
        ARMOR(Icons.ARMOR_CHESTPLATE, UIKeys.MODEL_EDITOR_ARMOR),
        ITEMS(Icons.HOTBAR, UIKeys.MODEL_EDITOR_ITEMS),
        FIRST_PERSON(Icons.LOOKING, UIKeys.MODEL_EDITOR_FIRST_PERSON),
        POSES(Icons.POSE, UIKeys.MODEL_EDITOR_POSES);

        public final Icon icon;
        public final IKey label;

        Tab(Icon icon, IKey label)
        {
            this.icon = icon;
            this.label = label;
        }
    }

    private static final Editor[] EDITORS = Editor.values();
    private static final Tab[] TABS = Tab.values();

    /** The tab strip's height — the dock stacks' one, since the strip is drawn like theirs. */
    private static final int TABS_HEIGHT = 20;

    /** The editor that was open last; kept across models and across leaving and re-entering the panel. */
    private static Editor lastEditor = Editor.CONFIG;

    /** The page that was open last; kept across models and across leaving and re-entering the panel. */
    private static Tab lastTab = Tab.GENERAL;

    /** The pane beside the preview, holding whichever editor is open. */
    public UIElement pane;
    public UITabStrip tabs;
    public UIModelEditorRenderer renderer;
    public UISplitter splitter;

    /** The editors themselves, in the order of their buttons: the config's tab strip over its pages, the model's own. */
    private final UIElement[] editors = new UIElement[EDITORS.length];
    private final UIIcon[] editorIcons = new UIIcon[EDITORS.length];

    private final UIScrollView[] pages = new UIScrollView[TABS.length];

    private final ModelForm form = new ModelForm();

    /** The model id waiting for its instance to load (models load asynchronously). */
    private String pendingId;

    private static final CubeFace[] FACES = CubeFace.values();

    /** The live model instance the pages are bound to; null until it loads. */
    private ModelInstance bound;

    /** Which hand the held items page shows. */
    private boolean offHand;

    /* The general page's sections, built once. */
    private UISection generalSection;
    private UISection renderSection;
    private UISection sizeSection;
    private UISection lookAtSection;
    private UISection[] sections;

    /* The bodies refilled per model or per list change. Every body made by body() is listed here. */
    private final List<UIElement> bodies = new ArrayList<>();
    private UIElement generalBody;
    private UIElement renderBody;
    private UIElement sizeBody;
    private UIElement lookAtBody;

    /** The picked bone's / weld's settings, under their lists. */
    private UIElement bonePanel;
    private UIElement weldPanel;

    /* The "list + settings" blocks: the attachment slots, the welds. The bone tree is one too, with its own list class. */
    private SlotBlock armor;
    private SlotBlock items;
    private SlotBlock firstPerson;
    private UITabStrip itemsTabs;
    private UITabStrip fpTabs;
    private UIIcon dupeItem;
    private UIIcon removeItem;
    private UIModelBoneList bones;
    private UISearchList<String> bonesSearch;
    private UIEntryList<WeldValue> weldList;
    private UIIcon dupeWeld;
    private UIIcon removeWeld;

    /** The poses page: the config's two poses picked by a tab strip, the editor of the picked one under it. */
    private UITabStrip poseTabs;
    private UIModelPoseEditor poseEditor;

    /** Which pose the poses page shows: the default one, or the sneaking one. */
    private boolean defaultPose;

    /** Whether the pose editor is in its two-column arrangement, which the pane's width decides. */
    private boolean wide;

    /* The role dots of the bone tree: rightmost, a mirror bone is set; next to it, a picking override. */
    private static final int MARKER_MIRROR = Colors.A100 | Colors.CYAN;
    private static final int MARKER_PICKING = Colors.A100 | Colors.ORANGE;

    /** Set while fillAll() refills everything, so the bodies don't each trigger a re-layout. */
    private boolean bulkFill;

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

    private final EntryClipboard welds = new EntryClipboard(PresetManager.MODEL_WELDS, "_CopyModelWeld");

    public UIModelEditorPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.pane = new UIElement();

        /* What the tour of this panel points at; the pane's parts are built further down */
        TourAnchors.register("model_editor.preview", () -> this.renderer);
        TourAnchors.register("model_editor.settings", () -> this.pane);
        TourAnchors.register("model_editor.bones", () -> this.tabs.getTab(Tab.BONES.ordinal()));

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
        this.createTabs();
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

        this.createPages();
        this.showEditor(lastEditor);
        this.showTab(lastTab);

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

    /** The panes of the two editors, one over the other; the config's contents are built into its own. */
    private void createEditors()
    {
        for (Editor pick : EDITORS)
        {
            UIElement element = new UIElement();

            element.full(this.pane);
            this.editors[pick.ordinal()] = element;
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
        this.layoutPanes();
        this.resize();
    }

    private void showEditor(Editor editor)
    {
        lastEditor = editor;

        for (Editor pick : EDITORS)
        {
            this.editorPane(pick).setVisible(pick == editor);
        }

        this.syncPreview();
    }

    /* Tabs. The strip is the dock stacks' one — square icons, the name on a card under the cursor, the
     * open one marked by the bar at its bottom edge. */

    private void createTabs()
    {
        this.tabs = new UITabStrip(ScrollDirection.HORIZONTAL)
        {
            @Override
            protected boolean pressTab(int index, UIContext context)
            {
                UIModelEditorPanel.this.openTab(TABS[index]);

                return true;
            }
        };
        this.tabs.fixed();
        this.tabs.background(BBSSettings::chromeSurface);
        this.tabs.activeEdge(Direction.BOTTOM);
        this.tabs.active(() -> lastTab.ordinal());
        this.tabs.hoverLabels((index) -> TABS[index].label);

        for (Tab tab : TABS)
        {
            UIIcon icon = new UIIcon(tab.icon, null);

            /* Stays white under the cursor: the label card is the hover cue here */
            icon.hoverColor(Colors.WHITE).wh(TABS_HEIGHT, TABS_HEIGHT);
            this.tabs.addTab(icon);
        }

        UIElement config = this.editorPane(Editor.CONFIG);

        this.tabs.relative(config).x(0).y(0).w(1F).h(TABS_HEIGHT);
        config.add(this.tabs);

        for (Tab tab : TABS)
        {
            UIScrollView page = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);

            page.relative(config).x(0).y(TABS_HEIGHT).w(1F).h(1F, -TABS_HEIGHT);
            this.pages[tab.ordinal()] = page;
            config.add(page);
        }
    }

    private UIScrollView page(Tab tab)
    {
        return this.pages[tab.ordinal()];
    }

    /** Open a page: it's the one shown, and the preview follows it. */
    private void openTab(Tab tab)
    {
        this.showTab(tab);
        this.layoutPanes();
        this.resize();
    }

    private void showTab(Tab tab)
    {
        lastTab = tab;

        for (Tab other : TABS)
        {
            this.page(other).setVisible(other == tab);
        }

        this.syncPreview();
    }

    /**
     * What the preview shows follows the open config page: the armor is worn only on the armor page
     * and the items held only on the items page (so each is seen against the bare model), the
     * first-person page is the first-person view, and the model sneaks on the poses page — so the
     * sneaking pose is what's being edited, the way the animator applies it in play.
     *
     * <p>The model editor is about the model itself, so it gets the plain model: nothing worn, nothing
     * held, the orbit view.</p>
     */
    private void syncPreview()
    {
        boolean config = lastEditor == Editor.CONFIG;

        this.renderer.setFirstPerson(config && lastTab == Tab.FIRST_PERSON);
        this.renderer.setEquipment(config && lastTab == Tab.ARMOR, config && lastTab == Tab.ITEMS);
        this.renderer.getEntity().setSneaking(config && lastTab == Tab.POSES && !this.defaultPose);
    }

    private void cycleTab(int direction)
    {
        this.openTab(TABS[Math.floorMod(lastTab.ordinal() + direction, TABS.length)]);
        UIUtils.playClick();
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
     * The pose editor's arrangement follows the pane's width (two columns once it's wide enough, the
     * form editor's rule), and the first-person frame is sized in pixels off the editor's area, so
     * it's laid out again after every pass.
     */
    @Override
    public void resize()
    {
        boolean wide = this.splitter.getPixels() > UIPoseEditor.WIDE_WIDTH;

        if (this.wide != wide)
        {
            this.wide = wide;
            this.poseEditor.buildLayout(wide);
        }

        super.resize();

        if (this.renderer.isFirstPerson())
        {
            this.layoutPanes();
            this.renderer.resize();
        }
    }

    private void registerKeybinds()
    {
        IKey category = UIKeys.MODEL_EDITOR_TITLE;
        Supplier<Boolean> open = () -> this.data != null;
        /* The tab keys belong to the config editor's tabs — they'd otherwise act on a hidden page. */
        Supplier<Boolean> config = () -> this.data != null && lastEditor == Editor.CONFIG;

        this.keys().register(Keys.MODEL_EDITOR_NEXT_TAB, () -> this.cycleTab(1)).active(config).category(category);
        this.keys().register(Keys.MODEL_EDITOR_PREV_TAB, () -> this.cycleTab(-1)).active(config).category(category);
        this.keys().register(Keys.MODEL_EDITOR_EXPAND_ALL, () -> this.setAllExpanded(true)).active(config).category(category);
        this.keys().register(Keys.MODEL_EDITOR_COLLAPSE_ALL, () -> this.setAllExpanded(false)).active(config).category(category);
        this.keys().register(Keys.MODEL_EDITOR_FIND_BONE, this::findBone).active(open).category(category);
        this.keys().register(Keys.MODEL_EDITOR_OPEN_HISTORY, this::openHistory).active(open).category(category);
    }

    private void setAllExpanded(boolean expanded)
    {
        for (UISection section : this.sections)
        {
            section.setExpanded(expanded);
        }

        this.resizePage(Tab.GENERAL);
        UIUtils.playClick();
    }

    /** Ctrl+F: the config editor's bones page, with the caret in the tree's search box. */
    private void findBone()
    {
        this.showEditor(Editor.CONFIG);
        this.openTab(Tab.BONES);
        this.getContext().focus(this.bonesSearch.search);
        UIUtils.playClick();
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
     * but our settings widgets are static — built off {@link #bound} — so the bone lists keep the old bones
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
        this.fillAll(data);

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
        this.fillAll(this.data);
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

    /* Page scaffolding. Built once here; only the bodies below get refilled. */

    private void createPages()
    {
        /* General: the plain settings in a few folded sections. */
        this.generalSection = this.section(UIKeys.FORMS_EDITORS_GENERAL, true);
        this.generalBody = this.body();
        this.generalSection.fields.add(this.generalBody);

        this.renderSection = this.section(UIKeys.MODEL_EDITOR_RENDER, true);
        this.renderBody = this.body();
        this.renderSection.fields.add(this.renderBody);

        this.sizeSection = this.section(UIKeys.MODEL_EDITOR_SIZE, true);
        this.sizeBody = this.body();
        this.sizeSection.fields.add(this.sizeBody);

        this.lookAtSection = this.section(UIKeys.MODEL_EDITOR_LOOK_AT, false);
        this.lookAtBody = this.body();
        this.lookAtSection.fields.add(this.lookAtBody);

        this.sections = new UISection[] {this.generalSection, this.renderSection, this.sizeSection, this.lookAtSection};
        this.page(Tab.GENERAL).add(this.sections);

        /* Bones: the tree with the picked bone's settings under it — no header, it IS the page. The tree
         * takes whatever height the page has left after the rest; the ask is a floor, not the wish. */
        this.bones = new UIModelBoneList((list) -> this.fillBone(), () -> this.data == null ? null : this.data.disabledBones, this::fillBone);
        this.bones.markers(this::boneMarkers, UIKeys.MODEL_EDITOR_BONES_LEGEND);
        this.bonesSearch = new UISearchList<>(this.bones);
        this.bonesSearch.label(UIKeys.GENERAL_SEARCH);
        this.bonesSearch.h(UIStringList.DEFAULT_HEIGHT * 8 - 8).expand();
        this.bonePanel = this.body();
        this.page(Tab.BONES).add(this.bonesSearch, this.bonePanel);

        /* Welds: the add/duplicate/remove strip over the list, the replay list's idiom — now that a weld
         * is picked rather than edited inline, the strip's verbs have something to act on. The add icon
         * pastes a copied weld as a new one on right click. */
        this.weldList = new UIEntryList<>((list) -> this.fillWeld(), UIModelEditorPanel::weldName).broken((weld) -> this.diagnoseWeld(weld) != null);
        this.weldList.h(UIStringList.DEFAULT_HEIGHT * 4).expand();
        this.weldList.context((menu) ->
        {
            WeldValue weld = this.weldList.getAtCursor(this.getContext());

            if (weld != null)
            {
                this.weldList.setCurrent(weld);
                this.fillWeld();
                this.fillWeldMenu(menu, () -> this.presetData(weld), (data) -> this.applyWeld(weld, data), () -> this.duplicateWeld(weld), () -> this.removeWeld(weld));
            }
        });

        UIIcon addWeld = new UIIcon(Icons.ADD, (b) -> this.addWeld());

        addWeld.tooltip(UIKeys.MODEL_EDITOR_WELD_ADD);
        addWeld.context((menu) -> this.fillWeldMenu(menu, null, this::pasteNewWeld, null, null));
        this.dupeWeld = new UIIcon(Icons.DUPE, (b) -> this.duplicateWeld(this.weldList.getCurrentFirst()));
        this.dupeWeld.tooltip(UIKeys.MODEL_EDITOR_WELD_DUPLICATE);
        this.removeWeld = new UIIcon(Icons.REMOVE, (b) -> this.removeWeld(this.weldList.getCurrentFirst()));
        this.removeWeld.tooltip(UIKeys.MODEL_EDITOR_WELD_REMOVE);

        this.weldPanel = this.body();
        this.page(Tab.WELDS).add(this.strip(addWeld, this.dupeWeld, this.removeWeld), this.weldList, this.weldPanel);

        /* Armor: every piece the config can place, one row each, named by the piece. */
        this.armor = new SlotBlock((slot) -> ModelSlotKind.ARMOR, (slot) -> this.armorTypeLabel(this.armorTypeOf(slot)), () -> this.armorSlots());
        this.page(Tab.ARMOR).add(this.armor.list, this.armor.panel);

        /* Held items: one hand at a time, picked by a tab strip, its slots editable. */
        this.itemsTabs = this.handTabs(() ->
        {
            this.items.list.deselect();
            this.fillItems();
        });

        this.items = new SlotBlock((slot) -> this.itemsKind(), null, () -> this.itemsList().getAllTyped());
        this.items.list.context((menu) ->
        {
            ArmorSlotValue slot = this.items.list.getAtCursor(this.getContext());

            if (slot != null)
            {
                this.items.list.setCurrent(slot);
                this.items.fill();
                menu.action(Icons.DUPE, UIKeys.MODEL_EDITOR_ITEM_DUPLICATE, () -> this.duplicateItem(slot));
                menu.icon(MenuVerb.REMOVE, () -> this.removeItem(slot)).label(UIKeys.MODEL_EDITOR_ITEM_REMOVE);
            }
        });

        UIIcon addItem = new UIIcon(Icons.ADD, (b) -> this.addItem());

        addItem.tooltip(UIKeys.MODEL_EDITOR_ITEM_ADD);
        this.dupeItem = new UIIcon(Icons.DUPE, (b) -> this.duplicateItem(this.items.list.getCurrentFirst()));
        this.dupeItem.tooltip(UIKeys.MODEL_EDITOR_ITEM_DUPLICATE);
        this.removeItem = new UIIcon(Icons.REMOVE, (b) -> this.removeItem(this.items.list.getCurrentFirst()));
        this.removeItem.tooltip(UIKeys.MODEL_EDITOR_ITEM_REMOVE);
        this.items.onFill = () ->
        {
            boolean picked = this.items.list.getCurrentFirst() != null;

            this.dupeItem.setEnabled(picked);
            this.removeItem.setEnabled(picked);
        };

        this.page(Tab.ITEMS).add(this.itemsTabs, this.strip(addItem, this.dupeItem, this.removeItem), this.items.list, this.items.panel);

        /* First person: the hand picked by the same tab strip as the items, its one slot under it. */
        this.fpTabs = this.handTabs(() -> this.firstPerson.fill());
        this.firstPerson = new SlotBlock(
            (slot) -> slot == this.data.fpMain ? ModelSlotKind.FIRST_PERSON_MAIN : ModelSlotKind.FIRST_PERSON_OFF,
            () -> this.offHand ? this.data.fpOffhand : this.data.fpMain
        );
        this.page(Tab.FIRST_PERSON).add(this.fpTabs, this.firstPerson.panel);

        /* Poses: the sneaking pose and the default pose, picked by a tab strip, over the form editor's pose
         * editor bound to the picked one — bare, since a model's pose has no material and no fix to show.
         * The presets menu of the editor's bone list is where a pose is loaded from and saved to; the
         * strip's own menu clears the shown one. The picked bone is on the viewport gizmo, and G/R/S
         * start a gesture on it. */
        this.poseTabs = this.textTabs(110, () -> this.defaultPose ? 1 : 0, (index) ->
        {
            this.defaultPose = index == 1;
            this.showTab(lastTab);
            this.fillPoses();
        }, UIKeys.MODEL_EDITOR_SNEAKING, UIKeys.MODEL_EDITOR_DEFAULT_POSE);
        this.poseTabs.context((menu) ->
        {
            if (this.data != null)
            {
                menu.icon(MenuVerb.REMOVE, this::clearPose).label(UIKeys.MODEL_EDITOR_SNEAKING_CLEAR);
            }
        });
        this.poseEditor = new UIModelPoseEditor();
        this.poseEditor.poseOnly();
        this.poseEditor.transform.hotkeyDrag(() ->
        {
            ModelSlotTarget target = this.poseTarget();

            return target == null ? null : this.renderer.buildGizmoDrag(target);
        });
        this.page(Tab.POSES).add(this.poseTabs, this.poseEditor);
    }

    /** The main/off hand strip the items and the first-person pages share; both read {@link #offHand}. */
    private UITabStrip handTabs(Runnable onChange)
    {
        return this.textTabs(70, () -> this.offHand ? 1 : 0, (index) ->
        {
            this.offHand = index == 1;
            onChange.run();
        }, UIKeys.MODEL_EDITOR_ITEMS_MAIN, UIKeys.MODEL_EDITOR_ITEMS_OFF);
    }

    /** A row of word tabs over a page's content, {@code width} each; the owner keeps which one is active. */
    private UITabStrip textTabs(int width, IntSupplier active, IntConsumer onSelect, IKey... labels)
    {
        UITabStrip tabs = new UITabStrip(ScrollDirection.HORIZONTAL)
        {
            @Override
            protected boolean pressTab(int index, UIContext context)
            {
                this.select(index);

                return true;
            }
        };

        tabs.fixed();
        tabs.active(active);
        tabs.onSelect(onSelect);

        for (IKey label : labels)
        {
            tabs.addTab(new UITextTab(label)).w(width).h(UIConstants.CONTROL_HEIGHT);
        }

        tabs.h(UIConstants.CONTROL_HEIGHT);

        return tabs;
    }

    private UISection section(IKey title, boolean defaultExpanded)
    {
        UISection section = new UISection(title);

        section.setExpanded(defaultExpanded);

        return section;
    }

    /** A vertical container that gets emptied and refilled on its own; registered for clearBodies(). */
    private UIElement body()
    {
        UIElement body = new UIElement();

        body.column(UIConstants.MARGIN).vertical().stretch();
        this.bodies.add(body);

        return body;
    }

    /** The verbs of a list — add, duplicate, remove — as a row of compact icons over it (the replay list's idiom). */
    private UIElement strip(UIIcon... icons)
    {
        UIElement strip = new UIElement();

        strip.row(0).height(UIConstants.CONTROL_HEIGHT);

        for (UIIcon icon : icons)
        {
            icon.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
            strip.add(icon);
        }

        return strip;
    }

    /** What a weld joins, as its row reads: {@code bone/face → bone/face}, a "?" for a bone not picked yet. */
    private static String weldName(WeldValue weld)
    {
        return weldEnd(weld.sourceBone.get(), weld.sourceFace.get()) + " → " + weldEnd(weld.targetBone.get(), weld.targetFace.get());
    }

    private static String weldEnd(String bone, String face)
    {
        String name = bone.isEmpty() ? "?" : bone;

        return face.isEmpty() ? name : name + "/" + face;
    }

    /* Filling. fillAll() is the "a different model is open" path; the individual fillX() methods are
     * what a list mutation calls, so an add or a remove only touches its own container. */

    private void fillAll(ModelConfig config)
    {
        if (config == null)
        {
            /* The base class hides the whole editor pane when no model is open; empty the bodies too so
             * the pages don't keep the closed model's config (and its widgets) alive. */
            this.clearBodies();

            return;
        }

        this.bulkFill = true;

        try
        {
            this.fillGeneral();
            this.fillLookAt();
            this.fillItems();
            this.fillArmor();
            this.fillFirstPerson();
            this.fillWelds();
            this.fillBones();
            this.fillPoses();
        }
        finally
        {
            this.bulkFill = false;
        }

        this.resizePages();
    }

    private void clearBodies()
    {
        this.bulkFill = true;

        try
        {
            for (UIElement body : this.bodies)
            {
                body.removeAll();
            }

            this.bones.fill(null);
            this.weldList.clear();
            this.armor.refill();
            this.items.refill();
            this.firstPerson.refill();
            this.poseEditor.setPose(new Pose(), "");
            this.poseEditor.fillGroups(null, null, true, null);
            this.fillBone();
            this.fillWeld();
        }
        finally
        {
            this.bulkFill = false;
        }

        this.resizePages();
    }

    private void resizePages()
    {
        for (UIScrollView page : this.pages)
        {
            page.resize();
            page.scroll.clamp();
        }
    }

    /**
     * Re-layout a page after a body changed height, keeping the scroll inside its new bounds.
     * Suppressed while every page is being refilled at once, so that costs one layout pass, not nine.
     */
    private void resizePage(Tab tab)
    {
        if (this.bulkFill)
        {
            return;
        }

        UIScrollView page = this.page(tab);

        page.resize();
        page.scroll.clamp();
    }

    private void fillGeneral()
    {
        ModelConfig config = this.data;

        UITextbox poseGroup = UIValues.textbox(10000, () -> this.data.poseGroup);

        UIBonePicker anchor = this.bonePicker(config.anchor::get, config.anchor::set, () -> {});

        UIValues.resettable(anchor, () -> this.data.anchor, anchor::refresh);

        UIButton texture = new UIButton(UIKeys.TEXTURE_PICK_TEXTURE, (b) -> UITexturePicker.open(this.getContext(), this.data.texture.get(), this.data.texture::set));

        UIValues.resettable(texture, () -> this.data.texture, null);

        this.generalBody.removeAll();
        this.generalBody.add(
            UI.labelRow(UIKeys.MODEL_EDITOR_POSE_GROUP, poseGroup),
            UI.labelRow(UIKeys.MODEL_EDITOR_ANCHOR, anchor),
            UI.labelRow(UIKeys.MODEL_EDITOR_TEXTURE, texture)
        );

        this.renderBody.removeAll();
        this.renderBody.add(
            this.toggle(UIKeys.MODEL_EDITOR_PROCEDURAL, () -> this.data.procedural, this::refresh),
            this.toggle(UIKeys.MODEL_EDITOR_CULLING, () -> this.data.culling, null),
            this.toggle(UIKeys.MODEL_EDITOR_ON_CPU, () -> this.data.onCpu, this::refresh)
        );

        UITrackpad uiScale = this.trackpad(() -> this.data.uiScale, null);

        uiScale.limit(config.uiScale).delayedInput();

        this.sizeBody.removeAll();
        this.sizeBody.add(
            UI.labelRow(UIKeys.MODEL_EDITOR_UI_SCALE, uiScale),
            UI.label(UIKeys.MODEL_EDITOR_SCALE), UI.row(this.component(config.scale, 0), this.component(config.scale, 1), this.component(config.scale, 2))
        );

        this.resizePage(Tab.GENERAL);
    }

    private void fillLookAt()
    {
        ModelConfig config = this.data;
        Runnable rebuild = () -> this.data.rebuild();

        UIBonePicker head = this.bonePicker(config.lookAt.head::get, config.lookAt.head::set, rebuild);

        UIValues.resettable(head, () -> this.data.lookAt.head, () ->
        {
            head.refresh();
            rebuild.run();
        });

        UITrackpad limit = this.trackpad(() -> this.data.lookAt.headLimit, rebuild);

        limit.delayedInput();

        this.lookAtBody.removeAll();
        this.lookAtBody.add(
            UI.labelRow(UIKeys.MODEL_EDITOR_LOOK_AT_HEAD, head),
            this.toggle(UIKeys.MODEL_EDITOR_LOOK_AT_PITCH, () -> this.data.lookAt.pitch, rebuild),
            UI.labelRow(UIKeys.MODEL_EDITOR_LOOK_AT_LIMIT, limit)
        );

        this.resizePage(Tab.GENERAL);
    }

    /* Attachment slots (items in hand, armor, first-person) — a bone plus a transform, the transform on the
     * viewport gizmo when the slot is the picked one of the open page. */

    /**
     * A "list + settings" block over attachment slots — what the armor, the held items and the
     * first-person pages are, told apart only by where their slots come from: a fixed set with a
     * name per slot (the armor pieces), the editable list of a hand (the items), or one slot at a
     * time with no list at all (the first-person hand the tab strip picks). The picked slot's bone
     * and transform sit under the list; with nothing picked the same fields stand empty and
     * disabled, so the page keeps its height and the scroll doesn't jump on a pick.
     */
    private class SlotBlock
    {
        /** The slots to pick from; null for a block over one slot at a time. */
        public final UIEntryList<ArmorSlotValue> list;
        public final UIElement panel;

        /** The picked slot on the viewport gizmo; null with nothing picked. */
        public ModelSlotTarget target;

        /** Runs after every fill, for whatever the host keeps in step with the pick (the items' verb strip). */
        public Runnable onFill;

        private final Function<ArmorSlotValue, ModelSlotKind> kinds;
        private final Function<ArmorSlotValue, IKey> labels;
        private final Supplier<List<ArmorSlotValue>> source;
        private final Supplier<ArmorSlotValue> fixed;

        /**
         * @param kinds  what kind of attachment a slot is
         * @param labels the name a row carries besides the bone, null for rows that are the bone alone
         * @param source the slots to list, asked again on every refill
         */
        public SlotBlock(Function<ArmorSlotValue, ModelSlotKind> kinds, Function<ArmorSlotValue, IKey> labels, Supplier<List<ArmorSlotValue>> source)
        {
            this.kinds = kinds;
            this.labels = labels;
            this.source = source;
            this.fixed = null;

            this.list = new UIEntryList<>((picked) -> this.fill(), this::name);
            this.list.h(UIStringList.DEFAULT_HEIGHT * 4).expand();
            this.panel = UIModelEditorPanel.this.body();
        }

        /** A block without a list: {@code fixed} names the one slot it edits, asked on every fill. */
        public SlotBlock(Function<ArmorSlotValue, ModelSlotKind> kinds, Supplier<ArmorSlotValue> fixed)
        {
            this.kinds = kinds;
            this.labels = null;
            this.source = null;
            this.fixed = fixed;

            this.list = null;
            this.panel = UIModelEditorPanel.this.body();
        }

        /** How a row reads: the slot's own name with its bone after it once it has one; the bone alone ("?" until picked) for rows without a name. */
        private String name(ArmorSlotValue slot)
        {
            String bone = slot.group.get();

            if (this.labels == null)
            {
                return slot.isActive() ? bone : "?";
            }

            String label = this.labels.apply(slot).get();

            return slot.isActive() ? label + ": " + bone : label;
        }

        public ArmorSlotValue picked()
        {
            if (UIModelEditorPanel.this.data == null)
            {
                return null;
            }

            return this.list == null ? this.fixed.get() : this.list.getCurrentFirst();
        }

        /** Re-list the slots from the source (none without a model), keeping the pick (slots are compared by identity). */
        public void refill()
        {
            if (this.list == null)
            {
                this.fill();

                return;
            }

            ArmorSlotValue picked = this.list.getCurrentFirst();

            this.list.setList(UIModelEditorPanel.this.data == null ? new ArrayList<>() : new ArrayList<>(this.source.get()));

            if (picked != null)
            {
                this.list.setCurrent(picked);
            }

            this.fill();
        }

        /** The picked slot's settings — its bone and transform. */
        public void fill()
        {
            ArmorSlotValue picked = this.picked();
            ArmorSlotValue slot = picked == null ? new ArmorSlotValue("") : picked;
            ModelSlotKind kind = picked == null ? ModelSlotKind.ARMOR : this.kinds.apply(picked);
            UIPropTransform transform = UIModelEditorPanel.this.slotTransform(slot, kind);

            this.target = picked == null ? null : new ModelSlotTarget(slot.group.get(), kind, transform);

            this.panel.removeAll();
            this.panel.add(
                UIModelEditorPanel.this.bonePicker(slot.group::get, slot.group::set, () ->
                {
                    UIModelEditorPanel.this.data.rebuild();
                    this.refill();
                }),
                transform
            );
            UIModelEditorPanel.this.setEnabledDeep(this.panel, picked != null);

            if (this.onFill != null)
            {
                this.onFill.run();
            }

            UIModelEditorPanel.this.resizePage(UIModelEditorPanel.this.tabOf(this));
        }
    }

    /** The block on the open page, if it's a slot page; its picked slot is the one on the gizmo. */
    private SlotBlock shownBlock()
    {
        return switch (lastTab)
        {
            case ARMOR -> this.armor;
            case ITEMS -> this.items;
            case FIRST_PERSON -> this.firstPerson;
            default -> null;
        };
    }

    /**
     * What the viewport gizmo is on: the open slot page's picked slot, or the poses page's picked bone.
     * Nothing outside the config editor — a slot and a pose are config, and the model editor has its own
     * things to put on the gizmo.
     */
    private ModelSlotTarget shownTarget()
    {
        if (lastEditor != Editor.CONFIG)
        {
            return null;
        }

        if (lastTab == Tab.POSES)
        {
            return this.poseTarget();
        }

        SlotBlock block = this.shownBlock();

        return block == null ? null : block.target;
    }

    /** The pose editor's picked bone as a gizmo target; null with no bone picked. */
    private ModelSlotTarget poseTarget()
    {
        String bone = this.data == null ? null : this.poseEditor.getGroup();

        if (bone == null || bone.isEmpty() || this.poseEditor.transform.getTransform() == null)
        {
            return null;
        }

        return new ModelSlotTarget(bone, ModelSlotKind.POSE, this.poseEditor.transform);
    }

    private Tab tabOf(SlotBlock block)
    {
        return block == this.armor ? Tab.ARMOR : block == this.items ? Tab.ITEMS : Tab.FIRST_PERSON;
    }

    /**
     * A slot's transform editor: edits go through the value's notify, so the undo handler catches them,
     * and the runtime reads a copy of the transform, so it's rebuilt after every step of a drag.
     */
    private UIPropTransform slotTransform(ArmorSlotValue slot, ModelSlotKind kind)
    {
        UIPropTransform transform = new UIPropTransform();

        transform.callbacks(
            () -> slot.transform.preNotify(),
            () ->
            {
                slot.transform.postNotify();
                this.data.rebuild();
            },
            () -> slot.transform.preNotify(IValueListener.FLAG_UNMERGEABLE)
        );
        transform.setTransform(slot.transform.get());

        /* G/R/S start a gesture on the shown slot without touching a handle — the way the arrows are
         * used everywhere else, with the handles hidden in the settings. Only the slot the viewport is
         * showing listens, or the keys would edit a slot that isn't on screen. */
        transform.hotkeyDrag(() ->
        {
            ModelSlotTarget target = this.shownTarget();

            return target == null ? null : this.renderer.buildGizmoDrag(target);
        });
        transform.enableHotkeys(() ->
        {
            ModelSlotTarget target = this.shownTarget();

            return target != null && target.editor() == transform && this.renderer.isFirstPerson() == kind.firstPerson;
        });

        return transform;
    }

    private ModelConfig.ItemSlotList itemsList()
    {
        return this.offHand ? this.data.itemsOff : this.data.itemsMain;
    }

    private ModelSlotKind itemsKind()
    {
        return this.offHand ? ModelSlotKind.ITEM_OFF : ModelSlotKind.ITEM_MAIN;
    }

    private void fillItems()
    {
        this.items.refill();
    }

    private void addItem()
    {
        this.insertItem(new ArmorSlotValue(""), -1);
    }

    private void duplicateItem(ArmorSlotValue slot)
    {
        if (slot == null)
        {
            return;
        }

        ArmorSlotValue copy = new ArmorSlotValue("");

        copy.fromData(this.presetData(slot));
        this.insertItem(copy, this.itemsList().getAllTyped().indexOf(slot) + 1);
    }

    /** Put a slot into the shown hand's list (at the end for {@code index < 0}) and pick it, so its settings are up at once. */
    private void insertItem(ArmorSlotValue slot, int index)
    {
        ModelConfig.ItemSlotList list = this.itemsList();

        BaseValue.edit(list, (v) ->
        {
            if (index < 0)
            {
                list.add(slot);
            }
            else
            {
                list.add(index, slot);
            }

            list.sync();
        });

        this.data.rebuild();
        this.fillItems();
        this.items.list.setCurrent(slot);
        this.items.fill();
    }

    private void removeItem(ArmorSlotValue slot)
    {
        if (slot == null)
        {
            return;
        }

        ModelConfig.ItemSlotList list = this.itemsList();

        BaseValue.edit(list, (v) ->
        {
            list.getAllTyped().remove(slot);
            list.sync();
        });

        this.data.rebuild();
        this.items.list.deselect();
        this.fillItems();
    }

    /** A value group as copyable data, or null if it doesn't serialise to a map (nothing to copy then). */
    private MapType presetData(BaseValue value)
    {
        BaseType data = value.toData();

        return data.isMap() ? data.asMap() : null;
    }

    private List<ArmorSlotValue> armorSlots()
    {
        List<ArmorSlotValue> slots = new ArrayList<>();

        for (ArmorType type : ArmorType.values())
        {
            slots.add(this.data.armorSlots.slot(type));
        }

        return slots;
    }

    private ArmorType armorTypeOf(ArmorSlotValue slot)
    {
        for (ArmorType type : ArmorType.values())
        {
            if (this.data.armorSlots.slot(type) == slot)
            {
                return type;
            }
        }

        return ArmorType.HELMET;
    }

    private void fillArmor()
    {
        this.armor.refill();
    }

    private IKey armorTypeLabel(ArmorType type)
    {
        return switch (type)
        {
            case HELMET -> UIKeys.MODEL_EDITOR_ARMOR_HELMET;
            case CHEST -> UIKeys.MODEL_EDITOR_ARMOR_CHEST;
            case LEGGINGS -> UIKeys.MODEL_EDITOR_ARMOR_LEGGINGS;
            case LEFT_ARM -> UIKeys.MODEL_EDITOR_ARMOR_LEFT_ARM;
            case RIGHT_ARM -> UIKeys.MODEL_EDITOR_ARMOR_RIGHT_ARM;
            case LEFT_LEG -> UIKeys.MODEL_EDITOR_ARMOR_LEFT_LEG;
            case RIGHT_LEG -> UIKeys.MODEL_EDITOR_ARMOR_RIGHT_LEG;
            case LEFT_BOOT -> UIKeys.MODEL_EDITOR_ARMOR_LEFT_BOOT;
            case RIGHT_BOOT -> UIKeys.MODEL_EDITOR_ARMOR_RIGHT_BOOT;
        };
    }

    private void fillFirstPerson()
    {
        this.firstPerson.refill();
    }

    /* Poses: the shown pose is edited in place, on the model wearing it in the preview — the default pose
     * is always on, the sneaking one while the entity sneaks (which the page makes it do). */

    /** The pose the poses page shows and edits. */
    private ValuePose shownPose()
    {
        return this.defaultPose ? this.data.defaultPose : this.data.sneakingPose;
    }

    private void fillPoses()
    {
        ValuePose pose = this.shownPose();

        this.poseEditor.setValuePose(pose);
        this.poseEditor.setPose(pose.get(), this.bound.getPoseGroup());
        this.poseEditor.fillGroups(this.bound.getModel(), this.bound.getFlippedParts(), true, this.bound.getDisabledBones());

        this.resizePage(Tab.POSES);
    }

    /** Empty the shown pose; the editor is re-bound, since it holds the pose object itself. */
    private void clearPose()
    {
        this.shownPose().set(new Pose());
        this.fillPoses();
    }

    /* Bones: the tree keeps its pick across a refill; the panel under it is the picked bone's settings. */

    private void fillBones()
    {
        String picked = this.bones.getCurrentFirst();

        this.bones.fill(this.bound == null ? null : this.bound.getModel());

        if (picked != null)
        {
            this.bones.setCurrent(picked);
        }

        this.fillBone();
    }

    /**
     * A bone clicked in the preview picks it where a bone is picked: in the tree on the bones page, in
     * the pose editor on the poses page; anywhere else the click is left alone (a bone is given to a
     * slot through its picker or the eyedropper).
     */
    private boolean selectBone(String bone)
    {
        if (lastEditor != Editor.CONFIG)
        {
            return false;
        }

        if (lastTab == Tab.POSES)
        {
            if (!this.poseEditor.hasBone(bone))
            {
                return false;
            }

            this.poseEditor.selectBone(bone);

            return true;
        }

        if (lastTab != Tab.BONES)
        {
            return false;
        }

        this.bonesSearch.filter("", true);
        this.bones.setCurrentScroll(bone);
        this.fillBone();

        return true;
    }

    /**
     * The picked bone's settings; refilled after every change to them, from either the panel or the tree's
     * menu. With nothing picked the same fields stand empty and disabled, so the page keeps its height and
     * the scroll doesn't jump on every pick.
     */
    private void fillBone()
    {
        ModelConfig config = this.data;
        String picked = config == null ? null : this.bones.getCurrentFirst();
        String bone = picked == null ? "" : picked;

        UIToggle visible = new UIToggle(UIKeys.MODEL_EDITOR_BONE_VISIBLE, picked != null && !config.disabledBones.get().contains(bone), (t) ->
        {
            BaseValue.edit(config.disabledBones, (v) ->
            {
                if (t.getValue())
                {
                    config.disabledBones.get().remove(bone);
                }
                else
                {
                    config.disabledBones.get().add(bone);
                }
            });

            this.fillBone();
        });

        this.bonePanel.removeAll();
        this.bonePanel.add(
            visible,
            UI.labelRow(UIKeys.MODEL_EDITOR_BONE_MIRROR, this.bonePicker(() -> picked == null ? "" : this.mirrorOf(bone), (other) -> this.setMirror(bone, other), this::fillBone)),
            UI.labelRow(UIKeys.MODEL_EDITOR_BONE_PICKING, this.bonePicker(() -> picked == null ? "" : config.pickingOverrides.get().getOrDefault(bone, ""), (other) -> this.setPickingOverride(bone, other), this::fillBone))
        );
        this.setEnabledDeep(this.bonePanel, picked != null);

        this.resizePage(Tab.BONES);
    }

    /** Enable or disable every control in a settings panel — the panel stands, only its fields go quiet. */
    private void setEnabledDeep(UIElement panel, boolean enabled)
    {
        for (UIElement element : panel.getChildren(UIElement.class, new ArrayList<>(), false))
        {
            element.setEnabled(enabled);
        }
    }

    private UIBoneTreeList.Marker[] boneMarkers(String bone)
    {
        ModelConfig config = this.data;

        if (config == null)
        {
            return null;
        }

        boolean mirror = !this.mirrorOf(bone).isEmpty();
        boolean picking = config.pickingOverrides.get().containsKey(bone);

        if (!mirror && !picking)
        {
            return null;
        }

        return new UIBoneTreeList.Marker[]
        {
            mirror ? new UIBoneTreeList.Marker(MARKER_MIRROR, false) : null,
            picking ? new UIBoneTreeList.Marker(MARKER_PICKING, false) : null
        };
    }

    /**
     * The bone's mirror: a flip pair is stored once, either way round, and the pose flip reads it
     * from both sides — so it's shown on both bones too.
     */
    private String mirrorOf(String bone)
    {
        Map<String, String> pairs = this.data.flippedParts.get();
        String mirror = pairs.get(bone);

        if (mirror != null)
        {
            return mirror;
        }

        for (Map.Entry<String, String> entry : pairs.entrySet())
        {
            if (entry.getValue().equals(bone))
            {
                return entry.getKey();
            }
        }

        return "";
    }

    /** Pair the bone with {@code other}, dropping whatever either of them was paired with before; empty unpairs. */
    private void setMirror(String bone, String other)
    {
        BaseValue.edit(this.data.flippedParts, (v) ->
        {
            Map<String, String> pairs = this.data.flippedParts.get();

            pairs.entrySet().removeIf((entry) -> entry.getKey().equals(bone) || entry.getValue().equals(bone));

            if (!other.isEmpty() && !other.equals(bone))
            {
                pairs.entrySet().removeIf((entry) -> entry.getKey().equals(other) || entry.getValue().equals(other));
                pairs.put(bone, other);
            }
        });
    }

    private void setPickingOverride(String bone, String other)
    {
        BaseValue.edit(this.data.pickingOverrides, (v) ->
        {
            if (other.isEmpty() || other.equals(bone))
            {
                this.data.pickingOverrides.get().remove(bone);
            }
            else
            {
                this.data.pickingOverrides.get().put(bone, other);
            }
        });
    }

    /* Welds: the list keeps its pick across a refill (welds are compared by identity, and an undo keeps
     * the same value objects); the panel under it is the picked weld's settings. */

    private void fillWelds()
    {
        ModelConfig config = this.data;
        WeldValue picked = this.weldList.getCurrentFirst();

        this.weldList.setList(new ArrayList<>(config.welds.getAllTyped()));

        if (picked != null)
        {
            this.weldList.setCurrent(picked);
        }

        this.fillWeld();
    }

    /**
     * The picked weld's settings. With nothing picked the same fields stand empty and disabled (bound to a
     * throwaway weld), so the page keeps its height; the issue line is there only while the weld has one.
     */
    private void fillWeld()
    {
        WeldValue picked = this.data == null ? null : this.weldList.getCurrentFirst();
        WeldValue weld = picked == null ? new WeldValue("") : picked;
        WeldBinding.Issue issue = picked == null ? null : this.diagnoseWeld(picked);

        this.dupeWeld.setEnabled(picked != null);
        this.removeWeld.setEnabled(picked != null);

        /* Bone/face changes can make the weld resolvable or not, so they refill the list and the panel to
         * update the name and the issue line; the trackpads can't, so they only re-resolve (a refill
         * mid-drag would orphan them). */
        this.weldPanel.removeAll();

        if (issue != null)
        {
            this.weldPanel.add(UI.label(this.weldIssueText(issue), UIConstants.CONTROL_HEIGHT).labelAnchor(0, 0.5F).color(Colors.NEGATIVE, true));
        }

        this.weldPanel.add(
            UI.row(this.bonePicker(weld.sourceBone::get, weld.sourceBone::set, this::refreshWelds), this.facePicker(weld.sourceFace, this::refreshWelds)),
            UI.row(this.bonePicker(weld.targetBone::get, weld.targetBone::set, this::refreshWelds), this.facePicker(weld.targetFace, this::refreshWelds)),
            UI.labelRow(UIKeys.MODEL_EDITOR_WELD_MAX_ANGLE, this.weldAngle(weld)),
            UI.labelRow(UIKeys.MODEL_EDITOR_WELD_SEAM_FALLOFF, this.weldFalloff(weld)),
            UI.labelRow(UIKeys.MODEL_EDITOR_WELD_PARENT_SHARE, this.weldShare(weld)),
            this.weldTwist(weld)
        );
        this.setEnabledDeep(this.weldPanel, picked != null);

        this.resizePage(Tab.WELDS);
    }

    /**
     * The one bone control of the panel: bound to its value (it relabels itself), its popup over the whole
     * model — welds and slots are model config, so hidden bones stay pickable — its eyedropper armed on the
     * preview, and the bone it names lit up in the preview while the cursor is over it.
     */
    private UIBonePicker bonePicker(Supplier<String> get, Consumer<String> set, Runnable onChange)
    {
        UIBonePicker picker = new UIBonePicker()
        {
            @Override
            public void render(UIContext context)
            {
                if (this.area.isInside(context))
                {
                    UIModelEditorPanel.this.renderer.highlight(get.get());
                }

                super.render(context);
            }
        };

        picker.bind(get, (bone) ->
        {
            set.accept(bone);
            onChange.run();
        }, UIKeys.MODEL_EDITOR_PICK_BONE);
        picker.menu((menu) ->
        {
            if (this.bound != null)
            {
                menu.bones(this.bound.getModel(), null).none().set(get.get());
            }
        });
        picker.viewport(this.renderer);

        return picker;
    }

    private UIIcons facePicker(ValueString value, Runnable onChange)
    {
        UIIcons icons = new UIIcons((b) ->
        {
            value.set(FACES[b.getValue()].name().toLowerCase());
            onChange.run();
        });

        icons.add(Icons.FORWARD, UIKeys.MODEL_EDITOR_FACE_FRONT);
        icons.add(Icons.BACKWARD, UIKeys.MODEL_EDITOR_FACE_BACK);
        icons.add(Icons.ARROW_RIGHT, UIKeys.MODEL_EDITOR_FACE_RIGHT);
        icons.add(Icons.ARROW_LEFT, UIKeys.MODEL_EDITOR_FACE_LEFT);
        icons.add(Icons.ARROW_UP, UIKeys.MODEL_EDITOR_FACE_TOP);
        icons.add(Icons.ARROW_DOWN, UIKeys.MODEL_EDITOR_FACE_BOTTOM);

        CubeFace current = CubeFace.fromName(value.get());

        icons.setValue(current == null ? 0 : current.ordinal());

        return icons;
    }

    private UITrackpad weldAngle(WeldValue weld)
    {
        UITrackpad trackpad = this.trackpad(() -> weld.maxAngle, this::invalidateWelds);

        trackpad.delayedInput();

        return trackpad;
    }

    private UISliderTrackpad weldFalloff(WeldValue weld)
    {
        return this.weldSlider(() -> weld.seamFalloff);
    }

    private UISliderTrackpad weldShare(WeldValue weld)
    {
        return this.weldSlider(() -> weld.parentShare);
    }

    private UISliderTrackpad weldSlider(Supplier<? extends BaseValueNumber<?>> value)
    {
        UISliderTrackpad trackpad = new UISliderTrackpad((v) ->
        {
            value.get().setNumber(v);
            this.invalidateWelds();
        });

        trackpad.limit(0F, 1F).increment(0.05F);
        trackpad.setValue(value.get().get().doubleValue());
        trackpad.delayedInput();

        return UIValues.resettable(trackpad, value, () ->
        {
            trackpad.setValue(value.get().get().doubleValue());
            this.invalidateWelds();
        });
    }

    private UIToggle weldTwist(WeldValue weld)
    {
        return this.toggle(UIKeys.MODEL_EDITOR_WELD_TWIST, () -> weld.twist, this::invalidateWelds);
    }

    private void addWeld()
    {
        this.insertWeld(new WeldValue(""), -1);
    }

    private void pasteNewWeld(MapType data)
    {
        WeldValue weld = new WeldValue("");

        weld.fromData(data);
        this.insertWeld(weld, -1);
    }

    private void duplicateWeld(WeldValue weld)
    {
        if (weld == null)
        {
            return;
        }

        WeldValue copy = new WeldValue("");

        copy.fromData(weld.toData());
        this.insertWeld(copy, this.data.welds.getAllTyped().indexOf(weld) + 1);
    }

    /** Put a weld into the list (at the end for {@code index < 0}) and pick it, so its settings are up at once. */
    private void insertWeld(WeldValue weld, int index)
    {
        ModelConfig config = this.data;

        BaseValue.edit(config.welds, (v) ->
        {
            if (index < 0)
            {
                config.welds.add(weld);
            }
            else
            {
                config.welds.add(index, weld);
            }

            config.welds.sync();
        });

        this.refresh();
        this.weldList.setCurrent(weld);
        this.fillWelds();
    }

    private void removeWeld(WeldValue weld)
    {
        if (weld == null)
        {
            return;
        }

        ModelConfig config = this.data;

        BaseValue.edit(config.welds, (v) ->
        {
            config.welds.getAllTyped().remove(weld);
            config.welds.sync();
        });

        this.refresh();
        this.weldList.deselect();
        this.fillWelds();
    }

    private void applyWeld(WeldValue weld, MapType data)
    {
        BaseValue.edit(weld, (v) -> weld.fromData(data));

        this.refresh();
        this.fillWelds();
    }

    private void invalidateWelds()
    {
        if (this.bound != null)
        {
            this.bound.invalidateWelds();
        }
    }

    /** Re-resolve AND refill the list and panel — for edits that can change a weld's name or resolvability (bone/face picks). */
    private void refreshWelds()
    {
        this.invalidateWelds();
        this.fillWelds();
    }

    private WeldBinding.Issue diagnoseWeld(WeldValue weld)
    {
        if (this.bound == null || !(this.bound.getModel() instanceof Model model))
        {
            return null;
        }

        return WeldBinding.diagnose(model, weld.toWeld());
    }

    private IKey weldIssueText(WeldBinding.Issue issue)
    {
        switch (issue)
        {
            case SOURCE_BONE: return UIKeys.MODEL_EDITOR_WELD_ISSUE_SOURCE_BONE;
            case TARGET_BONE: return UIKeys.MODEL_EDITOR_WELD_ISSUE_TARGET_BONE;
            case SOURCE_FACE: return UIKeys.MODEL_EDITOR_WELD_ISSUE_SOURCE_FACE;
            case TARGET_FACE: return UIKeys.MODEL_EDITOR_WELD_ISSUE_TARGET_FACE;
            case SOURCE_CUBES: return UIKeys.MODEL_EDITOR_WELD_ISSUE_SOURCE_CUBES;
            default: return UIKeys.MODEL_EDITOR_WELD_ISSUE_TARGET_CUBES;
        }
    }

    /**
     * A weld's context menu: the copy/paste/presets icon row on top, then the text actions — the same
     * shape the replay and clip lists use. {@code source} being null means the menu hangs off the "add"
     * button (nothing to copy from, so the row's copy icon disables itself), and {@code duplicate} /
     * {@code remove} are null there for the same reason.
     */
    private void fillWeldMenu(ContextMenuManager menu, Supplier<MapType> source, Consumer<MapType> target, Runnable duplicate, Runnable remove)
    {
        this.welds.aim(source, target);

        UIContext context = this.getContext();

        this.welds.controller.install(menu, context, context.mouseX, context.mouseY);

        if (duplicate != null)
        {
            menu.action(Icons.DUPE, UIKeys.MODEL_EDITOR_WELD_DUPLICATE, duplicate);
        }

        if (remove != null)
        {
            menu.icon(MenuVerb.REMOVE, remove).label(UIKeys.MODEL_EDITOR_WELD_REMOVE);
        }
    }

    /* Plain fields. All bound to a value through UIValues, so a right click offers "reset"; {@code after}
     * runs after an edit AND after a reset, for settings that change more than the value (a rebuild, a
     * geometry refresh). */

    private UIToggle toggle(IKey label, Supplier<ValueBoolean> value, Runnable after)
    {
        UIToggle toggle = new UIToggle(label, value.get().get(), (t) ->
        {
            value.get().set(t.getValue());

            if (after != null)
            {
                after.run();
            }
        });

        return UIValues.resettable(toggle, value, () ->
        {
            toggle.setValue(value.get().get());

            if (after != null)
            {
                after.run();
            }
        });
    }

    private UITrackpad trackpad(Supplier<? extends BaseValueNumber<?>> value, Runnable after)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            value.get().setNumber(v);

            if (after != null)
            {
                after.run();
            }
        });

        trackpad.setValue(value.get().get().doubleValue());

        return UIValues.resettable(trackpad, value, () ->
        {
            trackpad.setValue(value.get().get().doubleValue());

            if (after != null)
            {
                after.run();
            }
        });
    }

    /**
     * Rebuild the live instance's baked state so a config edit that changed the render path shows in the
     * preview without saving: re-resolve welds + derived caches, re-bake VAOs, and reset the renderer's
     * cached animator (the procedural/non-procedural choice). The plain scalar reads (scale, texture,
     * culling...) already update every frame, so they don't go through here.
     */
    private void refresh()
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

    /** One component of the scale vector; a right click on any of the three resets the whole vector. */
    private UITrackpad component(ValueVector3f value, int axis)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            /* Edit a copy so the stored vector stays at its old value until set() notifies — otherwise the
             * undo handler would cache the already-mutated value and the change wouldn't be undoable. */
            Vector3f vector = new Vector3f(value.get());

            if (axis == 0) vector.x = v.floatValue();
            else if (axis == 1) vector.y = v.floatValue();
            else vector.z = v.floatValue();

            value.set(vector);
        });

        Vector3f vector = value.get();

        trackpad.setValue(axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z);
        trackpad.delayedInput();

        return UIValues.resettable(trackpad, () -> this.data.scale, this::fillGeneral);
    }

    /**
     * A copy/paste controller whose copy source and paste target get re-pointed at whichever entry's
     * context menu is being opened — the same "current selection" role the clip and replay lists give
     * their controllers, except here the selection only lives as long as the menu.
     */
    private class EntryClipboard
    {
        public final UICopyPasteController controller;

        private Supplier<MapType> source;
        private Consumer<MapType> target;

        public EntryClipboard(PresetManager manager, String copyPrefix)
        {
            this.controller = new UICopyPasteController(manager, copyPrefix)
                .supplier(() -> this.source == null ? null : this.source.get())
                .consumer((data, mouseX, mouseY) ->
                {
                    if (this.target != null)
                    {
                        this.target.accept(data);
                    }
                })
                .canCopy(() -> this.source != null)
                .canPaste(() -> UIModelEditorPanel.this.data != null && this.target != null);
        }

        public void aim(Supplier<MapType> source, Consumer<MapType> target)
        {
            this.source = source;
            this.target = target;
        }
    }
}
