package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A dashboard panel and its lifecycle.
 *
 * <p>There are four edges, in two pairs:</p>
 *
 * <ul>
 *     <li>{@link #open()} / {@link #close()} &mdash; the dashboard screen came up / is being torn
 *     down. These run for <b>every</b> panel the dashboard owns, including the ones nobody is
 *     looking at, so nothing here may touch the world.</li>
 *     <li>{@link #appear()} / {@link #disappear()} &mdash; this panel is the one on screen. World
 *     effects (camera controllers, player hiding, playback) belong here and nowhere else.</li>
 * </ul>
 *
 * <p><b>Being on screen nests inside being open.</b> A panel that is showing when the screen closes
 * is sent {@code disappear()} first and {@code close()} after, so an editor never has to undo its
 * own world effects twice &mdash; which is what the film panel used to do, with six statements
 * copied between the two methods.</p>
 *
 * <p>The four methods are <b>final</b>, and each class registers its own share of an edge from its
 * constructor ({@link #onOpen}, {@link #onClose}, {@link #onAppear}, {@link #onDisappear}). A
 * subclass therefore cannot silently drop a parent's step by forgetting to call {@code super} —
 * which is exactly how particle schemes stopped saving: {@code UIParticleSchemePanel.close()}
 * overrode the method and never reached {@link UIDataDashboardPanel}'s save.</p>
 *
 * <p>Entering edges run base-first (the parent sets things up before the child), leaving edges run
 * child-first (the child writes into the data before the parent saves it).</p>
 */
public class UIDashboardPanel extends UIElement
{
    public final UIDashboard dashboard;

    private final List<Runnable> opening = new ArrayList<>();
    private final List<Runnable> closing = new ArrayList<>();
    private final List<Runnable> appearing = new ArrayList<>();
    private final List<Runnable> disappearing = new ArrayList<>();

    public UIDashboardPanel(UIDashboard dashboard)
    {
        super();

        this.dashboard = dashboard;
        this.markContainer();
    }

    public boolean needsBackground()
    {
        return true;
    }

    public boolean canToggleVisibility()
    {
        return true;
    }

    public boolean canPause()
    {
        return true;
    }

    public boolean canRefresh()
    {
        return true;
    }

    /** Register what this class does when the dashboard screen comes up. Constructor-time only. */
    protected void onOpen(Runnable step)
    {
        this.opening.add(step);
    }

    /** Register what this class does when the dashboard screen is torn down. Constructor-time only. */
    protected void onClose(Runnable step)
    {
        this.closing.add(step);
    }

    /** Register what this class does when this panel becomes the one on screen. Constructor-time only. */
    protected void onAppear(Runnable step)
    {
        this.appearing.add(step);
    }

    /** Register what this class does when this panel stops being the one on screen. Constructor-time only. */
    protected void onDisappear(Runnable step)
    {
        this.disappearing.add(step);
    }

    public final void open()
    {
        this.run(this.opening, false);
    }

    public final void close()
    {
        this.run(this.closing, true);
    }

    public final void appear()
    {
        this.run(this.appearing, false);
    }

    public final void disappear()
    {
        this.run(this.disappearing, true);
    }

    private void run(List<Runnable> steps, boolean childFirst)
    {
        if (childFirst)
        {
            for (int i = steps.size() - 1; i >= 0; i--)
            {
                steps.get(i).run();
            }
        }
        else
        {
            for (Runnable step : steps)
            {
                step.run();
            }
        }
    }

    public void update()
    {}

    public void startRenderFrame(float tickDelta)
    {}

    public void renderInWorld(WorldRenderContext context)
    {}

    public void renderPanelBackground(UIContext context)
    {}
}
