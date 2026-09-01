package mchorse.bbs_mod.ui.onboarding;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.onboarding.TourChapter.Step;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * One chapter being walked: a frame around the place the current step points at, and a card
 * beside it with the words and a "Continue". Nothing else changes — the editor underneath
 * keeps taking clicks, so the card explains a thing while the thing stays usable.
 *
 * <p>Lives in the menu's overlay layer, under the overlays proper: a settings window or a
 * prompt that opens meanwhile goes over the card, the way it goes over everything else.</p>
 *
 * <p>A step whose anchor is nowhere to be seen when its turn comes is skipped without a word.
 * One whose anchor disappears while it is up (a tab switched away) waits a moment for it to
 * come back, then moves on: a card pointing at nothing is worse than a step missed.</p>
 */
public class UITour extends UIElement
{
    private static final int CARD_WIDTH = 220;
    private static final int GAP = 10;
    private static final int PADDING = 8;
    private static final int MARGIN = 4;

    /** How long a step keeps waiting for its anchor to come back, in ticks. */
    private static final long PATIENCE = 60L;

    private static final int DIMMED = Colors.setA(Colors.WHITE, 0.7F);
    private static final int MUTED = Colors.setA(Colors.WHITE, 0.5F);

    public final TourChapter chapter;

    private final Runnable onFinished;
    private final UIElement card;
    private final UILabel title;
    private final UIText text;
    private final UILabel counter;

    private int index = -1;
    private boolean finished;

    /** Where the current anchor was last seen; the card is placed against it. */
    private final Area anchor = new Area();
    private boolean anchored;
    private long missingSince = -1L;

    public UITour(TourChapter chapter, Runnable onFinished)
    {
        this.chapter = chapter;
        this.onFinished = onFinished;

        this.title = UI.label(IKey.EMPTY, 16);
        this.title.labelAnchor(0F, 0.5F);
        this.text = new UIText().color(DIMMED, false).lineHeight(12);
        this.counter = UI.label(IKey.EMPTY, UIConstants.CONTROL_HEIGHT).color(MUTED);
        this.counter.labelAnchor(0F, 0.5F);

        UIIcon close = new UIIcon(Icons.CLOSE, (b) -> this.finish());
        UIButton next = new UIButton(UIKeys.ONBOARDING_TOUR_NEXT, (b) -> this.advance());

        close.w(20).h(16);
        next.w(90);

        UIElement head = new UIElement();
        UIElement foot = new UIElement();

        head.row(0).preferred(0).height(16);
        head.add(this.title, close);
        foot.row(4).preferred(0).height(UIConstants.CONTROL_HEIGHT);
        foot.add(this.counter, next);

        this.card = new UIElement();
        this.card.relative(this).w(CARD_WIDTH);
        this.card.column(6).vertical().stretch().padding(PADDING);
        this.card.markContainer().mouseEventPropagataion(EventPropagation.BLOCK_INSIDE);
        this.card.add(head, this.text, foot);
        this.card.setVisible(false);

        this.add(this.card);
    }

    public boolean isFinished()
    {
        return this.finished;
    }

    /** Take the tour down without ticking the chapter off — it will be offered again. */
    public void abandon()
    {
        this.finished = true;
        this.removeFromParent();
    }

    private void finish()
    {
        this.finished = true;
        this.removeFromParent();
        this.onFinished.run();
    }

    private void advance()
    {
        this.index += 1;
        this.anchored = false;
        this.missingSince = -1L;

        if (this.index >= this.chapter.steps().size())
        {
            this.finish();

            return;
        }

        Step step = this.chapter.steps().get(this.index);

        this.title.label = step.title();
        this.text.text(step.text());
        this.counter.label = UIKeys.ONBOARDING_TOUR_COUNTER.format(this.index + 1, this.chapter.steps().size());

        /* Two passes: the text learns its width on the first and its line count on the second */
        this.card.resize();
        this.card.resize();
    }

    @Override
    public void render(UIContext context)
    {
        if (this.finished)
        {
            return;
        }

        if (this.index < 0)
        {
            this.advance();
        }

        Area area = this.locate(context);

        if (this.finished)
        {
            return;
        }

        if (area == null)
        {
            this.card.setVisible(false);

            return;
        }

        this.renderHighlight(context, area);
        super.render(context);
    }

    /**
     * The current step's anchor, skipping forward past steps whose anchors aren't there. A step
     * already on screen gets {@link #PATIENCE} to see its anchor again before it is given up.
     */
    private Area locate(UIContext context)
    {
        while (!this.finished)
        {
            Step step = this.chapter.steps().get(this.index);
            Area area = TourAnchors.resolve(step.anchor());

            if (area != null)
            {
                this.missingSince = -1L;

                if (!this.anchored || !this.anchor.equals(area))
                {
                    this.anchor.copy(area);
                    this.anchored = true;
                    this.place();
                }

                return this.anchor;
            }

            if (this.anchored)
            {
                if (this.missingSince < 0L)
                {
                    this.missingSince = context.getTick();
                }

                if (context.getTick() - this.missingSince < PATIENCE)
                {
                    return null;
                }
            }

            this.advance();
        }

        return null;
    }

    /**
     * Beside the anchor on whichever side has room, in this order: right, left, below, above.
     * A place too large to have a side (the preview filling the screen) gets the card inside
     * itself, along its bottom edge.
     */
    private void place()
    {
        Area a = this.anchor;
        int w = CARD_WIDTH;
        int h = this.card.area.h;
        int x;
        int y;

        if (a.ex() + GAP + w <= this.area.ex() - MARGIN)
        {
            x = a.ex() + GAP;
            y = a.my() - h / 2;
        }
        else if (a.x - GAP - w >= this.area.x + MARGIN)
        {
            x = a.x - GAP - w;
            y = a.my() - h / 2;
        }
        else if (a.ey() + GAP + h <= this.area.ey() - MARGIN)
        {
            x = a.mx() - w / 2;
            y = a.ey() + GAP;
        }
        else if (a.y - GAP - h >= this.area.y + MARGIN)
        {
            x = a.mx() - w / 2;
            y = a.y - GAP - h;
        }
        else
        {
            x = a.mx() - w / 2;
            y = a.ey() - GAP - h;
        }

        x = MathUtils.clamp(x, this.area.x + MARGIN, this.area.ex() - MARGIN - w);
        y = MathUtils.clamp(y, this.area.y + MARGIN, this.area.ey() - MARGIN - h);

        this.card.xy(x - this.area.x, y - this.area.y);
        this.card.setVisible(true);
        this.card.resize();
    }

    /**
     * Everything but the place goes under the same dimming an overlay puts on the screen, the
     * place itself gets a frame that breathes so the eye finds it, and the card gets its own
     * surface. The dimming is paint, not glass: the editor under it still takes clicks.
     */
    private void renderHighlight(UIContext context, Area a)
    {
        int primary = BBSSettings.primaryColor.get() & Colors.RGB;
        int dim = BBSSettings.overlayBackground();
        float pulse = 0.7F + 0.3F * (float) Math.sin(context.getTickTransition() / 8D);
        Area s = this.area;

        if (Colors.getA(dim) > 0F)
        {
            context.batcher.box(s.x, s.y, s.ex(), a.y, dim);
            context.batcher.box(s.x, a.ey(), s.ex(), s.ey(), dim);
            context.batcher.box(s.x, a.y, a.x, a.ey(), dim);
            context.batcher.box(a.ex(), a.y, s.ex(), a.ey(), dim);
        }

        context.batcher.outline(a.x - 2, a.y - 2, a.ex() + 2, a.ey() + 2, Colors.setA(primary, pulse), 2);

        Area c = this.card.area;

        context.batcher.dropShadow(c.x, c.y, c.ex(), c.ey(), 8, BBSSettings.panelShadowOpaqueColor(), BBSSettings.panelShadowTransparentColor());
        context.batcher.box(c.x, c.y, c.ex(), c.ey(), BBSSettings.raisedSurface());
        context.batcher.box(c.x, c.y, c.x + 2, c.ey(), Colors.A100 | primary);
    }
}
