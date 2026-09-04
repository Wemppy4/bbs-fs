package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.config.ArmorSlotValue;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.model.config.WeldValue;
import mchorse.bbs_mod.cubic.weld.CubeFace;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
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
import mchorse.bbs_mod.ui.framework.elements.utils.UITabStrip;
import mchorse.bbs_mod.ui.framework.elements.utils.UITextTab;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The config editor of the model panel: everything the model's {@link ModelConfig} says, laid out
 * over the preview it shares with the panel's other editor.
 *
 * <p>A strip of icon tabs over one page at a time ({@link Tab}): the general settings, the bones,
 * the welds, the armor, the held items, the first-person hands, the poses. The page that's open
 * decides what the preview shows ({@link #applyPreview()}) — the armor is worn on the armor page,
 * the items held on the items page, the first-person view is the first-person page, the model
 * sneaks on the poses page — so there is nothing to toggle by hand.</p>
 *
 * <p>The pages are built exactly once, in the constructor; opening a model only refills their
 * bodies ({@link #fill(ModelConfig)}). That's what keeps the fold state, the scroll position and
 * the focused control alive across an edit — a list add/remove refills just that list's container,
 * never the whole page.</p>
 *
 * <p>What is edited belongs to the panel — the open config, the live model instance behind it, the
 * preview, the undo over all of it — and this editor only says what the pages are and keeps them in
 * step with it. Its keybinds are its own, so they go quiet by themselves while the panel is showing
 * its other editor.</p>
 */
public class UIModelConfigEditor extends UIElement
{
    /** The pages, in the order of their tabs. */
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

    private static final Tab[] TABS = Tab.values();

    /** The tab strip's height — the dock stacks' one, since the strip is drawn like theirs. */
    private static final int TABS_HEIGHT = 20;

    /** The page that was open last; kept across models and across leaving and re-entering the panel. */
    private static Tab lastTab = Tab.GENERAL;

    private static final CubeFace[] FACES = CubeFace.values();

    /* How a cube's side reads in the UI: the side picker's buttons, and the icon a weld row draws in
     * place of the side's name. Both are indexed by {@link CubeFace}, so the order is the enum's. */
    private static final Icon[] FACE_ICONS = {Icons.FORWARD, Icons.BACKWARD, Icons.ARROW_RIGHT, Icons.ARROW_LEFT, Icons.ARROW_UP, Icons.ARROW_DOWN};
    private static final IKey[] FACE_LABELS = {
        UIKeys.MODEL_EDITOR_FACE_FRONT, UIKeys.MODEL_EDITOR_FACE_BACK, UIKeys.MODEL_EDITOR_FACE_RIGHT,
        UIKeys.MODEL_EDITOR_FACE_LEFT, UIKeys.MODEL_EDITOR_FACE_TOP, UIKeys.MODEL_EDITOR_FACE_BOTTOM
    };

    /** The icon a side is shown by; null when the name doesn't name a side (an unset face). */
    static Icon faceIcon(String face)
    {
        CubeFace value = CubeFace.fromName(face);

        return value == null ? null : FACE_ICONS[value.ordinal()];
    }

    /* The role dots of the bone tree: rightmost, a mirror bone is set; next to it, a picking override. */
    private static final int MARKER_MIRROR = Colors.A100 | Colors.CYAN;
    private static final int MARKER_PICKING = Colors.A100 | Colors.ORANGE;

    /** The panel this editor lives in: what it edits, and the preview it edits it against. */
    private final UIModelEditorPanel modelPanel;

    public UITabStrip tabs;

    private final UIScrollView[] pages = new UIScrollView[TABS.length];

    /** The config the pages are bound to; the panel's, handed over on every fill. */
    private ModelConfig data;

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
    private UIWeldList weldList;
    private UIIcon dupeWeld;
    private UIIcon removeWeld;

    /** The poses page: the config's two poses picked by a tab strip, the editor of the picked one under it. */
    private UITabStrip poseTabs;
    private UIModelPoseEditor poseEditor;

    /** Which pose the poses page shows: the default one, or the sneaking one. */
    private boolean defaultPose;

    /** Whether the pose editor is in its two-column arrangement, which this pane's width decides. */
    private boolean wide;

    /** Set while fill() refills everything, so the bodies don't each trigger a re-layout. */
    private boolean bulkFill;

    private final EntryClipboard welds = new EntryClipboard(PresetManager.MODEL_WELDS, "_CopyModelWeld");

    public UIModelConfigEditor(UIModelEditorPanel panel)
    {
        this.modelPanel = panel;

        this.createTabs();
        this.createPages();
        this.showPage(lastTab);
        this.registerKeybinds();
    }

    /** The live model instance behind the open config — the panel's, asked fresh: a reload swaps it. */
    private ModelInstance instance()
    {
        return this.modelPanel.getInstance();
    }

    /**
     * What the preview shows while this editor is the open one: the armor is worn only on the armor
     * page and the items held only on the items page (so each is seen against the bare model), the
     * first-person page is the first-person view, and the model sneaks on the poses page — so the
     * sneaking pose is what's being edited, the way the animator applies it in play.
     */
    public void applyPreview()
    {
        UIModelEditorRenderer renderer = this.modelPanel.renderer;

        renderer.setFirstPerson(lastTab == Tab.FIRST_PERSON);
        renderer.setEquipment(lastTab == Tab.ARMOR, lastTab == Tab.ITEMS);
        renderer.getEntity().setSneaking(lastTab == Tab.POSES && !this.defaultPose);
    }

    /**
     * The pose editor's arrangement follows this pane's width — two columns once it's wide enough,
     * the form editor's rule. Done as the width lands and before the children lay themselves out, so
     * the new arrangement is what they measure against.
     */
    @Override
    protected void afterResizeApplied()
    {
        boolean wide = this.area.w > UIPoseEditor.WIDE_WIDTH;

        if (this.wide != wide)
        {
            this.wide = wide;
            this.poseEditor.buildLayout(wide);
        }

        super.afterResizeApplied();
    }

    /** The keys of the pages; registered here, so they go quiet while another editor is up. */
    private void registerKeybinds()
    {
        IKey category = UIKeys.MODEL_EDITOR_TITLE;
        Supplier<Boolean> open = () -> this.data != null;

        this.keys().register(Keys.MODEL_EDITOR_NEXT_TAB, () -> this.cycleTab(1)).active(open).category(category);
        this.keys().register(Keys.MODEL_EDITOR_PREV_TAB, () -> this.cycleTab(-1)).active(open).category(category);
        this.keys().register(Keys.MODEL_EDITOR_EXPAND_ALL, () -> this.setAllExpanded(true)).active(open).category(category);
        this.keys().register(Keys.MODEL_EDITOR_COLLAPSE_ALL, () -> this.setAllExpanded(false)).active(open).category(category);
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
                UIModelConfigEditor.this.openTab(TABS[index]);

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

        this.tabs.relative(this).x(0).y(0).w(1F).h(TABS_HEIGHT);
        this.add(this.tabs);

        for (Tab tab : TABS)
        {
            UIScrollView page = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);

            page.relative(this).x(0).y(TABS_HEIGHT).w(1F).h(1F, -TABS_HEIGHT);
            this.pages[tab.ordinal()] = page;
            this.add(page);
        }
    }

    private UIScrollView page(Tab tab)
    {
        return this.pages[tab.ordinal()];
    }

    /** A page's tab button, for what points at one from outside (the tour). */
    public UIElement getTab(Tab tab)
    {
        return this.tabs.getTab(tab.ordinal());
    }

    /** Open a page: it's the one shown, and the preview follows it. */
    private void openTab(Tab tab)
    {
        this.showPage(tab);
        this.modelPanel.refreshPreview();
    }

    /** Show a page and nothing else — the preview is the panel's to sync, and it does it around this. */
    private void showPage(Tab tab)
    {
        lastTab = tab;

        for (Tab other : TABS)
        {
            this.page(other).setVisible(other == tab);
        }
    }

    private void cycleTab(int direction)
    {
        this.openTab(TABS[Math.floorMod(lastTab.ordinal() + direction, TABS.length)]);
        UIUtils.playClick();
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

    /** Ctrl+F: the bones page, with the caret in the tree's search box. */
    public void findBone()
    {
        this.openTab(Tab.BONES);
        this.getContext().focus(this.bonesSearch.search);
        UIUtils.playClick();
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
        this.weldList = new UIWeldList((list) -> this.fillWeld());
        this.weldList.broken((weld) -> this.diagnoseWeld(weld) != null);
        this.weldList.h(UIWeldList.ROW_HEIGHT * 4).expand();
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
        this.page(Tab.WELDS).add(UI.strip(addWeld, this.dupeWeld, this.removeWeld), this.weldList, this.weldPanel);

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

        this.page(Tab.ITEMS).add(this.itemsTabs, UI.strip(addItem, this.dupeItem, this.removeItem), this.items.list, this.items.panel);

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
            this.modelPanel.syncPreview();
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

            return target == null ? null : this.modelPanel.renderer.buildGizmoDrag(target);
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

    /* Filling. fill() is the "a different model is open" path; the individual fillX() methods are
     * what a list mutation calls, so an add or a remove only touches its own container. */

    public void fill(ModelConfig config)
    {
        this.data = config;

        if (config == null)
        {
            /* The panel hides the whole editor pane when no model is open; empty the bodies too so
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
            this.toggle(UIKeys.MODEL_EDITOR_PROCEDURAL, () -> this.data.procedural, this.modelPanel::refresh),
            this.toggle(UIKeys.MODEL_EDITOR_CULLING, () -> this.data.culling, null),
            this.toggle(UIKeys.MODEL_EDITOR_ON_CPU, () -> this.data.onCpu, this.modelPanel::refresh)
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
            this.panel = UIModelConfigEditor.this.body();
        }

        /** A block without a list: {@code fixed} names the one slot it edits, asked on every fill. */
        public SlotBlock(Function<ArmorSlotValue, ModelSlotKind> kinds, Supplier<ArmorSlotValue> fixed)
        {
            this.kinds = kinds;
            this.labels = null;
            this.source = null;
            this.fixed = fixed;

            this.list = null;
            this.panel = UIModelConfigEditor.this.body();
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
            if (UIModelConfigEditor.this.data == null)
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

            this.list.setList(UIModelConfigEditor.this.data == null ? new ArrayList<>() : new ArrayList<>(this.source.get()));

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
            UIPropTransform transform = UIModelConfigEditor.this.slotTransform(slot, kind);

            this.target = picked == null ? null : new ModelSlotTarget(slot.group.get(), kind, transform);

            this.panel.removeAll();
            this.panel.add(
                UIModelConfigEditor.this.bonePicker(slot.group::get, slot.group::set, () ->
                {
                    UIModelConfigEditor.this.data.rebuild();
                    this.refill();
                }),
                transform
            );
            UIUtils.setEnabledDeep(this.panel, picked != null);

            if (this.onFill != null)
            {
                this.onFill.run();
            }

            UIModelConfigEditor.this.resizePage(UIModelConfigEditor.this.tabOf(this));
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
     * The panel asks, and only while this editor is the open one.
     */
    public ModelSlotTarget shownTarget()
    {
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

            return target == null ? null : this.modelPanel.renderer.buildGizmoDrag(target);
        });
        transform.enableHotkeys(() ->
        {
            ModelSlotTarget target = this.shownTarget();

            return target != null && target.editor() == transform && this.modelPanel.renderer.isFirstPerson() == kind.firstPerson;
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
        this.poseEditor.setPose(pose.get(), this.instance().getPoseGroup());
        this.poseEditor.fillGroups(this.instance().getModel(), this.instance().getFlippedParts(), true, this.instance().getDisabledBones());

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

        this.bones.fill(this.instance() == null ? null : this.instance().getModel());

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
    public boolean selectBone(String bone)
    {
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
        UIUtils.setEnabledDeep(this.bonePanel, picked != null);

        this.resizePage(Tab.BONES);
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
        UIUtils.setEnabledDeep(this.weldPanel, picked != null);

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
                    UIModelConfigEditor.this.modelPanel.renderer.highlight(get.get());
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
            if (this.instance() != null)
            {
                menu.bones(this.instance().getModel(), null).none().set(get.get());
            }
        });
        picker.viewport(this.modelPanel.renderer);

        return picker;
    }

    private UIIcons facePicker(ValueString value, Runnable onChange)
    {
        UIIcons icons = new UIIcons((b) ->
        {
            value.set(FACES[b.getValue()].name().toLowerCase());
            onChange.run();
        });

        for (int i = 0; i < FACES.length; i++)
        {
            icons.add(FACE_ICONS[i], FACE_LABELS[i]);
        }

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

        this.modelPanel.refresh();
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

        this.modelPanel.refresh();
        this.weldList.deselect();
        this.fillWelds();
    }

    private void applyWeld(WeldValue weld, MapType data)
    {
        BaseValue.edit(weld, (v) -> weld.fromData(data));

        this.modelPanel.refresh();
        this.fillWelds();
    }

    private void invalidateWelds()
    {
        if (this.instance() != null)
        {
            this.instance().invalidateWelds();
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
        if (this.instance() == null || !(this.instance().getModel() instanceof Model model))
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
                .canPaste(() -> UIModelConfigEditor.this.data != null && this.target != null);
        }

        public void aim(Supplier<MapType> source, Consumer<MapType> target)
        {
            this.source = source;
            this.target = target;
        }
    }
}
