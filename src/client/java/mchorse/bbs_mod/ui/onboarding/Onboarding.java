package mchorse.bbs_mod.ui.onboarding;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureManagerPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.model_editor.UIModelEditorPanel;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.ui.onboarding.welcome.UIWelcomeOverlayPanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.utility.audio.UIAudioEditorPanel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What happens the first time — the welcome screen once, then a tour chapter the first time
 * each place is reached. The panels report the moments ("the dashboard came up", "this panel
 * is on screen", "a document was opened in it"); this decides whether anything is owed.
 *
 * <p>A place is either a panel as such (morphing, model blocks, textures — there is something
 * to see the moment it is up) or a panel with a document in it (the film, a model, a particle
 * scheme, a sound — empty, it is a landing screen, and the landing screen has its own step).
 * Which is which is the two maps below.</p>
 *
 * <p>One thing at a time: the welcome screen outranks any tour, and between tours the one
 * with the higher priority wins, since a film opens under the dashboard on the way in and
 * the dashboard has to be explained first. A tour pushed aside isn't ticked off — it comes
 * back the next time its place is reached.</p>
 */
public class Onboarding
{
    /** Chapters owed the moment a panel is on screen. */
    private static final Map<Class<?>, TourChapter> SHOWN = new HashMap<>();

    /** Chapters owed once a panel has a document open. */
    private static final Map<Class<?>, TourChapter> OPENED = new HashMap<>();

    private static UITour active;

    static
    {
        SHOWN.put(UIMorphingPanel.class, Tours.MORPHING);
        SHOWN.put(UIModelBlockPanel.class, Tours.MODEL_BLOCKS);
        SHOWN.put(UITextureManagerPanel.class, Tours.TEXTURES);

        OPENED.put(UIFilmPanel.class, Tours.FILM);
        OPENED.put(UIModelEditorPanel.class, Tours.MODEL_EDITOR);
        OPENED.put(UIParticleSchemePanel.class, Tours.PARTICLES);
        OPENED.put(UIAudioEditorPanel.class, Tours.AUDIO);
    }

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

    /**
     * A panel became the one on screen. A chapter about the panel that just went away is
     * taken down, not ticked off: its anchors are gone, and walking them blind would count
     * as walked. The dashboard's own chapter stays — its anchors are on every panel.
     */
    public static void panelShown(UIDashboardPanel panel)
    {
        TourChapter chapter = panel == null ? null : SHOWN.get(panel.getClass());
        UIContext context = panel == null ? null : panel.getContext();

        if (active != null && !active.isFinished() && active.chapter.priority() == 0 && active.chapter != chapter)
        {
            abandon();
        }

        if (chapter != null && context != null)
        {
            start(context, chapter);
        }
    }

    /** A panel has a document open in it. */
    public static void dataOpened(UIDashboardPanel panel)
    {
        TourChapter chapter = OPENED.get(panel.getClass());
        UIContext context = panel.getContext();

        if (chapter != null && context != null)
        {
            start(context, chapter);
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

        /* It paints the whole screen itself; dimming and blurring under it would be wasted work */
        UIOverlay.addOverlay(context, panel, 1F, 1F).noBackground();
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

    /**
     * A chapter ended; the place the user is standing in may be owed one too — the film is
     * usually open all along, under the dashboard's chapter.
     */
    private static void finished(UIContext context, TourChapter chapter)
    {
        markDone(chapter);
        active = null;

        if (!(context.menu instanceof UIDashboard dashboard))
        {
            return;
        }

        UIDashboardPanel panel = dashboard.getPanels().panel;

        panelShown(panel);

        if (panel instanceof UIDataDashboardPanel<?> data && data.getData() != null)
        {
            dataOpened(panel);
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
