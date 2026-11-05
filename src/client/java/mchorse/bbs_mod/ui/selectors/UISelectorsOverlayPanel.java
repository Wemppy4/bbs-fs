package mchorse.bbs_mod.ui.selectors;

import com.mojang.brigadier.StringReader;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.selectors.EntitySelector;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIEntryListEditor;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.Identifier;

import java.util.List;

public class UISelectorsOverlayPanel extends UIOverlayPanel
{
    /** Width of the list side, the way the settings panel splits itself. */
    public static final int SIDE_WIDTH = 150;

    public UISelectorList selectors;
    public UIEntryListEditor<EntitySelector> selectorsEditor;

    public UIScrollView column;
    public UIToggle enabled;
    public UINestedEdit form;
    public UITextbox entity;
    public UITextbox name;
    public UITextarea<TextLine> nbt;

    private EntitySelector current;

    public UISelectorsOverlayPanel()
    {
        super(UIKeys.SELECTORS_TITLE);
        this.wh(430, 300);

        this.selectors = new UISelectorList((l) -> this.setSelector(l.get(0), false));
        this.selectors.setList(BBSModClient.getSelectors().selectors);
        this.selectors.update();

        this.enabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) ->
        {
            this.current.enabled = b.getValue();

            BBSModClient.getSelectors().update();
        });

        this.form = new UINestedEdit((editing) ->
        {
            UIFormPalette.open(this.getParent(UIOverlay.class), editing, this.current.form, true, (form) ->
            {
                this.current.form = FormUtils.copy(form);

                BBSModClient.getSelectors().update();
            });
        });
        this.entity = new UITextbox(100, (t) ->
        {
            String id = t.trim();

            try
            {
                this.current.entity = id.isEmpty() ? null : new Identifier(id);
            }
            catch (Exception e)
            {
                this.current.entity = null;
            }

            BBSModClient.getSelectors().update();
        });
        this.name = new UITextbox(100, (t) ->
        {
            this.current.name = t;

            BBSModClient.getSelectors().update();
        });
        this.nbt = new UITextarea<>((t) ->
        {
            try
            {
                if (t.trim().isEmpty())
                {
                    this.current.nbt = null;
                }
                else
                {
                    this.current.nbt = (new StringNbtReader(new StringReader(t))).parseCompound();
                }

                BBSModClient.getSelectors().update();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });

        this.nbt.background().wrap().h(80);

        this.selectorsEditor = new UIEntryListEditor<>(this.selectors, this::addSelector, null, this::removeSelector)
            .labels(UIKeys.SELECTORS_CONTEXT_ADD, UIKeys.GENERAL_DUPE, UIKeys.SELECTORS_CONTEXT_REMOVE)
            .clear(this::clearSelectors);

        this.column = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING,
            this.enabled,
            this.form,
            UI.labelRow(UIKeys.SELECTORS_ENTITY_ID, this.entity).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.SELECTORS_NAME_TAG, this.name).marginTop(UIConstants.SECTION_GAP),
            UI.label(UIKeys.SELECTORS_NBT).marginTop(UIConstants.SECTION_GAP),
            this.nbt
        );

        /* Selectors on the left, the properties of the selected one on the right — same split as the settings panel */
        this.selectorsEditor.strip.relative(this.content).w(SIDE_WIDTH);
        this.selectors.relative(this.content).y(UIConstants.CONTROL_HEIGHT).w(SIDE_WIDTH).h(1F, -UIConstants.CONTROL_HEIGHT);
        this.column.relative(this.content).x(SIDE_WIDTH).w(1F, -SIDE_WIDTH).h(1F);

        this.add(this.column, this.selectorsEditor.strip, this.selectors);
        this.onClose((e) -> BBSModClient.getSelectors().save());

        this.setSelector(this.selectors.getList().isEmpty() ? null : this.selectors.getList().get(0), true);
    }

    private void addSelector()
    {
        EntitySelector element = new EntitySelector();

        this.selectors.add(element);
        this.setSelector(element, true);
        BBSModClient.getSelectors().update();
    }

    /** The neighbour takes the pick: the selector that took its place, else the one before. */
    private void removeSelector(EntitySelector selector)
    {
        List<EntitySelector> list = this.selectors.getList();
        int index = list.indexOf(selector);

        list.remove(selector);
        this.setSelector(list.isEmpty() ? null : list.get(Math.min(index, list.size() - 1)), true);
        BBSModClient.getSelectors().update();
    }

    private void clearSelectors()
    {
        this.selectors.getList().clear();
        this.setSelector(null, true);
        BBSModClient.getSelectors().update();
    }

    private void setSelector(EntitySelector selector, boolean select)
    {
        this.current = selector;

        this.column.setVisible(selector != null);

        if (selector != null)
        {
            this.enabled.setValue(selector.enabled);
            this.form.setForm(selector.form);
            this.entity.setText(selector.entity == null ? "" : selector.entity.toString());
            this.name.setText(selector.name);
            this.nbt.setText(selector.nbt == null ? "" : selector.nbt.toString());
        }

        if (select)
        {
            this.selectors.setCurrentScroll(selector);
        }
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        this.content.area.render(context.batcher, BBSSettings.baseSurface());

        int x = this.content.area.x;

        context.batcher.box(x, this.content.area.y, x + SIDE_WIDTH, this.content.area.ey(), BBSSettings.chromeSurface());
    }
}
