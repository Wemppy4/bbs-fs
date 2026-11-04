package mchorse.bbs_mod.ui.sequence;

import mchorse.bbs_mod.film.sequence.FilmClip;
import mchorse.bbs_mod.film.sequence.SequenceFilms;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.WorldScope;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The panel of a film on a sequence's timeline: which film, from which of its ticks, with its
 * own sound or without, and the way into the film itself.
 */
public class UIFilmClip extends UIClip<FilmClip>
{
    public UIButton pickFilm;
    public UIIcon fit;
    public UITrackpad offset;
    public UIToggle filmAudio;
    public UIButton openFilm;
    public UILabel world;

    public UIFilmClip(FilmClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.pickFilm = new UIButton(UIKeys.SEQUENCE_CLIP_PICK_FILM, (b) -> this.pickFilm());
        this.fit = new UIIcon(Icons.RIGHTLOAD, (b) -> this.fitToFilm());
        this.fit.tooltip(UIKeys.SEQUENCE_CLIP_FIT);

        this.offset = this.trackpad(this.clip.offset);
        this.offset.limit(0, Integer.MAX_VALUE, true);

        this.filmAudio = this.toggle(UIKeys.SEQUENCE_CLIP_FILM_AUDIO, this.clip.filmAudio);

        this.openFilm = new UIButton(UIKeys.SEQUENCE_CLIP_OPEN_FILM, (b) ->
        {
            UISequencePanel panel = this.getSequencePanel();

            if (panel != null)
            {
                panel.openFilm(this.clip);
            }
        });
        this.bind(this.openFilm, () -> this.openFilm.setEnabled(this.clip.isInCurrentWorld() && !this.clip.film.get().isEmpty()));

        this.world = UI.label(IKey.EMPTY, UIConstants.CONTROL_HEIGHT).labelAnchor(0, 0.5F);
        this.world.color(Colors.LIGHTER_GRAY);
        this.world.tooltip(UIKeys.SEQUENCE_CLIP_OTHER_WORLD);
        this.bind(this.world, () ->
        {
            boolean other = !this.clip.isInCurrentWorld();

            this.world.label = other ? UIKeys.SEQUENCE_CLIP_WORLD.format(this.clip.world.get()) : IKey.EMPTY;
        });
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:film"),
            UI.row(this.pickFilm, this.fit),
            UI.labelRow(UIKeys.SEQUENCE_CLIP_OFFSET, this.offset).marginTop(UIConstants.SECTION_GAP),
            this.filmAudio,
            this.openFilm,
            this.world
        ));
    }

    /**
     * The sequence editor this panel is in, or null when it is in something else.
     */
    private UISequencePanel getSequencePanel()
    {
        return this.editor instanceof UIClipsPanel panel && panel.owner instanceof UISequencePanel sequence ? sequence : null;
    }

    private void pickFilm()
    {
        ContentType.FILMS.getRepository().requestKeys((keys) ->
        {
            UIStringOverlayPanel panel = new UIStringOverlayPanel(UIKeys.SEQUENCE_PICK_FILM, false, keys.stream().filter((key) -> !key.endsWith("/")).toList(), (id) ->
            {
                if (id == null || id.isEmpty())
                {
                    return;
                }

                this.clip.film.set(id);
                this.clip.world.set(WorldScope.current());
                this.fitToFilm();
            });

            panel.set(this.clip.isInCurrentWorld() ? this.clip.film.get() : "");
            UIOverlay.addOverlay(this.getContext(), panel, 240, 300);
        });
    }

    /**
     * Stretch the clip to the end of its film, counted from the tick it starts the film on.
     */
    private void fitToFilm()
    {
        UISequencePanel panel = this.getSequencePanel();

        if (panel == null || !this.clip.isInCurrentWorld() || this.clip.film.get().isEmpty())
        {
            return;
        }

        panel.getFilms().request(this.clip.film.get(), (film) ->
        {
            if (film != null)
            {
                this.clip.duration.set(Math.max(1, SequenceFilms.getDuration(film) - this.clip.offset.get()));
                this.fillData();
            }
        });
    }
}
