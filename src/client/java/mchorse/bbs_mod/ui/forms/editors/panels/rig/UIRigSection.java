package mchorse.bbs_mod.ui.forms.editors.panels.rig;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.panels.UIRigFormPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UITabStrip;
import mchorse.bbs_mod.ui.framework.elements.utils.UITextTab;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One step's part of the rig tab: a section whose header carries the step's icon and name, what its
 * setting spans ("start → end") and, for a step a bone may have or not, the switch that sets it up
 * on the bone or removes it. Switched off, the section is its gray header alone; switched on, it
 * folds like any section. The step keeps its data where it always has — the section only shows the
 * part of it the selected bone is in. Fields that fall into groups go on pages under word tabs at
 * the section's top ({@link #page}), never into a section inside the section.
 */
public abstract class UIRigSection extends UISection
{
    protected final UIRigFormPanel panel;

    /** The step this section shows; null for an addon's section, which shows settings of its own. */
    public final RigStep step;

    private final Icon icon;

    /** Sets the step up on the bone or removes it; null for a step every bone has (the limits). */
    public final UIToggle toggle;

    /** The page each section shows, for the session, by its step (or its class for an addon's): the sections are rebuilt with the panel. */
    private static final Map<Object, Integer> PAGES = new HashMap<>();

    /** The word tabs over the pages, once the section has any. */
    private UITabStrip tabs;
    private final List<UIElement> pages = new ArrayList<>();

    private boolean on;
    private String info = "";

    protected UIRigSection(UIRigFormPanel panel, RigStep step, boolean switchable)
    {
        this(panel, step, step.title(), step.icon, switchable);
    }

    /** An addon's section: a header with its own title and icon, see {@code RegisterRigSectionsEvent}. */
    protected UIRigSection(UIRigFormPanel panel, IKey title, Icon icon, boolean switchable)
    {
        this(panel, null, title, icon, switchable);
    }

    private UIRigSection(UIRigFormPanel panel, RigStep step, IKey title, Icon icon, boolean switchable)
    {
        super(title);

        this.panel = panel;
        this.step = step;
        this.icon = icon;
        this.toggle = switchable ? new UIToggle(IKey.EMPTY, (b) -> this.panel.switchStep(this, b.getValue())) : null;

        if (this.toggle != null)
        {
            /* As tall as the title, so the header is as tall as any section's. */
            this.toggle.w(UIConstants.SWITCH_WIDTH).h(HEADER_HEIGHT);
            this.title.removeFromParent();

            UIElement header = new UIElement();

            header.row(UIConstants.MARGIN).preferred(0).height(HEADER_HEIGHT);
            header.add(this.title, this.toggle);
            this.prepend(header);
        }
    }

    /**
     * Adds a page of fields under the word tabs at the top of the section, which shows one page at a
     * time; the tabs share the section's width.
     */
    protected void page(IKey label, UIElement... fields)
    {
        if (this.tabs == null)
        {
            this.tabs = new UITabStrip(ScrollDirection.HORIZONTAL)
            {
                @Override
                protected boolean pressTab(int index, UIContext context)
                {
                    this.select(index);

                    return true;
                }
            };
            this.tabs.fixed();
            /* The dark strip an icon switch (UIIcons) sits on. */
            this.tabs.background(BBSSettings::deepSurface);
            this.tabs.active(this::getPage);
            this.tabs.onSelect(this::setPage);
            this.tabs.h(UIConstants.CONTROL_HEIGHT);
            this.fields.add(this.tabs);
        }

        UIElement page = UI.column(fields);

        page.setVisible(this.pages.size() == this.getPage());
        this.pages.add(page);
        this.fields.add(page);
        this.tabs.addTab(new UITextTab(label)).h(UIConstants.CONTROL_HEIGHT);
    }

    private int getPage()
    {
        return PAGES.getOrDefault(this.pageKey(), 0);
    }

    private void setPage(int index)
    {
        PAGES.put(this.pageKey(), index);

        for (int i = 0; i < this.pages.size(); i++)
        {
            this.pages.get(i).setVisible(i == index);
        }

        this.resizeParent();
    }

    private Object pageKey()
    {
        return this.step == null ? this.getClass() : this.step;
    }

    /** Refills the section for the bone, the switch included. */
    public void refresh(String bone)
    {
        this.info = "";
        this.on = this.fill(bone);

        if (this.toggle != null)
        {
            this.toggle.setValue(this.on);
        }

        this.fields.setVisible(this.hasBody());
    }

    /** Whether the step has something on the selected bone. */
    public boolean isOn()
    {
        return this.on;
    }

    /** What the setting spans, after the name in the header. */
    protected void setInfo(String info)
    {
        this.info = info == null ? "" : info;
    }

    /** Fills the fields for the bone; whether the step has something on it. */
    protected abstract boolean fill(String bone);

    /** Sets the step up on the bone ({@code on}) or removes what the section shows. */
    public abstract void switched(String bone, boolean on);

    /** Grays the fields out with the panel. */
    public void setFieldsEnabled(boolean enabled)
    {
        if (this.toggle != null)
        {
            this.toggle.setEnabled(enabled);
        }
    }

    /** A switchable section has nothing under its header while it's off. */
    protected boolean hasBody()
    {
        return this.on || this.toggle == null;
    }

    /** Whether the header reads at full strength. */
    protected boolean isLit()
    {
        return this.on;
    }

    @Override
    protected void renderHeader(UIContext context, UILabel title)
    {
        FontRenderer font = context.batcher.getFont();
        Area area = title.area;
        int color = this.isLit() ? Colors.WHITE : Colors.GRAY;
        int x = area.x;
        int right = area.ex();

        /* The strip one pixel taller than the label, centred the way every section header is. */
        float middle = area.y + (area.h + 1) / 2F;
        int textY = area.y + (area.h + 1 - font.getHeight()) / 2;

        context.batcher.icon(this.icon, color, x, middle, 0F, 0.5F);
        x += ICON_WIDTH;

        if (this.hasBody())
        {
            renderArrow(context, right - ARROW_SIZE / 2F, middle, this.isExpanded(), color);
            right -= ARROW_SIZE + 2;
        }

        String label = font.limitToWidth(title.label.get(), right - x);

        context.batcher.textShadow(label, x, textY, color);
        x += font.getWidth(label) + UIConstants.MARGIN * 2;

        if (!this.info.isEmpty() && x < right)
        {
            context.batcher.textShadow(font.limitToWidth(this.info, right - x), x, textY, Colors.GRAY);
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        /* Nothing folds under a switched off header. */
        return this.hasBody() && super.subMouseClicked(context);
    }
}
