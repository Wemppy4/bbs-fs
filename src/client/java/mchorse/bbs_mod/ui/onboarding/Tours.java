package mchorse.bbs_mod.ui.onboarding;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.onboarding.TourChapter.Step;

import java.util.List;

/**
 * The chapters there are. Anchors are named where the panels register them, in their
 * constructors; a chapter here is the list of them with the words, nothing more.
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

    /** Pick a form, and the pencil is the way into everything a form has inside. */
    public static final TourChapter MORPHING = new TourChapter("morphing", 0, List.of(
        new Step("morphing.forms", UIKeys.ONBOARDING_TOUR_MORPHING_FORMS_TITLE, UIKeys.ONBOARDING_TOUR_MORPHING_FORMS_TEXT),
        new Step("morphing.edit", UIKeys.ONBOARDING_TOUR_MORPHING_EDIT_TITLE, UIKeys.ONBOARDING_TOUR_MORPHING_EDIT_TEXT),
        new Step("morphing.demorph", UIKeys.ONBOARDING_TOUR_MORPHING_DEMORPH_TITLE, UIKeys.ONBOARDING_TOUR_MORPHING_DEMORPH_TEXT)
    ));

    public static final TourChapter MODEL_BLOCKS = new TourChapter("model_blocks", 0, List.of(
        new Step("model_blocks.list", UIKeys.ONBOARDING_TOUR_MODEL_BLOCKS_LIST_TITLE, UIKeys.ONBOARDING_TOUR_MODEL_BLOCKS_LIST_TEXT),
        new Step("model_blocks.form", UIKeys.ONBOARDING_TOUR_MODEL_BLOCKS_FORM_TITLE, UIKeys.ONBOARDING_TOUR_MODEL_BLOCKS_FORM_TEXT),
        new Step("model_blocks.transform", UIKeys.ONBOARDING_TOUR_MODEL_BLOCKS_TRANSFORM_TITLE, UIKeys.ONBOARDING_TOUR_MODEL_BLOCKS_TRANSFORM_TEXT)
    ));

    public static final TourChapter PARTICLES = new TourChapter("particles", 0, List.of(
        new Step("particles.preview", UIKeys.ONBOARDING_TOUR_PARTICLES_PREVIEW_TITLE, UIKeys.ONBOARDING_TOUR_PARTICLES_PREVIEW_TEXT),
        new Step("particles.sections", UIKeys.ONBOARDING_TOUR_PARTICLES_SECTIONS_TITLE, UIKeys.ONBOARDING_TOUR_PARTICLES_SECTIONS_TEXT),
        new Step("particles.molang", UIKeys.ONBOARDING_TOUR_PARTICLES_MOLANG_TITLE, UIKeys.ONBOARDING_TOUR_PARTICLES_MOLANG_TEXT)
    ));

    public static final TourChapter MODEL_EDITOR = new TourChapter("model_editor", 0, List.of(
        new Step("model_editor.preview", UIKeys.ONBOARDING_TOUR_MODEL_EDITOR_PREVIEW_TITLE, UIKeys.ONBOARDING_TOUR_MODEL_EDITOR_PREVIEW_TEXT),
        new Step("model_editor.settings", UIKeys.ONBOARDING_TOUR_MODEL_EDITOR_SETTINGS_TITLE, UIKeys.ONBOARDING_TOUR_MODEL_EDITOR_SETTINGS_TEXT),
        new Step("model_editor.bones", UIKeys.ONBOARDING_TOUR_MODEL_EDITOR_BONES_TITLE, UIKeys.ONBOARDING_TOUR_MODEL_EDITOR_BONES_TEXT)
    ));

    public static final TourChapter TEXTURES = new TourChapter("textures", 0, List.of(
        new Step("textures.browser", UIKeys.ONBOARDING_TOUR_TEXTURES_BROWSER_TITLE, UIKeys.ONBOARDING_TOUR_TEXTURES_BROWSER_TEXT),
        new Step("textures.edit", UIKeys.ONBOARDING_TOUR_TEXTURES_EDIT_TITLE, UIKeys.ONBOARDING_TOUR_TEXTURES_EDIT_TEXT),
        new Step("textures.tabs", UIKeys.ONBOARDING_TOUR_TEXTURES_TABS_TITLE, UIKeys.ONBOARDING_TOUR_TEXTURES_TABS_TEXT)
    ));

    public static final TourChapter AUDIO = new TourChapter("audio", 0, List.of(
        new Step("audio.waveform", UIKeys.ONBOARDING_TOUR_AUDIO_WAVEFORM_TITLE, UIKeys.ONBOARDING_TOUR_AUDIO_WAVEFORM_TEXT),
        new Step("audio.play", UIKeys.ONBOARDING_TOUR_AUDIO_PLAY_TITLE, UIKeys.ONBOARDING_TOUR_AUDIO_PLAY_TEXT),
        new Step("audio.save", UIKeys.ONBOARDING_TOUR_AUDIO_SAVE_TITLE, UIKeys.ONBOARDING_TOUR_AUDIO_SAVE_TEXT)
    ));
}
