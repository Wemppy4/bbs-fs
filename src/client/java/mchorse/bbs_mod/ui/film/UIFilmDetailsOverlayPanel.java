package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIFilmDetailsOverlayPanel extends UIOverlayPanel
{
    private Film film;

    public UITextarea<TextLine> description;
    public UILabel timeLabel;

    private long lastTimeUpdate;

    public UIFilmDetailsOverlayPanel(Film film)
    {
        super(L10n.lang("bbs.ui.film.details.title"));

        this.film = film;

        /* Stats */
        int replaysCount = film.replays.getList().size();
        int clipsCount = film.camera.get().size();
        int duration = film.camera.calculateDuration();
        long timeSpentActiveTicks = film.timeSpentActive.get();

        String timeFormatted = this.formatTime(timeSpentActiveTicks);
        String durationFormatted = TimeUtils.formatTime(duration);
        String createdFormatted = Film.formatCreatedAtForDisplay(film.createdAt.get());

        UILabel nameLabel = styleDetailRow(UI.label(L10n.lang("bbs.ui.film.details.name").format(film.getId())));
        UILabel createdLabel = styleDetailRow(UI.label(L10n.lang("bbs.ui.film.details.created").format(createdFormatted != null ? createdFormatted : "—")));
        UILabel statsLabel = styleDetailRow(UI.label(L10n.lang("bbs.ui.film.details.stats").format(replaysCount, clipsCount)));
        UILabel durationLabel = styleDetailRow(UI.label(L10n.lang("bbs.ui.film.details.duration").format(durationFormatted)));
        this.timeLabel = styleDetailRow(UI.label(L10n.lang("bbs.ui.film.details.time_spent").format(timeFormatted)));

        UILabel descriptionHeading = UI.label(L10n.lang("bbs.ui.film.details.description")).color(Colors.LIGHTER_GRAY);

        /* Description */
        this.description = new UITextarea<>((t) -> this.film.description.set(t));
        this.description.setText(film.description.get());
        this.description.background().wrap(true).padding(8);
        /* The one field that grows with the panel — the stats below it are single lines */
        this.description.h(88).expand();

        /* Layout: grouped spacing — meta, then description block, then stats */
        UIElement column = UI.column(
            4,
            0,
            nameLabel,
            createdLabel.marginTop(2),
            descriptionHeading.marginTop(8),
            this.description.marginTop(3),
            statsLabel.marginTop(10),
            durationLabel.marginTop(3),
            this.timeLabel.marginTop(3)
        );

        /* hTo, not h(1F, -12): a column's own resizer reports the height of its content and that
         * beats a flex height (see Flex#getH), so the column would sit exactly on its fields and
         * the description would have no leftover to expand into. A height anchored to the panel's
         * bottom edge is read before the post resizer, so it wins. */
        column.relative(this.content).xy(6, 6).w(1F, -12).hTo(this.content.area, 1F, -6);

        this.content.add(column);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (System.currentTimeMillis() - this.lastTimeUpdate >= 1000)
        {
            this.updateTimeDisplay();
            this.lastTimeUpdate = System.currentTimeMillis();
        }
    }

    private void updateTimeDisplay()
    {
        long timeSpentActiveTicks = this.film.timeSpentActive.get();
        String timeFormatted = this.formatTime(timeSpentActiveTicks);

        this.timeLabel.label = L10n.lang("bbs.ui.film.details.time_spent").format(timeFormatted);
    }

    private String formatTime(long ticks)
    {
        long seconds = ticks / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private static UILabel styleDetailRow(UILabel label)
    {
        return label.background(BBSSettings.primaryColor(Colors.A12));
    }
}
