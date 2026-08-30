package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Where the film preview's overlay elements sit.
 *
 * <p>Each of the frame's six zones is a stack: an element asks its zone for a block of a
 * given size and is told where it landed, the zone remembering how much of itself is
 * already spoken for. Elements therefore cannot overlap, and cannot depend on whether
 * another element's condition happened to be true.
 *
 * <p>That was the whole trouble before: the right-hand column was a running
 * {@code y +=} down {@link mchorse.bbs_mod.ui.film.controller.FilmControllerHud}, so the
 * selected replay's name slid up and down depending on whether looping and flight were on,
 * and the stick guide in the bottom left was drawn straight over the axes crosshair. Every
 * offset was also a literal spelled out at the call site, which is why nothing lined up
 * with anything.
 *
 * <p>Drawing is deferred to {@link #flush(UIContext)}: a zone's wash has to go down before
 * its text, but its size is only known once the last element has been placed. Collecting
 * first and drawing at the end is what lets the wash be exactly the block it stands behind,
 * in the same frame.
 *
 * <p>Within a zone the stack order is the call order, growing from the frame's edge inwards.
 */
public class PreviewHud
{
    /** Gap between the frame's edge and the first element of a zone. */
    public static final int MARGIN = 3;

    /** Gap between two elements stacked in the same zone. */
    public static final int GAP = 3;

    /** The padding {@code Batcher2D#textCard} draws around its text on every side. */
    public static final int CARD_PADDING = 3;

    /** How far past a washed block the wash keeps fading out. */
    private static final int BACKDROP_FADE = 12;

    public enum Anchor
    {
        TOP_LEFT(0F, true),
        TOP_CENTER(0.5F, true),
        TOP_RIGHT(1F, true),
        BOTTOM_LEFT(0F, false),
        BOTTOM_CENTER(0.5F, false),
        BOTTOM_RIGHT(1F, false);

        public final float horizontal;
        public final boolean top;

        private Anchor(float horizontal, boolean top)
        {
            this.horizontal = horizontal;
            this.top = top;
        }
    }

    /** One thing to draw, and the block it was given. */
    private static class Entry
    {
        public final Area block;
        public final Consumer<UIContext> draw;

        public Entry(Area block, Consumer<UIContext> draw)
        {
            this.block = block;
            this.draw = draw;
        }
    }

    private final Area frame = new Area();
    private final int[] used = new int[Anchor.values().length];
    private final int[] inset = new int[Anchor.values().length];
    private final List<List<Entry>> entries = new ArrayList<>();
    private final List<Area> backdrops = new ArrayList<>();

    public PreviewHud()
    {
        for (int i = 0; i < Anchor.values().length; i++)
        {
            this.entries.add(new ArrayList<>());
        }
    }

    /** Starts a new frame: every zone is empty again. */
    public void begin(Area area)
    {
        this.frame.copy(area);
        this.backdrops.clear();

        for (int i = 0; i < this.used.length; i++)
        {
            this.used[i] = 0;
            this.inset[i] = 0;
            this.entries.get(i).clear();
        }
    }

    /** The frame the zones are measured against — the video's area, not the panel's. */
    public Area getFrame()
    {
        return this.frame;
    }

    /**
     * Reserves a block of the given size in a zone and answers where it goes. The block is
     * placed past whatever the zone already holds, growing away from the frame's edge.
     */
    public Area push(Anchor anchor, int w, int h)
    {
        int offset = this.used[anchor.ordinal()];
        int inset = this.inset[anchor.ordinal()];
        int left = this.frame.x + MARGIN + (anchor.horizontal < 0.5F ? inset : 0);
        int right = this.frame.ex() - MARGIN - (anchor.horizontal > 0.5F ? inset : 0);
        Area block = new Area();

        block.setSize(w, h);
        block.setPos(
            Math.round(left + (right - left - w) * anchor.horizontal),
            anchor.top
                ? this.frame.y + MARGIN + offset
                : this.frame.ey() - MARGIN - offset - h
        );

        this.used[anchor.ordinal()] = offset + h + GAP;

        return block;
    }

    /**
     * Holds a strip of the zone's anchored side clear, so the stack builds beside it rather
     * than under it — for something that stands alongside the whole stack instead of taking
     * a line of its own, the way the selected replay's thumbnail does.
     */
    public void inset(Anchor anchor, int amount)
    {
        this.inset[anchor.ordinal()] = amount;
    }

    /**
     * Takes room in a zone without drawing anything, for the one thing that lays itself out
     * on its own: the icon bar is a real UI element positioned against the panel, not the
     * frame, so the zone is told how much of itself the bar covers instead of handing it a
     * block it could not use.
     */
    public void reserve(Anchor anchor, int amount)
    {
        if (amount > 0)
        {
            this.used[anchor.ordinal()] += amount;
        }
    }

    /** Stacks an icon at its own size. */
    public Area icon(UIContext context, Anchor anchor, Icon icon, int color)
    {
        Area block = this.push(anchor, icon.w, icon.h);

        return this.add(anchor, block, (c) -> c.batcher.icon(icon, color, block.x, block.y));
    }

    /** Stacks a bare line of text, read against the video by its shadow alone. */
    public Area text(UIContext context, Anchor anchor, String text, int color)
    {
        Area block = this.push(anchor, context.batcher.getFont().getWidth(text), context.batcher.getFont().getHeight());

        return this.add(anchor, block, (c) -> c.batcher.text(text, block.x, block.y, color, true));
    }

    /** Stacks a text card, sized so that the card's background is what fills the block. */
    public Area label(UIContext context, Anchor anchor, String text, int color, int background)
    {
        Area block = this.push(anchor, cardWidth(context, text), cardHeight(context));

        return this.add(anchor, block, (c) -> drawLabel(c, block, text, color, background));
    }

    /**
     * Draws something in a zone at a block of the caller's own choosing, without moving the
     * zone's stack — for the piece that stands beside a stacked element rather than under it
     * (the selected replay's thumbnail).
     */
    public Area include(Anchor anchor, Area block, Consumer<UIContext> draw)
    {
        return this.add(anchor, block, draw);
    }

    /**
     * Asks for a wash under a block — a picture needs the video behind it darkened to read as
     * a picture, where text gets by on its shadow. Asked for rather than derived, so a corner
     * is only ever tinted where something actually needed it.
     */
    public void backdrop(Area block)
    {
        Area copy = new Area();

        copy.copy(block);
        this.backdrops.add(copy);
    }

    private Area add(Anchor anchor, Area block, Consumer<UIContext> draw)
    {
        this.entries.get(anchor.ordinal()).add(new Entry(block, draw));

        return block;
    }

    /** Lays down the washes that were asked for, then everything put in the zones this frame. */
    public void flush(UIContext context)
    {
        this.renderBackdrops(context);

        for (Anchor anchor : Anchor.values())
        {
            for (Entry entry : this.entries.get(anchor.ordinal()))
            {
                entry.draw.accept(context);
            }
        }
    }

    /**
     * The washes asked for through {@link #backdrop}: each fades in from the frame's edge the
     * block sits nearest, dies out a short way past its far side, and is only as tall as the
     * block itself.
     */
    private void renderBackdrops(UIContext context)
    {
        if (this.backdrops.isEmpty())
        {
            return;
        }

        int shade = BBSSettings.lightSurfaces() ? (Colors.A50 | 0xFFFFFF) : Colors.A50;

        for (Area block : this.backdrops)
        {
            int y1 = Math.max(this.frame.y, block.y - MARGIN);
            int y2 = Math.min(this.frame.ey(), block.ey() + MARGIN);

            if (block.mx() > this.frame.mx())
            {
                context.batcher.gradientHBox(block.x - BACKDROP_FADE, y1, this.frame.ex(), y2, 0, shade);
            }
            else
            {
                context.batcher.gradientHBox(this.frame.x, y1, block.ex() + BACKDROP_FADE, y2, shade, 0);
            }
        }
    }

    public static int cardWidth(UIContext context, String text)
    {
        return context.batcher.getFont().getWidth(text) + CARD_PADDING * 2;
    }

    public static int cardHeight(UIContext context)
    {
        return context.batcher.getFont().getHeight() + CARD_PADDING * 2;
    }

    /** Draws a text card into an already reserved block, right where the block starts. */
    public static void drawLabel(UIContext context, Area block, String text, int color, int background)
    {
        drawLabel(context, block.x, block.y, text, color, background);
    }

    /** Draws a text card whose background's top left corner is the given point. */
    public static void drawLabel(UIContext context, int x, int y, String text, int color, int background)
    {
        context.batcher.textCard(text, x + CARD_PADDING, y + CARD_PADDING, color, background);
    }
}
