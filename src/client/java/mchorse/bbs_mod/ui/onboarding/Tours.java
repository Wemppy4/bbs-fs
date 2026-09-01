package mchorse.bbs_mod.ui.onboarding;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.onboarding.TourChapter.Step;

import java.util.List;

/**
 * The chapters there are. Anchors are named where the panels register them: the dashboard
 * in {@code UIDashboard}, the film editor in {@code UIFilmPanel}.
 */
public class Tours
{
    /** Two things can be touched while the landing screen is up: the bar and the landing itself. */
    public static final TourChapter DASHBOARD = new TourChapter("dashboard", 1, List.of(
        new Step("dashboard.taskbar", UIKeys.ONBOARDING_TOUR_DASHBOARD_TASKBAR_TITLE, UIKeys.ONBOARDING_TOUR_DASHBOARD_TASKBAR_TEXT),
        new Step("dashboard.landing", UIKeys.ONBOARDING_TOUR_DASHBOARD_LANDING_TITLE, UIKeys.ONBOARDING_TOUR_DASHBOARD_LANDING_TEXT)
    ));

    /**
     * The one thing this chapter has to land is that there are two editors: without it the
     * timeline and the properties look like they change on their own.
     */
    public static final TourChapter FILM = new TourChapter("film", 0, List.of(
        new Step("film.preview", UIKeys.ONBOARDING_TOUR_FILM_PREVIEW_TITLE, UIKeys.ONBOARDING_TOUR_FILM_PREVIEW_TEXT),
        new Step("film.editors", UIKeys.ONBOARDING_TOUR_FILM_EDITORS_TITLE, UIKeys.ONBOARDING_TOUR_FILM_EDITORS_TEXT),
        new Step("film.timeline", UIKeys.ONBOARDING_TOUR_FILM_TIMELINE_TITLE, UIKeys.ONBOARDING_TOUR_FILM_TIMELINE_TEXT),
        new Step("film.properties", UIKeys.ONBOARDING_TOUR_FILM_PROPERTIES_TITLE, UIKeys.ONBOARDING_TOUR_FILM_PROPERTIES_TEXT),
        new Step("film.export", UIKeys.ONBOARDING_TOUR_FILM_EXPORT_TITLE, UIKeys.ONBOARDING_TOUR_FILM_EXPORT_TEXT)
    ));
}
