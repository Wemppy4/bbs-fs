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
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
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
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
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
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseManager;
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
import java.util.function.Supplier;

/**
 * Model Editor — a proper data panel (tabs, right icon bar, save) over models. Each tab is an open model;
 * the picker in the icon bar chooses one. The editor area is split into a resizable settings pane on the
 * left (binding straight to the live model's {@link ModelConfig}, so edits show in the preview at once)
 * and the preview on the right. Models are assets, so create/rename/delete are intentionally off.
 *
 * <p>The sections themselves are built exactly once, in the constructor; opening a model only refills
 * their bodies. That's what keeps the fold state, the scroll position and the focused control alive
 * across an edit — a list add/remove refills just that list's container, never the whole pane.</p>
 *
 * <p>The preview ({@link UIModelEditorRenderer}) shows what the config describes: items and armor in
 * the slots, the first-person hands in a first-person view, the active slot's transform on a gizmo,
 * the bone a row names lit up under the cursor. Every bone picker has the viewport eyedropper, and
 * every plain field answers a right click with "reset".</p>
 */
public class UIModelEditorPanel extends UIDataDashboardPanel<ModelConfig>
{
    public UIScrollView general;
    public UIModelEditorRenderer renderer;
    public UISplitter splitter;

    /** The pane on the right: the bones and the welds, each a list with the picked entry's settings under it. */
    public UIScrollView bonesPane;
    public UISplitter bonesSplitter;

    private final ModelForm form = new ModelForm();

    /** The model id waiting for its instance to load (models load asynchronously). */
    private String pendingId;

    private static final CubeFace[] FACES = CubeFace.values();

    /** The live model instance the sections are bound to; null until it loads. */
    private ModelInstance bound;

    /** The armor section shows one region at a time. */
    private static final ArmorType[][] ARMOR_REGIONS =
    {
        {ArmorType.HELMET},
        {ArmorType.CHEST, ArmorType.LEFT_ARM, ArmorType.RIGHT_ARM},
        {ArmorType.LEGGINGS, ArmorType.LEFT_LEG, ArmorType.RIGHT_LEG},
        {ArmorType.LEFT_BOOT, ArmorType.RIGHT_BOOT}
    };

    private int armorRegion;

    /** Which hand the held items section shows. */
    private boolean offHand;

    /* Sections, built once. */
    private UISection generalSection;
    private UISection itemsSection;
    private UISection armorSection;
    private UISection firstPersonSection;
    private UISection lookAtSection;
    private UISection sneakingSection;
    private UISection weldsSection;
    private UISection[] sections;

    /* Their bodies, refilled per model or per list change. Every body made by body() is listed here. */
    private final List<UIElement> bodies = new ArrayList<>();
    private UIElement generalBody;
    private UIElement itemPanel;
    private UIElement armorBody;
    private UIElement firstPersonBody;
    private UIElement lookAtBody;
    private UIElement sneakingBody;

    /** The picked bone's / weld's settings, under their lists. */
    private UIElement bonePanel;
    private UIElement weldPanel;

    /* The "list + settings" blocks: held items, welds. The bone tree is one too, with its own list class. */
    private UITabStrip itemsTabs;
    private UIEntryList<ArmorSlotValue> itemList;
    private UIIcon dupeItem;
    private UIIcon removeItem;
    private UIModelBoneList bones;
    private UISearchList<String> bonesSearch;
    private UIEntryList<WeldValue> weldList;
    private UIIcon dupeWeld;
    private UIIcon removeWeld;

    /* The role dots of the bone tree: rightmost, a mirror bone is set; next to it, a picking override. */
    private static final int MARKER_MIRROR = Colors.A100 | Colors.CYAN;
    private static final int MARKER_PICKING = Colors.A100 | Colors.ORANGE;

    /** Set while fillSections() refills everything, so the bodies don't each trigger a re-layout. */
    private boolean bulkFill;

    /** The config the bodies were last filled from, to tell a new model from a refill of the same one. */
    private ModelConfig filled;

    /** Thumbnail in the preview's corner showing the model as it appears in UI slots (form pickers). */
    private UIElement miniPreview;

    private UIIcon folderIcon;
    private UIIcon historyIcon;
    private UIIcon animationIcon;
    private UIIcon equipmentIcon;
    private UIIcon firstPersonIcon;

    /** The slot the viewport gizmo edits, and the widgets currently standing for each slot. */
    private ArmorSlotValue activeSlot;
    private final Map<ArmorSlotValue, ModelSlotTarget> targets = new IdentityHashMap<>();

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

        this.general = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);

        this.renderer = new UIModelEditorRenderer()
            .target(() -> this.activeSlot == null ? null : this.targets.get(this.activeSlot))
            .onBoneClick(this::selectBone)
            .onViewChange(() ->
            {
                this.layoutPanes();
                this.resize();
            });
        this.renderer.form = this.form;

        /* Three panes: settings, the preview, the bones. The outer two keep at least 160px each, and
         * so does the preview between them — each splitter's range follows the editor's width and
         * the other pane. */
        Runnable relayout = () ->
        {
            this.layoutPanes();
            this.resize();
        };

        this.splitter = new UISplitter("model_editor.split", false, 200);
        this.splitter.measure(this.editor).range(160, () -> (float) (this.editor.area.w - 160 - this.bonesSplitter.getPixels())).onChange(relayout);

        this.bonesSplitter = new UISplitter("model_editor.bones_split", false, 180).fromEnd();
        this.bonesSplitter.measure(this.editor).range(160, () -> (float) (this.editor.area.w - 160 - this.splitter.getPixels())).onChange(relayout);

        this.bonesPane = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);
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

        this.editor.add(this.general, this.renderer, this.bonesPane, this.splitter, this.bonesSplitter);

        this.createSections();

        this.openOverlay.tooltip(UIKeys.FORMS_EDITOR_MODEL_PICK_MODEL);

        this.folderIcon = new UIIcon(Icons.FOLDER, (b) -> this.openModelFolder());
        this.folderIcon.tooltip(UIKeys.FORMS_CATEGORIES_CONTEXT_OPEN_MODEL_FOLDER);

        this.historyIcon = new UIIcon(Icons.UNDO, (b) -> this.openHistory());
        this.historyIcon.tooltip(UIKeys.MODEL_EDITOR_OPEN_HISTORY);

        this.animationIcon = new UIIcon(Icons.PLAY, (b) -> this.openAnimations());
        this.animationIcon.tooltip(UIKeys.MODEL_EDITOR_ANIMATION_PLAY);

        this.firstPersonIcon = new UIIcon(Icons.LOOKING, (b) -> this.renderer.setFirstPerson(!this.renderer.isFirstPerson()));
        this.firstPersonIcon.tooltip(UIKeys.MODEL_EDITOR_FIRST_PERSON_SHOW);

        this.equipmentIcon = new UIIcon(Icons.ARMOR_CHESTPLATE, (b) -> this.renderer.setEquipment(!this.renderer.hasEquipment()));
        this.equipmentIcon.tooltip(UIKeys.MODEL_EDITOR_PREVIEW_EQUIPMENT);

        this.actions()
            .action(this.folderIcon)
            .action(this.historyIcon)
            .action(this.animationIcon)
            .action(this.equipmentIcon, this.renderer::hasEquipment)
            .action(this.firstPersonIcon, this.renderer::isFirstPerson);

        this.mountLanding();

        this.add(new UIUndoKeys(this::undo, this::redo).full(this));

        this.registerKeybinds();

        this.fill(null);
    }

    /**
     * Three panes in the orbit view. In the first-person view the bones pane steps aside and the preview
     * becomes the game's frame: a rectangle of the game window's proportions, letterboxed into the room
     * past the settings — the hand sits where the game puts it (the lower right of the window), which a
     * narrower frame would cut off, and a wider one would misplace.
     */
    private void layoutPanes()
    {
        int splitWidth = this.splitter.getPixels();
        boolean firstPerson = this.renderer.isFirstPerson();
        int bonesWidth = firstPerson ? 0 : this.bonesSplitter.getPixels();

        this.general.relative(this.editor).x(0).y(0).w(splitWidth).h(1F);
        this.splitter.relative(this.editor).x(splitWidth).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);
        this.bonesPane.relative(this.editor).x(1F, -bonesWidth).y(0).w(bonesWidth).h(1F);
        this.bonesSplitter.relative(this.editor).x(1F, -bonesWidth).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);
        this.bonesPane.setVisible(!firstPerson);
        this.bonesSplitter.setVisible(!firstPerson);

        if (!firstPerson)
        {
            this.renderer.relative(this.editor).x(splitWidth).y(0).w(1F, -splitWidth - bonesWidth).h(1F);

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

        this.renderer.relative(this.editor).x(splitWidth + (roomW - w) / 2).y((roomH - h) / 2).w(w).h(h);
    }

    /** The first-person frame is sized in pixels off the editor's area, so it's laid out again after every pass. */
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


    private void registerKeybinds()
    {
        IKey category = UIKeys.MODEL_EDITOR_TITLE;
        Supplier<Boolean> open = () -> this.data != null;

        this.keys().register(Keys.MODEL_EDITOR_EXPAND_ALL, () -> this.setAllExpanded(true)).active(open).category(category);
        this.keys().register(Keys.MODEL_EDITOR_COLLAPSE_ALL, () -> this.setAllExpanded(false)).active(open).category(category);
        this.keys().register(Keys.MODEL_EDITOR_FIND_BONE, this::findBone).active(open).category(category);
        this.keys().register(Keys.MODEL_EDITOR_OPEN_HISTORY, this::openHistory).active(open).category(category);
    }

    private void setAllExpanded(boolean expanded)
    {
        for (UISection section : this.sections)
        {
            section.setExpanded(expanded);
        }

        this.resizeGeneral();
        this.resizeBones();
        UIUtils.playClick();
    }

    /** Ctrl+F: drop the caret straight into the bone tree's search box. */
    private void findBone()
    {
        this.getContext().focus(this.bonesSearch.search);
        UIUtils.playClick();
    }

    @Override
    public void render(UIContext context)
    {
        /* A fully solid dark backdrop over the whole panel so the dashboard background doesn't show through
         * the settings pane or behind the preview. deepSurface() is the same solid the mini preview uses. */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.deepSurface());

        super.render(context);
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
        this.fillSections(data);

        boolean open = data != null;

        for (UIIcon icon : new UIIcon[] {this.folderIcon, this.historyIcon, this.animationIcon, this.equipmentIcon, this.firstPersonIcon})
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
     * baked geometry don't track. Re-derive the config's caches, re-bake the instance and refill the sections
     * so the whole editor reflects the restored state.
     */
    private void afterUndo()
    {
        this.data.rebuild();
        this.refresh();
        this.fillSections(this.data);
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

    /** The slot the viewport gizmo is on; a first-person slot switches the preview to the first-person view, any other back. */
    private void activate(ArmorSlotValue slot, ModelSlotKind kind)
    {
        this.activeSlot = slot;
        this.renderer.setFirstPerson(kind.firstPerson);
    }

    /* Section scaffolding. Built once here; only the bodies below get refilled. */

    private void createSections()
    {
        this.generalSection = this.section(UIKeys.FORMS_EDITORS_GENERAL, true);
        this.generalBody = this.body();
        this.generalSection.fields.add(this.generalBody);

        /* Held items: one list at a time, the hand picked by a tab strip. */
        this.itemsSection = this.section(UIKeys.MODEL_EDITOR_ITEMS, false);
        this.itemsTabs = new UITabStrip(ScrollDirection.HORIZONTAL)
        {
            @Override
            protected boolean pressTab(int index, UIContext context)
            {
                this.select(index);

                return true;
            }
        };
        this.itemsTabs.fixed();
        this.itemsTabs.active(() -> this.offHand ? 1 : 0);
        this.itemsTabs.onSelect((index) ->
        {
            this.offHand = index == 1;
            this.itemList.deselect();
            this.fillItems();
        });
        this.itemsTabs.addTab(new UITextTab(UIKeys.MODEL_EDITOR_ITEMS_MAIN)).w(70).h(UIConstants.CONTROL_HEIGHT);
        this.itemsTabs.addTab(new UITextTab(UIKeys.MODEL_EDITOR_ITEMS_OFF)).w(70).h(UIConstants.CONTROL_HEIGHT);
        this.itemsTabs.h(UIConstants.CONTROL_HEIGHT);

        /* The slots of the shown hand as a list — the same shape as the welds — with the picked slot's
         * bone and transform under it; picking a slot also puts it on the viewport gizmo. */
        this.itemList = new UIEntryList<>((list) -> this.pickItem(), (slot) -> slot.isActive() ? slot.group.get() : "?");
        this.itemList.h(UIStringList.DEFAULT_HEIGHT * 4);
        this.itemList.context((menu) ->
        {
            ArmorSlotValue slot = this.itemList.getAtCursor(this.getContext());

            if (slot != null)
            {
                this.itemList.setCurrent(slot);
                this.pickItem();
                menu.action(Icons.DUPE, UIKeys.MODEL_EDITOR_ITEM_DUPLICATE, () -> this.duplicateItem(slot));
                menu.icon(MenuVerb.REMOVE, () -> this.removeItem(slot)).label(UIKeys.MODEL_EDITOR_ITEM_REMOVE);
            }
        });

        UIIcon addItem = new UIIcon(Icons.ADD, (b) -> this.addItem());

        addItem.tooltip(UIKeys.MODEL_EDITOR_ITEM_ADD);
        this.dupeItem = new UIIcon(Icons.DUPE, (b) -> this.duplicateItem(this.itemList.getCurrentFirst()));
        this.dupeItem.tooltip(UIKeys.MODEL_EDITOR_ITEM_DUPLICATE);
        this.removeItem = new UIIcon(Icons.REMOVE, (b) -> this.removeItem(this.itemList.getCurrentFirst()));
        this.removeItem.tooltip(UIKeys.MODEL_EDITOR_ITEM_REMOVE);

        this.itemPanel = this.body();
        this.itemsSection.fields.add(this.itemsTabs, this.strip(addItem, this.dupeItem, this.removeItem), this.itemList, this.itemPanel);

        /* Armor: one region at a time, its pieces below. */
        this.armorSection = this.section(UIKeys.MODEL_EDITOR_ARMOR, false);
        this.armorBody = this.body();

        UIIcons regions = new UIIcons((b) ->
        {
            this.armorRegion = b.getValue();
            this.fillArmor();
        });

        regions.add(Icons.ARMOR_HELMET, UIKeys.MODEL_EDITOR_ARMOR_HELMET);
        regions.add(Icons.ARMOR_CHESTPLATE, UIKeys.MODEL_EDITOR_ARMOR_CHEST);
        regions.add(Icons.ARMOR_LEGGINGS, UIKeys.MODEL_EDITOR_ARMOR_LEGGINGS);
        regions.add(Icons.ARMOR_BOOTS, UIKeys.MODEL_EDITOR_ARMOR_BOOTS);
        regions.setValue(this.armorRegion);

        this.armorSection.fields.add(regions, this.armorBody);

        this.firstPersonSection = this.section(UIKeys.MODEL_EDITOR_FIRST_PERSON, false);
        this.firstPersonBody = this.body();
        this.firstPersonSection.fields.add(this.firstPersonBody);

        this.lookAtSection = this.section(UIKeys.MODEL_EDITOR_LOOK_AT, false);
        this.lookAtBody = this.body();
        this.lookAtSection.fields.add(this.lookAtBody);

        this.sneakingSection = this.section(UIKeys.MODEL_EDITOR_SNEAKING, false);
        this.sneakingBody = this.body();
        this.sneakingSection.fields.add(this.sneakingBody);

        this.general.add(this.generalSection, this.itemsSection, this.armorSection, this.firstPersonSection, this.lookAtSection, this.sneakingSection);

        /* The right pane: the bone tree with the picked bone's settings under it — no header, it IS the
         * pane — and the welds section below. The tree takes whatever height the pane has left after the
         * rest; the ask is a floor, not the wish (a tall floor would refuse to give the welds their room). */
        this.bones = new UIModelBoneList((list) -> this.fillBone(), () -> this.data == null ? null : this.data.disabledBones, this::fillBone);
        this.bones.markers(this::boneMarkers, UIKeys.MODEL_EDITOR_BONES_LEGEND);
        this.bonesSearch = new UISearchList<>(this.bones);
        this.bonesSearch.label(UIKeys.GENERAL_SEARCH);
        this.bonesSearch.h(UIStringList.DEFAULT_HEIGHT * 8 - 8).expand();
        this.bonePanel = this.body();

        /* Welds: the add/duplicate/remove strip over the list, the replay list's idiom — now that a weld
         * is picked rather than edited inline, the strip's verbs have something to act on. The add icon
         * pastes a copied weld as a new one on right click. */
        this.weldsSection = this.section(UIKeys.MODEL_EDITOR_WELDS, false);
        this.weldList = new UIEntryList<>((list) -> this.fillWeld(), UIModelEditorPanel::weldName).broken((weld) -> this.diagnoseWeld(weld) != null);
        this.weldList.h(120);
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
        this.weldsSection.fields.add(this.strip(addWeld, this.dupeWeld, this.removeWeld), this.weldList, this.weldPanel);

        this.bonesPane.add(this.bonesSearch, this.bonePanel, this.weldsSection);

        this.sections = new UISection[]
        {
            this.generalSection,
            this.itemsSection,
            this.armorSection,
            this.firstPersonSection,
            this.lookAtSection,
            this.sneakingSection,
            this.weldsSection
        };
    }

    private UISection section(IKey title, boolean defaultExpanded)
    {
        UISection section = new UISection(title);

        section.setExpanded(defaultExpanded);

        return section;
    }

    /** A vertical container inside a section that gets emptied and refilled on its own; registered for clearBodies(). */
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

    /* Filling. fillSections() is the "a different model is open" path; the individual fillX() methods are
     * what a list mutation calls, so an add or a remove only touches its own container. */

    private void fillSections(ModelConfig config)
    {
        this.targets.clear();

        if (config != this.filled)
        {
            this.filled = config;
            this.activeSlot = null;
        }

        if (config == null)
        {
            /* The base class hides the whole editor pane when no model is open; empty the bodies too so
             * the sections don't keep the closed model's config (and its widgets) alive. */
            this.clearBodies();

            return;
        }

        this.bulkFill = true;

        try
        {
            this.fillGeneral();
            this.fillItems();
            this.fillArmor();
            this.fillFirstPerson();
            this.fillLookAt();
            this.fillSneaking();
            this.fillWelds();
            this.fillBones();
        }
        finally
        {
            this.bulkFill = false;
        }

        this.resizeGeneral();
        this.resizeBones();
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
            this.itemList.clear();
            this.fillBone();
            this.fillWeld();
            this.fillItem();
        }
        finally
        {
            this.bulkFill = false;
        }

        this.general.resize();
        this.bonesPane.resize();
    }

    /**
     * Re-layout the settings pane after a body changed height, keeping the scroll inside its new bounds.
     * Suppressed while every section is being refilled at once, so that costs one layout pass, not nine.
     */
    private void resizeGeneral()
    {
        if (this.bulkFill)
        {
            return;
        }

        this.general.resize();
        this.general.scroll.clamp();
    }

    /** Same, for the right pane. */
    private void resizeBones()
    {
        if (this.bulkFill)
        {
            return;
        }

        this.bonesPane.resize();
        this.bonesPane.scroll.clamp();
    }

    /** {@code base} with a "(n)" suffix when non-zero, so a folded section still shows how much it holds. */
    private void countTitle(UISection section, IKey base, int count)
    {
        section.title(count > 0 ? IKey.constant(base.get() + " (" + count + ")") : base);
    }

    private void fillGeneral()
    {
        ModelConfig config = this.data;

        UITrackpad uiScale = this.trackpad(() -> this.data.uiScale, null);

        uiScale.limit(config.uiScale).delayedInput();

        UITextbox poseGroup = UIValues.textbox(10000, () -> this.data.poseGroup);

        poseGroup.setText(config.poseGroup.get());

        UIBonePicker anchor = this.bonePicker(config.anchor::get, config.anchor::set, () -> {});

        UIValues.resettable(anchor, () -> this.data.anchor, anchor::refresh);

        UIButton texture = new UIButton(UIKeys.TEXTURE_PICK_TEXTURE, (b) -> UITexturePicker.open(this.getContext(), this.data.texture.get(), this.data.texture::set));

        UIValues.resettable(texture, () -> this.data.texture, null);

        this.generalBody.removeAll();
        this.generalBody.add(
            this.toggle(UIKeys.MODEL_EDITOR_PROCEDURAL, () -> this.data.procedural, this::refresh),
            this.toggle(UIKeys.MODEL_EDITOR_CULLING, () -> this.data.culling, null),
            this.toggle(UIKeys.MODEL_EDITOR_ON_CPU, () -> this.data.onCpu, this::refresh),
            UI.labelRow(UIKeys.MODEL_EDITOR_UI_SCALE, uiScale),
            UI.label(UIKeys.MODEL_EDITOR_SCALE), UI.row(this.component(config.scale, 0), this.component(config.scale, 1), this.component(config.scale, 2)),
            UI.labelRow(UIKeys.MODEL_EDITOR_POSE_GROUP, poseGroup),
            UI.labelRow(UIKeys.MODEL_EDITOR_ANCHOR, anchor),
            UI.labelRow(UIKeys.MODEL_EDITOR_TEXTURE, texture)
        );

        this.resizeGeneral();
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

        this.countTitle(this.lookAtSection, UIKeys.MODEL_EDITOR_LOOK_AT, config.lookAt.isActive() ? 1 : 0);
        this.resizeGeneral();
    }

    /* Attachment slots (items in hand, armor, first-person) — a bone plus a transform, the transform on the
     * viewport gizmo when the slot is the active one. */

    private ModelConfig.ItemSlotList itemsList()
    {
        return this.data == null ? null : this.offHand ? this.data.itemsOff : this.data.itemsMain;
    }

    private ModelSlotKind itemsKind()
    {
        return this.offHand ? ModelSlotKind.ITEM_OFF : ModelSlotKind.ITEM_MAIN;
    }

    /** The shown hand's slots; the list keeps its pick across a refill (slots are compared by identity). */
    private void fillItems()
    {
        ModelConfig config = this.data;

        if (config == null)
        {
            return;
        }

        ArmorSlotValue picked = this.itemList.getCurrentFirst();

        this.itemList.setList(new ArrayList<>(this.itemsList().getAllTyped()));

        if (picked != null)
        {
            this.itemList.setCurrent(picked);
        }

        this.countTitle(this.itemsSection, UIKeys.MODEL_EDITOR_ITEMS, config.getItemsMain().size() + config.getItemsOff().size());
        this.fillItem();
    }

    /** A slot picked in the list goes on the viewport gizmo, and its settings come up. */
    private void pickItem()
    {
        ArmorSlotValue slot = this.itemList.getCurrentFirst();

        if (slot != null)
        {
            this.activate(slot, this.itemsKind());
        }

        this.fillItem();
    }

    /** The picked slot's settings — its bone and transform; empty and disabled with nothing picked. */
    private void fillItem()
    {
        ArmorSlotValue picked = this.data == null ? null : this.itemList.getCurrentFirst();
        ArmorSlotValue slot = picked == null ? new ArmorSlotValue("") : picked;

        this.dupeItem.setEnabled(picked != null);
        this.removeItem.setEnabled(picked != null);

        this.itemPanel.removeAll();
        this.itemPanel.add(
            this.bonePicker(slot.group::get, slot.group::set, () ->
            {
                this.data.rebuild();
                this.fillItems();
            }),
            this.slotTransform(slot, this.itemsKind())
        );
        this.setEnabledDeep(this.itemPanel, picked != null);

        this.resizeGeneral();
    }

    /**
     * A slot's transform editor: edits go through the value's notify, so the undo handler catches them,
     * and the runtime reads a copy of the transform, so it's rebuilt after every step of a drag. Registered
     * as the slot's gizmo target — the viewport asks for the active slot's editor by the slot.
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

        ModelSlotTarget target = new ModelSlotTarget(slot, kind, transform);

        /* G/R/S start a gesture on the active slot without touching a handle — the way the arrows are
         * used everywhere else, with the handles hidden in the settings. Only the slot the viewport is
         * showing listens, or the keys would edit a slot that isn't on screen. */
        transform.hotkeyDrag(() -> this.renderer.buildGizmoDrag(target));
        transform.enableHotkeys(() -> this.activeSlot == slot && this.renderer.isFirstPerson() == kind.firstPerson);
        this.targets.put(slot, target);

        return transform;
    }

    /**
     * A slot's row: its head (the bone picker) and, once it has a bone, its transform. A click anywhere in
     * it makes the slot the active one — the one on the viewport gizmo — marked by a bar on the left; hovering
     * it lights the slot's bone up in the preview.
     */
    private UIElement slotEntry(ArmorSlotValue slot, ModelSlotKind kind, UIElement head)
    {
        UIElement entry = new UIElement()
        {
            @Override
            protected IUIElement childrenMouseClicked(UIContext context)
            {
                if (context.mouseButton == 0 && this.area.isInside(context))
                {
                    UIModelEditorPanel.this.activate(slot, kind);
                }

                return super.childrenMouseClicked(context);
            }

            @Override
            public void render(UIContext context)
            {
                if (UIModelEditorPanel.this.activeSlot == slot)
                {
                    context.batcher.box(this.area.x - 3, this.area.y, this.area.x - 1, this.area.ey(), BBSSettings.primaryColor(Colors.A100));
                }

                if (this.area.isInside(context))
                {
                    UIModelEditorPanel.this.renderer.highlight(slot.group.get());
                }

                super.render(context);
            }
        };

        entry.column(UIConstants.MARGIN).vertical().stretch();
        entry.add(head);

        if (slot.isActive())
        {
            entry.add(this.slotTransform(slot, kind));

            /* A block of fields wants air after it; a bare bone row reads as a list line and doesn't. */
            entry.marginBottom(6);
        }

        return entry;
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
        this.itemList.setCurrent(slot);
        this.fillItems();
        this.pickItem();
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

        if (this.activeSlot == slot)
        {
            this.activeSlot = null;
        }

        this.data.rebuild();
        this.itemList.deselect();
        this.fillItems();
    }

    /** A value group as copyable data, or null if it doesn't serialise to a map (nothing to copy then). */
    private MapType presetData(BaseValue value)
    {
        BaseType data = value.toData();

        return data.isMap() ? data.asMap() : null;
    }

    private void fillArmor()
    {
        ModelConfig config = this.data;

        if (config == null)
        {
            return;
        }

        this.armorBody.removeAll();

        for (ArmorType type : ARMOR_REGIONS[this.armorRegion])
        {
            ArmorSlotValue slot = config.armorSlots.slot(type);

            this.armorBody.add(this.slotEntry(slot, ModelSlotKind.ARMOR, this.slotHead(this.armorTypeLabel(type), slot, this::fillArmor)));
        }

        this.countTitle(this.armorSection, UIKeys.MODEL_EDITOR_ARMOR, config.getArmorSlots().size());
        this.resizeGeneral();
    }

    /** A labelled bone picker for a fixed slot; a pick rebuilds the config and refills the section, so the transform row follows the bone. */
    private UIElement slotHead(IKey label, ArmorSlotValue slot, Runnable refill)
    {
        return UI.labelRow(label, this.bonePicker(slot.group::get, slot.group::set, () ->
        {
            this.data.rebuild();
            refill.run();
        }));
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
        ModelConfig config = this.data;

        this.firstPersonBody.removeAll();
        this.firstPersonBody.add(
            this.slotEntry(config.fpMain, ModelSlotKind.FIRST_PERSON_MAIN, this.slotHead(UIKeys.MODEL_EDITOR_ITEMS_MAIN, config.fpMain, this::fillFirstPerson)),
            this.slotEntry(config.fpOffhand, ModelSlotKind.FIRST_PERSON_OFF, this.slotHead(UIKeys.MODEL_EDITOR_ITEMS_OFF, config.fpOffhand, this::fillFirstPerson))
        );

        this.countTitle(this.firstPersonSection, UIKeys.MODEL_EDITOR_FIRST_PERSON,
            (config.fpMain.isActive() ? 1 : 0) + (config.fpOffhand.isActive() ? 1 : 0));
        this.resizeGeneral();
    }

    /* The sneaking pose is picked from the model's pose presets rather than edited here. */

    private void fillSneaking()
    {
        ModelConfig config = this.data;
        boolean has = !config.sneakingPose.get().isEmpty();
        IEntity entity = this.renderer.getEntity();

        this.sneakingBody.removeAll();
        this.sneakingBody.add(new UIButton(has ? UIKeys.MODEL_EDITOR_SNEAKING_SET : UIKeys.MODEL_EDITOR_SNEAKING_PICK, (b) -> this.openPosePicker(config)));

        if (has)
        {
            this.sneakingBody.add(new UIButton(UIKeys.MODEL_EDITOR_SNEAKING_CLEAR, (b) ->
            {
                config.sneakingPose.set(new Pose());
                this.fillSneaking();
            }));
        }

        /* Preview only: the entity sneaks, so the animator applies the pose the way it does in play. */
        this.sneakingBody.add(new UIToggle(UIKeys.MODEL_EDITOR_SNEAKING_TRY, entity.isSneaking(), (t) -> entity.setSneaking(t.getValue())));

        this.countTitle(this.sneakingSection, UIKeys.MODEL_EDITOR_SNEAKING, has ? 1 : 0);
        this.resizeGeneral();
    }

    private void openPosePicker(ModelConfig config)
    {
        if (this.bound == null)
        {
            return;
        }

        String group = config.poseGroup.get();

        if (group.isEmpty())
        {
            group = this.form.model.get();
        }

        MapType poses = PoseManager.INSTANCE.getData(group);

        this.getContext().replaceContextMenu((menu) ->
        {
            for (String name : poses.keys())
            {
                menu.action(Icons.POSE, IKey.constant(name), () ->
                {
                    Pose pose = new Pose();

                    pose.fromData(poses.getMap(name));
                    config.sneakingPose.set(pose);
                    this.fillSneaking();
                });
            }
        });
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

    /** A bone clicked in the preview: pick it in the tree. */
    private void selectBone(String bone)
    {
        this.bonesSearch.filter("", true);
        this.bones.setCurrentScroll(bone);
        this.fillBone();
    }

    /**
     * The picked bone's settings; refilled after every change to them, from either the panel or the tree's
     * menu. With nothing picked the same fields stand empty and disabled, so the pane keeps its height and
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

        this.resizeBones();
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

        this.countTitle(this.weldsSection, UIKeys.MODEL_EDITOR_WELDS, config.welds.getList().size());
        this.fillWeld();
    }

    /**
     * The picked weld's settings. With nothing picked the same fields stand empty and disabled (bound to a
     * throwaway weld), so the pane keeps its height; the issue line is always there too, blank when the
     * weld resolves.
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
        this.weldPanel.add(
            UI.label(issue == null ? IKey.EMPTY : this.weldIssueText(issue), UIConstants.CONTROL_HEIGHT).labelAnchor(0, 0.5F).color(Colors.NEGATIVE, true),
            UI.row(this.bonePicker(weld.sourceBone::get, weld.sourceBone::set, this::refreshWelds), this.facePicker(weld.sourceFace, this::refreshWelds)),
            UI.row(this.bonePicker(weld.targetBone::get, weld.targetBone::set, this::refreshWelds), this.facePicker(weld.targetFace, this::refreshWelds)),
            UI.labelRow(UIKeys.MODEL_EDITOR_WELD_MAX_ANGLE, this.weldAngle(weld)),
            UI.labelRow(UIKeys.MODEL_EDITOR_WELD_SEAM_FALLOFF, this.weldFalloff(weld)),
            UI.labelRow(UIKeys.MODEL_EDITOR_WELD_PARENT_SHARE, this.weldShare(weld)),
            this.weldTwist(weld)
        );
        this.setEnabledDeep(this.weldPanel, picked != null);

        this.resizeBones();
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
