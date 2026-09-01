package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.renderers.EmptyStateRenderer;

import java.util.function.Supplier;

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

    /**
     * What the panel's spot says while nothing is picked, asked every frame because the answer
     * changes with the timeline: an empty timeline is told how to get something into it, a full
     * one how to pick. Returning null keeps the spot silent; null itself never shows anything.
     */
    private Supplier<IKey> emptyLabel;

    /** Paints {@link #emptyLabel} inside the target. Lives there, so it survives a lost pick. */
    private UIRenderable emptyState;

    /** Who holds {@link #emptyState} right now, so it can be taken off when the target moves. */
    private UIElement emptyStateHost;

    /** The properties panel for the current pick, or null when nothing is picked. */
    protected abstract UIElement getPropertiesPanel();

    /**
     * Where the properties panel goes. Set it through here rather than by hand: the empty state
     * lives in the target too, and this is what moves it along.
     */
    public void setTarget(UIElement target)
    {
        this.target = target;

        this.updateEmptyState();
    }

    /**
     * Say what to do to fill the panel's spot while nothing is picked. Only the docked shape
     * needs it: without a target the panel is a strip off this element's own edge, and with
     * nothing picked the timeline takes that width back, leaving no hole to explain.
     */
    public void setEmptyState(Supplier<IKey> label)
    {
        this.emptyLabel = label;

        this.updateEmptyState();
    }

    private void updateEmptyState()
    {
        if (this.emptyStateHost != null && this.emptyStateHost != this.target)
        {
            this.emptyStateHost.remove(this.emptyState);
            this.emptyStateHost = null;
        }

        if (this.emptyLabel == null || this.target == null || this.emptyStateHost == this.target)
        {
            return;
        }

        if (this.emptyState == null)
        {
            this.emptyState = new UIRenderable(this::renderEmptyState);
        }

        this.target.add(this.emptyState);
        this.emptyStateHost = this.target;
    }

    /**
     * Nothing while a panel is up — it draws over this spot anyway — and nothing while the
     * properties are hidden, so what the user put away does not come back as a line of text.
     */
    private void renderEmptyState(UIContext context)
    {
        if (!this.propertiesVisible || this.getPropertiesPanel() != null || this.emptyStateHost == null)
        {
            return;
        }

        IKey label = this.emptyLabel.get();

        if (label != null)
        {
            EmptyStateRenderer.renderHint(context, this.emptyStateHost.area, label);
        }
    }

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

        if (this.emptyStateHost != null)
        {
            this.emptyStateHost.remove(this.emptyState);
            this.emptyStateHost = null;
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
