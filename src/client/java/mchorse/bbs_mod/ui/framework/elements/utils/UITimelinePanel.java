package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * A timeline with a panel of properties for whatever is picked in it. The camera clips editor
 * and the keyframe editor are the same shape — pick something, throw the old panel away, build
 * a new one over the same spot — and everything here is the part that does not depend on WHAT
 * was picked: where the panel goes, who takes it down, and what the two visibility switches do.
 *
 * <p>The panel is deliberately NOT a field here. Both editors expose theirs under their own
 * name and type (a clip panel, a keyframe factory), callers reach for it, and hiding that
 * behind one erased field would cost more casts than it saves.
 */
public abstract class UITimelinePanel extends UIElement
{
    /**
     * The element the properties panel is laid out over — a dock tab, usually. Without one the
     * panel takes a strip off this element's own right edge.
     */
    protected UIElement target;

    protected boolean timelineVisible = true;
    protected boolean propertiesVisible = true;

    /** The properties panel for the current pick, or null when nothing is picked. */
    protected abstract UIElement getPropertiesPanel();

    /** The timeline this editor is built around. */
    protected abstract UIElement getTimeline();

    /**
     * The properties panel is parented to {@link #target}, not to this editor, so nothing would
     * take it down when this editor is dropped — it would stay in the edit area, clickable, and
     * the next editor would stack its own panel on top of it.
     */
    @Override
    public void removeFromParent()
    {
        super.removeFromParent();

        UIElement panel = this.getPropertiesPanel();

        if (panel != null)
        {
            panel.removeFromParent();
        }
    }

    public void setTimelineVisible(boolean visible)
    {
        this.timelineVisible = visible;

        this.getTimeline().setVisible(visible);
    }

    public void setPropertiesVisible(boolean visible)
    {
        this.propertiesVisible = visible;

        UIElement panel = this.getPropertiesPanel();

        if (panel != null)
        {
            panel.setVisible(visible);
        }
    }

    /**
     * Lays a freshly built properties panel out and adds it: over the target when there is one,
     * otherwise as a strip of the given width along this element's right edge.
     *
     * <p>The panel lives in whichever element it is laid out over, so it stays visible when the
     * timeline is hidden behind another dock tab.
     */
    protected void attachPropertiesPanel(UIElement panel, int width)
    {
        if (this.target == null)
        {
            panel.relative(this).x(1F, -width).w(width).h(1F);
        }
        else
        {
            panel.relative(this.target).x(0).y(0).w(1F).h(1F);
        }

        (this.target == null ? this : this.target).add(panel);
    }
}
