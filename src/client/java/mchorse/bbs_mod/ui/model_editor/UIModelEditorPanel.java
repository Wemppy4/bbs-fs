package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.cubic.model.config.ArmorSlotValue;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.model.config.WeldValue;
import mchorse.bbs_mod.cubic.weld.CubeFace;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.settings.values.ui.ValueStringMap;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.DataTab;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UIDataTabs;
import mchorse.bbs_mod.ui.film.utils.undo.UIUndoHistoryOverlay;
import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISimpleTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseManager;
import mchorse.bbs_mod.utils.presets.PresetManager;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
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
 * and the orbit preview on the right. Models are assets, so create/rename/delete are intentionally off.
 *
 * <p>The sections themselves are built exactly once, in the constructor; opening a model only refills
 * their bodies. That's what keeps the fold state, the scroll position and the focused control alive
 * across an edit — a list add/remove refills just that list's container, never the whole pane.</p>
 */
public class UIModelEditorPanel extends UIDataDashboardPanel<ModelConfig>
{
    public UIScrollView general;
    public UIFormRenderer renderer;
    public UIDraggable splitter;

    private final ModelForm form = new ModelForm();

    /** The model id whose instance we're waiting on; models load asynchronously, so the fill is deferred. */
    private String pendingId;
    private int splitWidth = 200;

    /** Cube faces in enum order; the face picker adds its icons in this order so the index maps straight back. */
    private static final CubeFace[] FACES = CubeFace.values();

    /** The live instance backing the current tab, kept so weld edits can re-resolve its bindings. */
    private ModelInstance bound;

    /** Working rows for the bone maps ({@code [from, to]} pairs); a pair with a blank key can't live in the
     *  map itself, so the rows are edited here and committed back to the value on every change. */
    private final List<String[]> flippedEntries = new ArrayList<>();
    private final List<String[]> pickingEntries = new ArrayList<>();

    /** Armor types grouped by body region, one row per region icon (helmet / chest+arms / legs / boots). */
    private static final ArmorType[][] ARMOR_REGIONS =
    {
        {ArmorType.HELMET},
        {ArmorType.CHEST, ArmorType.LEFT_ARM, ArmorType.RIGHT_ARM},
        {ArmorType.LEGGINGS, ArmorType.LEFT_LEG, ArmorType.RIGHT_LEG},
        {ArmorType.LEFT_BOOT, ArmorType.RIGHT_BOOT},
    };

    /** The armor region the icon row currently shows; its slots fill {@link #armorBody}. */
    private int armorRegion;

    /* The sections, built once and refilled — see the class docs. */
    private UISection generalSection;
    private UISection itemsSection;
    private UISection armorSection;
    private UISection firstPersonSection;
    private UISection lookAtSection;
    private UISection sneakingSection;
    private UISection mapsSection;
    private UISection weldsSection;
    private UISection bonesSection;

    /* The refillable bodies inside those sections. */
    private UIElement generalBody;
    private UIElement itemsMainBody;
    private UIElement itemsOffBody;
    private UIElement armorBody;
    private UIElement firstPersonBody;
    private UIElement lookAtBody;
    private UIElement sneakingBody;
    private UIElement flippedBody;
    private UIElement pickingBody;
    private UIElement weldsBody;
    private UIScrollView bonesBody;
    private UITextbox bonesSearch;

    /** The bone list's current filter, mirrored here because {@link UITextbox} only pushes it through the callback. */
    private String bonesQuery = "";

    /** Every section in {@link #general}, in display order — the collapse/expand keybinds walk this. */
    private UISection[] sections;

    /** True while {@link #fillSections} refills everything, collapsing the nine re-layouts into one. */
    private boolean bulkFill;

    /** Landing screen shown when the current tab has no model open. */
    private UIModelSelectionScreen selectionPanel;

    /** A small morph-style model thumbnail pinned to the top-right of the orbit viewport. */
    private UIElement miniPreview;

    /** Opens the current model's asset folder; enabled only while a model is open. */
    private UIIcon folderIcon;

    /** Opens the undo/redo history overlay; enabled only while a model is open. */
    private UIIcon historyIcon;

    /** Undo/redo over the model's {@link ModelConfig} value tree — reuses the form editor's diff handler. */
    private UIFormUndoHandler undoHandler;

    /** Configs we've already wired the undo pre-callback into (by identity), so a re-open doesn't stack it. */
    private final Set<ModelConfig> hookedConfigs = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Set for one {@link #fill} when re-binding to a reloaded instance, so the reload keeps the undo stack. */
    private boolean preserveUndo;

    /* Clipboards for the sub-list entries — copy/paste/presets, like every other editor's lists. */
    private final EntryClipboard slots = new EntryClipboard(PresetManager.MODEL_SLOTS, "_CopyModelSlot");
    private final EntryClipboard welds = new EntryClipboard(PresetManager.MODEL_WELDS, "_CopyModelWeld");

    public UIModelEditorPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.enableTabs();

        this.general = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);

        this.renderer = new UIFormRenderer();
        this.renderer.form = this.form;

        this.splitter = new UIDraggable((context) ->
        {
            this.splitWidth = MathUtils.clamp(context.mouseX - this.editor.area.x, 160, this.editor.area.w - 160);
            this.layoutPanes();
            this.resize();
        }).cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);

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

        this.editor.add(this.general, this.renderer, this.splitter);

        this.createSections();

        this.openOverlay.tooltip(UIKeys.FORMS_EDITOR_MODEL_PICK_MODEL, Direction.LEFT);

        this.folderIcon = new UIIcon(Icons.FOLDER, (b) -> this.openModelFolder());
        this.folderIcon.tooltip(UIKeys.FORMS_CATEGORIES_CONTEXT_OPEN_MODEL_FOLDER, Direction.LEFT);
        this.iconBar.add(this.folderIcon);

        this.historyIcon = new UIIcon(Icons.UNDO, (b) -> this.openHistory());
        this.historyIcon.tooltip(UIKeys.MODEL_EDITOR_OPEN_HISTORY, Direction.LEFT);
        this.iconBar.add(this.historyIcon);

        this.selectionPanel = new UIModelSelectionScreen(this);
        this.selectionPanel.relative(this).y(UIDataTabs.TABS_HEIGHT_PX).wTo(this.iconBar.area).h(1F, -UIDataTabs.TABS_HEIGHT_PX);
        this.add(this.selectionPanel);

        this.add(new UIModelEditorUndoKeys(this).full(this));

        this.registerKeybinds();

        this.fill(null);
    }

    private void layoutPanes()
    {
        this.general.relative(this.editor).x(0).y(0).w(this.splitWidth).h(1F);
        this.renderer.relative(this.editor).x(this.splitWidth).y(0).w(1F, -this.splitWidth).h(1F);
        this.splitter.relative(this.editor).x(this.splitWidth).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);
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
        UIUtils.playClick();
    }

    /** Ctrl+F: open the bone list and drop the caret straight into its search box. */
    private void findBone()
    {
        this.bonesSection.setExpanded(true);
        this.general.resize();

        /* The bone list is the last section, so scrolling to the end puts it in view. */
        this.general.scroll.scrollToEnd();
        this.getContext().focus(this.bonesSearch);
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
    public Icon getTabIcon(DataTab tab)
    {
        return tab != null && tab.dataId == null ? Icons.SEARCH : Icons.POSE;
    }

    @Override
    protected IKey getTitle()
    {
        return UIKeys.MODEL_EDITOR_TITLE;
    }

    @Override
    protected boolean shouldAutoOpenListOnFirstResize()
    {
        return false;
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

        this.seedMap(this.flippedEntries, data == null ? null : data.flippedParts);
        this.seedMap(this.pickingEntries, data == null ? null : data.pickingOverrides);

        this.fillSections(data);

        if (this.selectionPanel != null)
        {
            this.selectionPanel.setVisible(data == null);
        }

        if (this.folderIcon != null)
        {
            this.folderIcon.setEnabled(data != null);
        }

        if (this.historyIcon != null)
        {
            this.historyIcon.setEnabled(data != null);
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
     * baked geometry don't track. Re-derive the config's caches, re-bake the instance, re-seed the working
     * map rows and refill the sections so the whole editor reflects the restored state.
     */
    private void afterUndo()
    {
        this.data.rebuild();
        this.refresh();
        this.seedMap(this.flippedEntries, this.data.flippedParts);
        this.seedMap(this.pickingEntries, this.data.pickingOverrides);
        this.fillSections(this.data);
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

    private void seedMap(List<String[]> entries, ValueStringMap value)
    {
        entries.clear();

        if (value != null)
        {
            for (Map.Entry<String, String> entry : value.get().entrySet())
            {
                entries.add(new String[] {entry.getKey(), entry.getValue()});
            }
        }
    }

    @Override
    public void appear()
    {
        super.appear();

        /* No model open → the selection screen is up; refresh its list each time the panel is shown. */
        if (this.selectionPanel != null && this.selectionPanel.isVisible())
        {
            this.requestNames();
        }
    }

    private void openModelFolder()
    {
        String id = this.form.model.get();

        if (id != null && !id.isEmpty())
        {
            UIUtils.openFolder(BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + id + "/"));
        }
    }

    /* Section scaffolding. Built once here; only the bodies below get refilled. */

    private void createSections()
    {
        this.generalSection = this.section(UIKeys.FORMS_EDITORS_GENERAL, true);
        this.generalBody = this.body();
        this.generalSection.fields.add(this.generalBody);

        this.itemsSection = this.section(UIKeys.MODEL_EDITOR_ITEMS, false);
        this.itemsMainBody = this.body();
        this.itemsOffBody = this.body();
        this.itemsSection.fields.add(
            this.listHeader(UIKeys.MODEL_EDITOR_ITEMS_MAIN, UIKeys.MODEL_EDITOR_ITEM_ADD,
                () -> this.addItem(this.itemsMainList()), this.slots, (data) -> this.pasteNewItem(this.itemsMainList(), data)),
            this.itemsMainBody,
            this.listHeader(UIKeys.MODEL_EDITOR_ITEMS_OFF, UIKeys.MODEL_EDITOR_ITEM_ADD,
                () -> this.addItem(this.itemsOffList()), this.slots, (data) -> this.pasteNewItem(this.itemsOffList(), data)),
            this.itemsOffBody
        );

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

        this.mapsSection = this.section(UIKeys.MODEL_EDITOR_MAPS, false);
        this.flippedBody = this.body();
        this.pickingBody = this.body();
        this.mapsSection.fields.add(
            this.listHeader(UIKeys.MODEL_EDITOR_FLIPPED_PARTS, UIKeys.MODEL_EDITOR_MAP_ADD, () -> this.addMap(this.flippedEntries), null, null),
            this.flippedBody,
            this.listHeader(UIKeys.MODEL_EDITOR_PICKING_OVERRIDES, UIKeys.MODEL_EDITOR_MAP_ADD, () -> this.addMap(this.pickingEntries), null, null),
            this.pickingBody
        );

        this.weldsSection = this.section(UIKeys.MODEL_EDITOR_WELDS, false);
        this.weldsBody = this.body();
        this.weldsSection.fields.add(
            this.listHeader(IKey.EMPTY, UIKeys.MODEL_EDITOR_WELD_ADD, this::addWeld, this.welds, this::pasteNewWeld),
            this.weldsBody
        );

        this.bonesSection = this.section(UIKeys.MODEL_EDITOR_BONES, false);
        this.bonesBody = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);
        this.bonesBody.h(160);
        this.bonesSearch = new UITextbox(100, (query) ->
        {
            this.bonesQuery = query;
            this.fillBones();
        });
        this.bonesSearch.placeholder(UIKeys.GENERAL_SEARCH);
        this.bonesSection.fields.add(this.bonesSearch, this.bonesBody);

        this.sections = new UISection[]
        {
            this.generalSection,
            this.itemsSection,
            this.armorSection,
            this.firstPersonSection,
            this.lookAtSection,
            this.sneakingSection,
            this.mapsSection,
            this.weldsSection,
            this.bonesSection
        };

        this.general.add(this.sections);
    }

    private UISection section(IKey title, boolean defaultExpanded)
    {
        UISection section = new UISection(title);

        section.setExpanded(defaultExpanded);

        return section;
    }

    /** A vertical container inside a section that gets emptied and refilled on its own. */
    private UIElement body()
    {
        UIElement body = new UIElement();

        body.column(UIConstants.MARGIN).vertical().stretch();

        return body;
    }

    /**
     * A sub-list header: a label on the left and a compact "+" add button pinned to the right. Both the
     * header and the button open the list's clipboard menu on right click, so a copied entry can be
     * pasted in as a new one — the same copy/paste/presets trio the other editors' list toolbars have.
     */
    private UIElement listHeader(IKey label, IKey tooltip, Runnable add, EntryClipboard clipboard, Consumer<MapType> pasteNew)
    {
        UIIcon plus = new UIIcon(Icons.ADD, (b) -> add.run());

        plus.tooltip(tooltip, Direction.LEFT);
        plus.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);

        if (clipboard != null)
        {
            plus.context((menu) -> this.fillEntryMenu(menu, clipboard, null, pasteNew, null, null));
        }

        return UI.row(UIConstants.MARGIN, 0, UIConstants.CONTROL_HEIGHT,
            UI.label(label, UIConstants.CONTROL_HEIGHT).labelAnchor(0, 0.5F),
            plus
        );
    }

    /* Filling. fillSections() is the "a different model is open" path; the individual fillX() methods are
     * what a list mutation calls, so an add or a remove only touches its own container. */

    private void fillSections(ModelConfig config)
    {
        if (config == null)
        {
            /* The base class hides the whole editor pane when no model is open; empty the bodies too so
             * the sections don't keep the closed model's config (and its widgets) alive. */
            this.clearBodies();

            return;
        }

        /* A different model has different bones, so the old filter shouldn't carry over. */
        this.bonesQuery = "";
        this.bonesSearch.setText("");

        this.bulkFill = true;

        try
        {
            this.fillGeneral();
            this.fillItems();
            this.fillArmor();
            this.fillFirstPerson();
            this.fillLookAt();
            this.fillSneaking();
            this.fillMaps();
            this.fillWelds();
            this.fillBones();
        }
        finally
        {
            this.bulkFill = false;
        }

        this.resizeGeneral();
    }

    private void clearBodies()
    {
        this.generalBody.removeAll();
        this.itemsMainBody.removeAll();
        this.itemsOffBody.removeAll();
        this.armorBody.removeAll();
        this.firstPersonBody.removeAll();
        this.lookAtBody.removeAll();
        this.sneakingBody.removeAll();
        this.flippedBody.removeAll();
        this.pickingBody.removeAll();
        this.weldsBody.removeAll();
        this.bonesBody.removeAll();

        this.general.resize();
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

    /** {@code base} with a "(n)" suffix when non-zero, so a folded section still shows how much it holds. */
    private void countTitle(UISection section, IKey base, int count)
    {
        section.title(count > 0 ? IKey.constant(base.get() + " (" + count + ")") : base);
    }

    private void fillGeneral()
    {
        ModelConfig config = this.data;

        this.generalBody.removeAll();
        this.generalBody.add(
            this.toggleRefresh(UIKeys.MODEL_EDITOR_PROCEDURAL, config.procedural),
            this.toggle(UIKeys.MODEL_EDITOR_CULLING, config.culling),
            this.toggleRefresh(UIKeys.MODEL_EDITOR_ON_CPU, config.onCpu),
            this.labeledRow(UIKeys.MODEL_EDITOR_UI_SCALE, this.floatField(config.uiScale)),
            UI.label(UIKeys.MODEL_EDITOR_SCALE), UI.row(this.component(config.scale, 0), this.component(config.scale, 1), this.component(config.scale, 2)),
            this.labeledRow(UIKeys.MODEL_EDITOR_BEVEL, this.floatFieldRefresh(config.bevel)),
            this.labeledRow(UIKeys.MODEL_EDITOR_BEVEL_SEGMENTS, this.intFieldRefresh(config.bevelSegments)),
            this.labeledRow(UIKeys.MODEL_EDITOR_POSE_GROUP, this.stringField(config.poseGroup)),
            this.labeledRow(UIKeys.MODEL_EDITOR_ANCHOR, this.bonePicker(config.anchor::get, config.anchor::set, () -> {})),
            this.labeledRow(UIKeys.MODEL_EDITOR_TEXTURE, this.textureField(config.texture))
        );

        this.resizeGeneral();
    }

    private void fillLookAt()
    {
        ModelConfig config = this.data;

        this.lookAtBody.removeAll();
        this.lookAtBody.add(
            this.labeledRow(UIKeys.MODEL_EDITOR_LOOK_AT_HEAD, this.bonePicker(config.lookAt.head::get, config.lookAt.head::set, config::rebuild)),
            this.lookAtPitch(config),
            this.labeledRow(UIKeys.MODEL_EDITOR_LOOK_AT_LIMIT, this.lookAtLimit(config))
        );

        this.countTitle(this.lookAtSection, UIKeys.MODEL_EDITOR_LOOK_AT, config.lookAt.isActive() ? 1 : 0);
        this.resizeGeneral();
    }

    private UIToggle lookAtPitch(ModelConfig config)
    {
        return new UIToggle(UIKeys.MODEL_EDITOR_LOOK_AT_PITCH, config.lookAt.pitch.get(), (t) ->
        {
            config.lookAt.pitch.set(t.getValue());
            config.rebuild();
        });
    }

    private UITrackpad lookAtLimit(ModelConfig config)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            config.lookAt.headLimit.set(v.floatValue());
            config.rebuild();
        });

        trackpad.setValue(config.lookAt.headLimit.get());
        trackpad.delayedInput();

        return trackpad;
    }

    /* Attachment slots (items in hand, armor, first-person) — a bone plus a transform. */

    private UISimpleTransform slotTransform(ModelConfig config, ArmorSlotValue slot)
    {
        UISimpleTransform transform = new UISimpleTransform(config::rebuild);

        transform.setValue(slot.transform);
        transform.w(1F);

        return transform;
    }

    private ModelConfig.ItemSlotList itemsMainList()
    {
        return this.data == null ? null : this.data.itemsMain;
    }

    private ModelConfig.ItemSlotList itemsOffList()
    {
        return this.data == null ? null : this.data.itemsOff;
    }

    private void fillItems()
    {
        ModelConfig config = this.data;

        this.fillItemList(this.itemsMainBody, config.itemsMain);
        this.fillItemList(this.itemsOffBody, config.itemsOff);

        this.countTitle(this.itemsSection, UIKeys.MODEL_EDITOR_ITEMS, this.activeCount(config.itemsMain) + this.activeCount(config.itemsOff));
        this.resizeGeneral();
    }

    private void fillItemList(UIElement body, ModelConfig.ItemSlotList list)
    {
        body.removeAll();

        for (ArmorSlotValue slot : list.getAllTyped())
        {
            body.add(this.itemEntry(list, slot));
        }
    }

    private UIElement itemEntry(ModelConfig.ItemSlotList list, ArmorSlotValue slot)
    {
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.removeItem(list, slot));

        remove.tooltip(UIKeys.MODEL_EDITOR_ITEM_REMOVE, Direction.LEFT);
        remove.wh(20, UIConstants.CONTROL_HEIGHT);

        UIElement head = new UIElement();

        head.row(UIConstants.MARGIN).preferred(0);
        head.add(this.bonePicker(slot.group::get, slot.group::set, this.data::rebuild), remove);

        UIElement entry = UI.column(head, this.slotTransform(this.data, slot));

        entry.marginBottom(6);
        entry.context((menu) -> this.fillEntryMenu(menu, this.slots,
            () -> this.slotData(slot),
            (data) -> this.applySlot(slot, data),
            () -> this.duplicateItem(list, slot),
            () -> this.removeItem(list, slot)
        ));

        return entry;
    }

    private void addItem(ModelConfig.ItemSlotList list)
    {
        BaseValue.edit(list, (v) ->
        {
            list.add(new ArmorSlotValue(String.valueOf(list.getList().size())));
            list.sync();
        });

        this.data.rebuild();
        this.fillItems();
    }

    private void removeItem(ModelConfig.ItemSlotList list, ArmorSlotValue slot)
    {
        BaseValue.edit(list, (v) ->
        {
            list.getAllTyped().remove(slot);
            list.sync();
        });

        this.data.rebuild();
        this.fillItems();
    }

    private void duplicateItem(ModelConfig.ItemSlotList list, ArmorSlotValue slot)
    {
        MapType data = this.slotData(slot);

        BaseValue.edit(list, (v) ->
        {
            ArmorSlotValue copy = new ArmorSlotValue("");

            copy.fromData(data);
            list.add(list.getAllTyped().indexOf(slot) + 1, copy);
            list.sync();
        });

        this.data.rebuild();
        this.fillItems();
    }

    private void pasteNewItem(ModelConfig.ItemSlotList list, MapType data)
    {
        BaseValue.edit(list, (v) ->
        {
            ArmorSlotValue slot = new ArmorSlotValue("");

            slot.fromData(data);
            list.add(slot);
            list.sync();
        });

        this.data.rebuild();
        this.fillItems();
    }

    /** A slot's value tree as a preset — the config stores its rotations in degrees, {@code toData} converts. */
    private MapType slotData(ArmorSlotValue slot)
    {
        return this.presetData(slot);
    }

    /** A value group as preset data, or null if it doesn't serialise to a map (nothing to copy then). */
    private MapType presetData(BaseValue value)
    {
        BaseType data = value.toData();

        return data.isMap() ? data.asMap() : null;
    }

    private void applySlot(ArmorSlotValue slot, MapType data)
    {
        BaseValue.edit(slot, (v) -> slot.fromData(data));

        this.data.rebuild();
        this.fillSlotSections();
    }

    /** A pasted slot could sit in any of the three slot sections, so refresh all of them. */
    private void fillSlotSections()
    {
        this.fillItems();
        this.fillArmor();
        this.fillFirstPerson();
    }

    private int activeCount(ModelConfig.ItemSlotList list)
    {
        int count = 0;

        for (ArmorSlotValue slot : list.getAllTyped())
        {
            if (slot.isActive())
            {
                count++;
            }
        }

        return count;
    }

    private int armorCount(ModelConfig config)
    {
        int count = 0;

        for (ArmorType type : ArmorType.values())
        {
            ArmorSlotValue slot = config.armorSlots.slot(type);

            if (slot != null && slot.isActive())
            {
                count++;
            }
        }

        return count;
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
            this.armorBody.add(this.armorRow(config, type));
        }

        this.countTitle(this.armorSection, UIKeys.MODEL_EDITOR_ARMOR, this.armorCount(config));
        this.resizeGeneral();
    }

    private UIElement armorRow(ModelConfig config, ArmorType type)
    {
        ArmorSlotValue slot = config.armorSlots.slot(type);

        UIElement column = UI.column(
            this.labeledRow(this.armorTypeLabel(type), this.bonePicker(slot.group::get, slot.group::set, () ->
            {
                config.rebuild();
                this.fillArmor();
            }))
        );

        if (slot.isActive())
        {
            column.add(this.slotTransform(config, slot));
        }

        column.marginBottom(6);
        column.context((menu) -> this.fillEntryMenu(menu, this.slots,
            () -> this.slotData(slot),
            (data) -> this.applySlot(slot, data),
            null, null
        ));

        return column;
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
            this.fpRow(config, UIKeys.MODEL_EDITOR_ITEMS_MAIN, config.fpMain),
            this.fpRow(config, UIKeys.MODEL_EDITOR_ITEMS_OFF, config.fpOffhand)
        );

        this.countTitle(this.firstPersonSection, UIKeys.MODEL_EDITOR_FIRST_PERSON,
            (config.fpMain.isActive() ? 1 : 0) + (config.fpOffhand.isActive() ? 1 : 0));
        this.resizeGeneral();
    }

    private UIElement fpRow(ModelConfig config, IKey label, ArmorSlotValue slot)
    {
        UIElement column = UI.column(
            this.labeledRow(label, this.bonePicker(slot.group::get, slot.group::set, () ->
            {
                config.rebuild();
                this.fillFirstPerson();
            }))
        );

        if (slot.isActive())
        {
            column.add(this.slotTransform(config, slot));
        }

        column.marginBottom(6);
        column.context((menu) -> this.fillEntryMenu(menu, this.slots,
            () -> this.slotData(slot),
            (data) -> this.applySlot(slot, data),
            null, null
        ));

        return column;
    }

    /* Bone maps (flip mirror pairs, picking overrides) — rows of "from -> to" bones. */

    private void fillMaps()
    {
        ModelConfig config = this.data;

        this.fillMap(this.flippedBody, config.flippedParts, this.flippedEntries);
        this.fillMap(this.pickingBody, config.pickingOverrides, this.pickingEntries);

        this.countTitle(this.mapsSection, UIKeys.MODEL_EDITOR_MAPS, config.flippedParts.get().size() + config.pickingOverrides.get().size());
        this.resizeGeneral();
    }

    private void fillMap(UIElement body, ValueStringMap value, List<String[]> entries)
    {
        body.removeAll();

        for (String[] pair : entries)
        {
            body.add(this.mapEntry(value, entries, pair));
        }
    }

    private UIElement mapEntry(ValueStringMap value, List<String[]> entries, String[] pair)
    {
        Runnable commit = () ->
        {
            this.commitMap(value, entries);
            this.fillMaps();
        };

        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.removeMap(value, entries, pair));

        remove.tooltip(UIKeys.MODEL_EDITOR_MAP_REMOVE, Direction.LEFT);
        remove.wh(20, UIConstants.CONTROL_HEIGHT);

        UIElement row = new UIElement();

        row.row(UIConstants.MARGIN).preferred(0);
        row.add(
            this.bonePicker(() -> pair[0], (v) -> pair[0] = v, commit),
            this.arrowSeparator(),
            this.bonePicker(() -> pair[1], (v) -> pair[1] = v, commit),
            remove
        );

        row.context((menu) ->
        {
            menu.action(Icons.DUPE, UIKeys.MODEL_EDITOR_MAP_DUPLICATE, () -> this.duplicateMap(value, entries, pair));
            menu.action(Icons.REMOVE, UIKeys.MODEL_EDITOR_MAP_REMOVE, () -> this.removeMap(value, entries, pair));
        });

        return row;
    }

    private void addMap(List<String[]> entries)
    {
        entries.add(new String[] {"", ""});
        this.fillMaps();
    }

    private void removeMap(ValueStringMap value, List<String[]> entries, String[] pair)
    {
        entries.remove(pair);
        this.commitMap(value, entries);
        this.fillMaps();
    }

    private void duplicateMap(ValueStringMap value, List<String[]> entries, String[] pair)
    {
        entries.add(entries.indexOf(pair) + 1, new String[] {pair[0], pair[1]});
        this.commitMap(value, entries);
        this.fillMaps();
    }

    /** A non-interactive right-arrow drawn between the two bones of a map pair. */
    private UIIcon arrowSeparator()
    {
        UIIcon arrow = new UIIcon(Icons.ARROW_RIGHT, null);

        arrow.setEnabled(false);
        arrow.disabledColor = Colors.setA(Colors.WHITE, 0.5F);
        arrow.wh(12, UIConstants.CONTROL_HEIGHT);

        return arrow;
    }

    private void commitMap(ValueStringMap value, List<String[]> entries)
    {
        /* The map is mutated in place, so bracket it in a notify (via edit) for the undo handler to catch. */
        BaseValue.edit(value, (v) ->
        {
            Map<String, String> map = value.get();

            map.clear();

            for (String[] pair : entries)
            {
                if (!pair[0].trim().isEmpty())
                {
                    map.put(pair[0], pair[1]);
                }
            }
        });
    }

    /* The sneaking pose is picked from the model's pose presets rather than edited here. */

    private void fillSneaking()
    {
        ModelConfig config = this.data;
        boolean has = !config.sneakingPose.get().isEmpty();

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

    private void fillBones()
    {
        ModelConfig config = this.data;

        if (config == null)
        {
            return;
        }

        this.bonesBody.removeAll();

        if (this.bound != null)
        {
            ValueStringKeys hidden = config.disabledBones;
            String filter = this.bonesQuery.trim().toLowerCase();

            for (String bone : this.bound.getModel().getGroupKeysInHierarchyOrder())
            {
                if (filter.isEmpty() || bone.toLowerCase().contains(filter))
                {
                    this.bonesBody.add(this.boneToggle(bone, hidden));
                }
            }
        }

        this.bonesBody.resize();
        this.countTitle(this.bonesSection, UIKeys.MODEL_EDITOR_BONES, config.disabledBones.get().size());
    }

    private UIToggle boneToggle(String bone, ValueStringKeys hidden)
    {
        return new UIToggle(IKey.raw(bone), !hidden.get().contains(bone), (t) ->
        {
            /* The set is mutated in place, so bracket it in a notify (via edit) for the undo handler to catch. */
            BaseValue.edit(hidden, (v) ->
            {
                if (t.getValue())
                {
                    hidden.get().remove(bone);
                }
                else
                {
                    hidden.get().add(bone);
                }
            });

            this.countTitle(this.bonesSection, UIKeys.MODEL_EDITOR_BONES, hidden.get().size());
        });
    }

    private void fillWelds()
    {
        ModelConfig config = this.data;

        this.weldsBody.removeAll();

        for (WeldValue weld : config.welds.getAllTyped())
        {
            this.weldsBody.add(this.weldEntry(config, weld));
        }

        this.countTitle(this.weldsSection, UIKeys.MODEL_EDITOR_WELDS, config.welds.getList().size());
        this.resizeGeneral();
    }

    private UIElement weldEntry(ModelConfig config, WeldValue weld)
    {
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.removeWeld(weld));

        remove.tooltip(UIKeys.MODEL_EDITOR_WELD_REMOVE, Direction.LEFT);
        remove.wh(20, UIConstants.CONTROL_HEIGHT);

        UIElement angle = new UIElement();

        angle.row(UIConstants.MARGIN).preferred(0);
        angle.add(this.weldAngle(weld), remove);

        UIElement entry = UI.column(
            UI.row(this.bonePicker(weld.sourceBone::get, weld.sourceBone::set, this::invalidateWelds), this.facePicker(weld.sourceFace, this::invalidateWelds)),
            UI.row(this.bonePicker(weld.targetBone::get, weld.targetBone::set, this::invalidateWelds), this.facePicker(weld.targetFace, this::invalidateWelds)),
            UI.label(UIKeys.MODEL_EDITOR_WELD_MAX_ANGLE),
            angle,
            UI.label(UIKeys.MODEL_EDITOR_WELD_SEAM_FALLOFF),
            this.weldFalloff(weld)
        );

        entry.marginBottom(6);
        entry.context((menu) -> this.fillEntryMenu(menu, this.welds,
            () -> this.presetData(weld),
            (data) -> this.applyWeld(weld, data),
            () -> this.duplicateWeld(weld),
            () -> this.removeWeld(weld)
        ));

        return entry;
    }

    private UIButton bonePicker(Supplier<String> get, Consumer<String> set, Runnable onChange)
    {
        UIButton[] ref = new UIButton[1];
        UIButton button = new UIButton(this.boneLabel(get.get()), (b) ->
        {
            if (this.bound == null)
            {
                return;
            }

            String current = get.get();

            this.getContext().replaceContextMenu((menu) ->
            {
                menu.action(Icons.REMOVE, UIKeys.GENERAL_NONE, current == null || current.isEmpty(), () -> this.pickBone(ref[0], set, onChange, ""));

                for (String bone : this.bound.getModel().getGroupKeysInHierarchyOrder())
                {
                    menu.action(Icons.LIMB, IKey.constant(bone), bone.equals(current), () -> this.pickBone(ref[0], set, onChange, bone));
                }
            });
        });

        ref[0] = button;

        return button;
    }

    private void pickBone(UIButton button, Consumer<String> set, Runnable onChange, String bone)
    {
        set.accept(bone);
        button.label = this.boneLabel(bone);
        onChange.run();
    }

    private IKey boneLabel(String bone)
    {
        return bone == null || bone.isEmpty() ? UIKeys.MODEL_EDITOR_PICK_BONE : IKey.raw(bone);
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
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            weld.maxAngle.set(v.floatValue());
            this.invalidateWelds();
        });

        trackpad.setValue(weld.maxAngle.get());
        trackpad.delayedInput();

        return trackpad;
    }

    private UITrackpad weldFalloff(WeldValue weld)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            weld.seamFalloff.set(v.floatValue());
            this.invalidateWelds();
        });

        trackpad.limit(0F, 1F).increment(0.05F);
        trackpad.setValue(weld.seamFalloff.get());
        trackpad.delayedInput();

        return trackpad;
    }

    private void addWeld()
    {
        ModelConfig config = this.data;

        BaseValue.edit(config.welds, (v) ->
        {
            config.welds.add(new WeldValue(String.valueOf(config.welds.getList().size())));
            config.welds.sync();
        });

        this.refresh();
        this.fillWelds();
    }

    private void removeWeld(WeldValue weld)
    {
        ModelConfig config = this.data;

        BaseValue.edit(config.welds, (v) ->
        {
            config.welds.getAllTyped().remove(weld);
            config.welds.sync();
        });

        this.refresh();
        this.fillWelds();
    }

    private void duplicateWeld(WeldValue weld)
    {
        ModelConfig config = this.data;

        BaseValue.edit(config.welds, (v) ->
        {
            WeldValue copy = new WeldValue("");

            copy.fromData(weld.toData());
            config.welds.add(config.welds.getAllTyped().indexOf(weld) + 1, copy);
            config.welds.sync();
        });

        this.refresh();
        this.fillWelds();
    }

    private void pasteNewWeld(MapType data)
    {
        ModelConfig config = this.data;

        BaseValue.edit(config.welds, (v) ->
        {
            WeldValue weld = new WeldValue("");

            weld.fromData(data);
            config.welds.add(weld);
            config.welds.sync();
        });

        this.refresh();
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

    /**
     * The shared entry menu: duplicate, copy, paste and presets, then remove. {@code source} being null
     * means the menu hangs off an "add" button (nothing to copy from), {@code duplicate}/{@code remove}
     * being null mean the entry is a fixed slot rather than a list row.
     */
    private void fillEntryMenu(ContextMenuManager menu, EntryClipboard clipboard, Supplier<MapType> source, Consumer<MapType> target, Runnable duplicate, Runnable remove)
    {
        clipboard.aim(source, target);

        UIContext context = this.getContext();

        if (duplicate != null)
        {
            menu.action(Icons.DUPE, UIKeys.GENERAL_DUPE, duplicate);
        }

        if (source != null)
        {
            menu.action(Icons.COPY, UIKeys.GENERAL_COPY, clipboard.controller::copy);
        }

        if (clipboard.controller.canPaste())
        {
            menu.action(Icons.PASTE, UIKeys.GENERAL_PASTE, () -> clipboard.controller.paste(context.mouseX, context.mouseY));
        }

        menu.action(Icons.MORE, UIKeys.GENERAL_PRESETS, () -> clipboard.controller.openPresets(context, context.mouseX, context.mouseY));

        if (remove != null)
        {
            menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, remove);
        }
    }

    private UIElement labeledRow(IKey label, UIElement widget)
    {
        return UI.labelRow(label, widget);
    }

    private UIToggle toggle(IKey label, ValueBoolean value)
    {
        return new UIToggle(label, value.get(), (t) -> value.set(t.getValue()));
    }

    /** A toggle for a setting that changes the render path/baked geometry (procedural, on_cpu) — refreshes. */
    private UIToggle toggleRefresh(IKey label, ValueBoolean value)
    {
        return new UIToggle(label, value.get(), (t) ->
        {
            value.set(t.getValue());
            this.refresh();
        });
    }

    /**
     * Rebuild the live instance's baked state so a config edit that changed the render path or geometry
     * shows in the preview without saving: re-resolve welds + derived caches, re-apply the bevel, re-bake
     * VAOs, and reset the renderer's cached animator (the procedural/non-procedural choice). The plain
     * scalar reads (scale, texture, culling...) already update every frame, so they don't go through here.
     */
    private void refresh()
    {
        if (this.bound == null)
        {
            return;
        }

        this.bound.invalidateWelds();
        this.bound.applyBevel();
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

    private UITrackpad floatField(ValueFloat value)
    {
        UITrackpad trackpad = new UITrackpad((v) -> value.set(v.floatValue()));

        trackpad.limit(value.getMin(), value.getMax()).delayedInput();
        trackpad.setValue(value.get());

        return trackpad;
    }

    /** A float field for a setting that changes baked geometry (bevel) — refreshes like {@link #toggleRefresh}. */
    private UITrackpad floatFieldRefresh(ValueFloat value)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            value.set(v.floatValue());
            this.refresh();
        });

        trackpad.limit(value.getMin(), value.getMax()).delayedInput();
        trackpad.setValue(value.get());

        return trackpad;
    }

    private UITrackpad intFieldRefresh(ValueInt value)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            value.set(v.intValue());
            this.refresh();
        });

        trackpad.limit(value.getMin(), value.getMax()).integer().delayedInput();
        trackpad.setValue(value.get());

        return trackpad;
    }

    private UITextbox stringField(ValueString value)
    {
        UITextbox textbox = new UITextbox(10000, value::set);

        textbox.setText(value.get());

        return textbox;
    }

    private UIButton textureField(ValueLink value)
    {
        return new UIButton(UIKeys.TEXTURE_PICK_TEXTURE, (b) -> UITexturePicker.open(this.getContext(), value.get(), value::set));
    }

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

        return trackpad;
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
