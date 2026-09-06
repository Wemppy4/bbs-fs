package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The one vocabulary every row of every list speaks — timeline tracks, folder trees, replay
 * categories, texture layers, morph sections.
 *
 * <p>Every mark here is the same shape: a gradient across the row with a solid bar down its left
 * edge. That is the mark {@link Batcher2D#highlight} already puts on the chosen tab, mode and
 * tool, so a picked row and a pressed tool now say "this one" the same way. Rows carry text over
 * the mark and there are many of them at once, so the wash is softer than a tab's — the same mark,
 * said quieter.</p>
 *
 * <p>What tells the states apart is <b>where the colour comes from</b>, not the shape:</p>
 *
 * <ul>
 *     <li><b>Whose row this is</b> — the row's own colour, if it has one: a track's colour, a
 *     category's colour. The bar alone, never a wash. Belonging is a standing fact about a row
 *     rather than something happening to it, and a list whose every row is washed in its own
 *     colour is a list where the cursor has nothing left to say.</li>
 *     <li><b>The cursor is here</b> — the row's own colour where there is one, the accent
 *     otherwise. That is what keeps the timeline's coloured hovers, which are the reason this
 *     vocabulary exists at all.</li>
 *     <li><b>This is the pick</b> — always the accent, and always the strongest thing on the row.
 *     A row that has a colour of its own keeps its bar, so being picked never costs it its
 *     category.</li>
 * </ul>
 *
 * <p>Hover and pick are told apart by the bar, not by opacity alone: only a picked row has one
 * (or, having one already, gets the accent laid over it). Two washes of the same colour at
 * different strengths would be a guess; a bar is either there or it is not.</p>
 *
 * <p>The bar goes down last, over every wash, so it stays the one hard edge on the row however
 * many marks the row is wearing. A row with no bar — a section header, which is a whole strip
 * rather than one of a list — simply gets the wash, edge to edge.</p>
 */
public class RowStyle
{
    /** Width of the bar down a row's left edge. */
    public static final int STRIPE = 2;

    /** A row that names other rows rather than being one — a body part heading. Lit permanently. */
    private static final float HEADER_NEAR = 0.2F;
    private static final float HEADER_FAR = 0.04F;

    /** The cursor is on the row: a hint, not a pick. */
    private static final float HOVER_NEAR = 0.22F;
    private static final float HOVER_FAR = 0.03F;

    /** The pick, and the pick with the cursor still on it. */
    private static final float PICK_NEAR = 0.5F;
    private static final float PICK_HOVER_NEAR = 0.62F;
    private static final float PICK_FAR = 0.08F;

    /** Where a drop would land: louder than a hover, since it answers a question the user asked. */
    private static final float DROP_NEAR = 0.35F;
    private static final float DROP_FAR = 0.05F;

    /** How far a colour tag reaches across a row before it has faded out, and how strongly it starts. */
    private static final int SWATCH = 24;
    private static final float SWATCH_NEAR = 0.25F;

    /** How loud a row's text is while nothing is happening to it. */
    private static final float REST_TEXT = 0.75F;

    /**
     * What a row's text is drawn with. A row nothing is happening to speaks a little quieter, so
     * the one under the cursor and the one that is picked stand out — the way the timeline's track
     * names have always read.
     *
     * <p>Plain white when lit, not a tint. A row used to lift by turning faintly blue, which was
     * all it had to say with; now that resting is quieter, the tint only put the text out of step
     * with the icon beside it, which lifts to white.</p>
     *
     * @param lit whether the cursor is on the row or the row is the pick
     */
    public static int textColor(boolean lit)
    {
        return lit ? Colors.WHITE : Colors.setA(Colors.WHITE, REST_TEXT);
    }

    /**
     * The same quieting for a row whose text has a colour of its own to say something — broken,
     * disabled, missing. It keeps saying it, just as quietly as everything else at rest.
     */
    public static int textColor(boolean lit, int color)
    {
        return lit ? Colors.A100 | color : Colors.mulA(Colors.A100 | color, REST_TEXT);
    }

    /**
     * What a row's icon is drawn with. The same as its text — the whole row rises and falls as one
     * thing, rather than a bright icon dragging a faint name around. Kept as its own name because
     * an icon and a word are asked for in different places, and one of them may yet want to differ.
     */
    public static int iconColor(boolean lit)
    {
        return textColor(lit);
    }

    /**
     * Lay a row's marks down in order, so no caller has to remember it: what the row belongs to
     * first, then what it is, then what the cursor and the pick are doing to it — and the bar over
     * all of them.
     *
     * @param color the row's own colour, or 0 when it has none
     */
    public static void row(Batcher2D batcher, int x, int y, int w, int h, int color, boolean header, boolean hover, boolean picked)
    {
        if (header)
        {
            wash(batcher, x, y, w, h, tint(color), HEADER_NEAR, HEADER_FAR);
        }

        if (picked)
        {
            wash(batcher, x, y, w, h, accent(), hover ? PICK_HOVER_NEAR : PICK_NEAR, PICK_FAR);
        }
        else if (hover)
        {
            wash(batcher, x, y, w, h, tint(color), HOVER_NEAR, HOVER_FAR);
        }

        /* A row's own colour outranks the accent on the bar: being picked must not cost a row its
         * category, and the wash has already said which of the two the pick is. */
        int bar = color != 0 ? color : (picked ? accent() : 0);

        if (bar != 0)
        {
            bar(batcher, x, y, h, bar);
        }
    }

    /**
     * The cursor is on something that answers to it — a row, a section header, a menu entry. No
     * bar: a hint is not a pick, and the bar is what says "pick".
     */
    public static void hover(Batcher2D batcher, int x, int y, int w, int h, int color)
    {
        wash(batcher, x, y, w, h, tint(color), HOVER_NEAR, HOVER_FAR);
    }

    /**
     * A short colour tag at the start of a row: the same bar and fade, stopped before the row's
     * text instead of running under it. This is what a colour looks like where the row is
     * <em>about</em> that colour — a track in a filter, a replay in a menu — rather than being a
     * row that happens to have one.
     */
    public static void swatch(Batcher2D batcher, int x, int y, int h, int color)
    {
        wash(batcher, x, y, SWATCH, h, color, SWATCH_NEAR, 0F);
        bar(batcher, x, y, h, color);
    }

    /** Where a drop would land inside this row. */
    public static void dropTarget(Batcher2D batcher, int x, int y, int w, int h)
    {
        int accent = accent();

        wash(batcher, x, y, w, h, accent, DROP_NEAR, DROP_FAR);
        bar(batcher, x, y, h, accent);
    }

    /** The solid edge, drawn over the washes so it stays the one hard line on the row. */
    private static void bar(Batcher2D batcher, int x, int y, int h, int color)
    {
        batcher.box(x, y, x + STRIPE, y + h, Colors.A100 | color);
    }

    /** The gradient itself, edge to edge — the bar goes over its start. */
    private static void wash(Batcher2D batcher, int x, int y, int w, int h, int color, float near, float far)
    {
        batcher.gradientHBox(x, y, x + w, y + h, Colors.setA(color, near), Colors.setA(color, far));
    }

    /** A row's own colour where it has one, the accent where it has not. */
    private static int tint(int color)
    {
        return color == 0 ? accent() : color;
    }

    private static int accent()
    {
        return BBSSettings.primaryColor.get() & Colors.RGB;
    }
}
