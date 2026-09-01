package mchorse.bbs_mod.ui.onboarding;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.onboarding.welcome.UIWelcomeOverlayPanel;

import java.util.HashSet;
import java.util.Set;

/**
 * What happens the first time — the welcome screen once, then a tour chapter the first time
 * each place is reached. The panels report the moments ("the dashboard came up", "a film was
 * opened"); this decides whether anything is owed for them.
 *
 * <p>One thing at a time: the welcome screen outranks any tour, and between tours the one
 * with the higher priority wins, since a film opens under the dashboard on the way in and
 * the dashboard has to be explained first. A tour pushed aside isn't ticked off — it comes
 * back the next time its place is reached.</p>
 */
public class Onboarding
{
    private static UITour active;

    /** The dashboard screen came up. */
    public static void dashboardOpened(UIDashboard dashboard)
    {
        if (!BBSSettings.onboardingWelcomeSeen.get())
        {
            showWelcome(dashboard.context);

            return;
        }

        start(dashboard.context, Tours.DASHBOARD);
    }

    /** A film is on screen in the film editor. */
    public static void filmOpened(UIFilmPanel panel)
    {
        UIContext context = panel.getContext();

        if (context != null)
        {
            start(context, Tours.FILM);
        }
    }

    /** The welcome screen, whether for the first time or from the settings. */
    public static void showWelcome(UIContext context)
    {
        UIWelcomeOverlayPanel panel = new UIWelcomeOverlayPanel();

        /* The context is taken now: by the time the close event fires the panel is already off
         * the tree, and a panel off the tree has no context to ask for */
        panel.onClose((e) -> welcomeClosed(context));

        abandon();
        UIOverlay.addOverlay(context, panel, 1F, 1F);
    }

    /** The welcome screen went down, however it went down: the tour is what comes next. */
    public static void welcomeClosed(UIContext context)
    {
        BBSSettings.onboardingWelcomeSeen.set(true);
        start(context, Tours.DASHBOARD);
    }

    /** Forget every chapter walked, and start over from where the user is. */
    public static void resetTours(UIContext context)
    {
        BBSSettings.onboardingToursDone.set(new HashSet<>());
        abandon();
        start(context, Tours.DASHBOARD);
    }

    private static boolean isDone(TourChapter chapter)
    {
        return BBSSettings.onboardingToursDone.get().contains(chapter.id());
    }

    private static void markDone(TourChapter chapter)
    {
        Set<String> done = new HashSet<>(BBSSettings.onboardingToursDone.get());

        done.add(chapter.id());
        BBSSettings.onboardingToursDone.set(done);
    }

    /**
     * Walk a chapter, unless it was walked already, something modal is up (the moment comes
     * again), or a chapter that matters more is being walked right now.
     */
    private static void start(UIContext context, TourChapter chapter)
    {
        if (isDone(chapter) || UIOverlay.has(context))
        {
            return;
        }

        if (active != null && !active.isFinished())
        {
            if (active.chapter == chapter || chapter.priority() <= active.chapter.priority())
            {
                return;
            }

            abandon();
        }

        UIElement overlay = context.menu.overlay;

        active = new UITour(chapter, () -> finished(context, chapter));
        active.full(overlay);

        /* First among the overlay's children: drawn under every overlay, and offered clicks last */
        overlay.prepend(active);
        overlay.resize();
    }

    private static void finished(UIContext context, TourChapter chapter)
    {
        markDone(chapter);
        active = null;

        /* The film may have been open all along, under the dashboard's chapter */
        if (context.menu instanceof UIDashboard dashboard && dashboard.getPanels().panel instanceof UIFilmPanel film && film.getData() != null)
        {
            start(context, Tours.FILM);
        }
    }

    private static void abandon()
    {
        if (active != null)
        {
            active.abandon();
            active = null;
        }
    }
}
