package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.landing.LandingBackdrop;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingScreen;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * "First time in BBS FS?" — the whole screen, once. One tab at a time in the middle, right
 * on the backdrop, arrows at the sides to walk them, the title above and the dots below. Every tab
 * is one decision: how it looks, whether video can be saved, where to go next. The last one
 * holds the only way out; what going out starts is {@code Onboarding}'s business, hooked to
 * the close event by whoever opens this.
 *
 * <p>Built on the overlay panel for the sake of the overlay stack (Escape, the layer, the
 * close event), but wears none of its chrome: no title bar, no grip, no shadow — it is the
 * screen, not a window on it.</p>
 */
public class UIWelcomeOverlayPanel extends UIOverlayPanel
{
    private static final int PAGE_W = 320;
    private static final int PAGE_H = 150;
    private static final int PAGE_SHIFT = 20;
    private static final int ARROW_SIZE = 32;
    private static final int ARROW_W = 44;
    private static final int ARROW_H = 80;
    private static final int ARROW_GAP = 8;
    private static final int TITLE_SCALE = 2;
    private static final int TITLE_GAP = 22;
    private static final int DOT = 6;
    private static final int DOT_GAP = 6;
    private static final int DOTS_Y = 16;

    private static final int DIMMED = Colors.setA(Colors.WHITE, 0.7F);
    private static final int MUTED = Colors.setA(Colors.WHITE, 0.35F);

    private final List<UIWelcomePage> pages = new ArrayList<>();
    private final UIElement host;
    private final UIArrow prev;
    private final UIArrow next;
    private final LandingBackdrop backdrop = new LandingBackdrop();

    private int index = -1;

    public UIWelcomeOverlayPanel()
    {
        super(UIKeys.ONBOARDING_WELCOME_TITLE);

        this.title.setVisible(false);
        this.icons.setVisible(false);
        this.content.resetFlex().relative(this).xy(0, 0).w(1F).h(1F);

        /* A little under the middle, so the title above it doesn't crowd the top edge */
        this.host = new UIElement();
        this.host.relative(this.content).x(0.5F).y(0.5F, PAGE_SHIFT).wh(PAGE_W, PAGE_H).anchor(0.5F);

        this.prev = new UIArrow(Icons.ARROW_LEFT, (b) -> this.show(this.index - 1));
        this.next = new UIArrow(Icons.ARROW_RIGHT, (b) -> this.show(this.index + 1));
        this.prev.wh(ARROW_W, ARROW_H).relative(this.host).x(0, -ARROW_W - ARROW_GAP).y(0.5F).anchorY(0.5F);
        this.next.wh(ARROW_W, ARROW_H).relative(this.host).x(1F, ARROW_GAP).y(0.5F).anchorY(0.5F);

        this.pages.add(new UIAppearancePage());
        this.pages.add(new UIEncoderPage());
        this.pages.add(new UILayoutPage());
        this.pages.add(new UINextStepsPage(this));

        this.content.add(new UIRenderable(this::renderFraming), this.host, this.prev, this.next);

        this.show(0);
    }

    public void show(int index)
    {
        index = MathUtils.clamp(index, 0, this.pages.size() - 1);

        if (index == this.index)
        {
            return;
        }

        if (this.index >= 0)
        {
            this.pages.get(this.index).removeFromParent();
        }

        this.index = index;

        UIWelcomePage page = this.pages.get(index);

        page.resetFlex().relative(this.host).xy(0, 0).w(1F).h(1F);
        this.host.add(page);
        this.prev.setVisible(index > 0);
        this.next.setVisible(index < this.pages.size() - 1);
        this.host.resize();
        page.onShown();
    }

    /** It is the screen: nothing to drag, nothing to size. */
    @Override
    public boolean isResizable()
    {
        return false;
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (!context.isFocused())
        {
            if (context.isPressed(GLFW.GLFW_KEY_LEFT))
            {
                this.show(this.index - 1);

                return true;
            }
            else if (context.isPressed(GLFW.GLFW_KEY_RIGHT))
            {
                this.show(this.index + 1);

                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        this.backdrop.render(context, this.area);
    }

    /** The title over the tab and the dots under it. */
    private void renderFraming(UIContext context)
    {
        Area card = this.host.area;
        FontRenderer font = context.batcher.getFont();
        String title = UIKeys.ONBOARDING_WELCOME_TITLE.format(UILandingScreen.getVersion()).get();
        MatrixStack stack = context.batcher.getContext().getMatrices();

        int titleW = font.getWidth(title) * TITLE_SCALE;
        int titleY = card.y - TITLE_GAP - font.getHeight() * TITLE_SCALE;

        stack.push();
        stack.translate(card.mx() - titleW / 2F, titleY, 0F);
        stack.scale(TITLE_SCALE, TITLE_SCALE, 1F);
        context.batcher.text(title, 0, 0, Colors.WHITE, true);
        stack.pop();

        int count = this.pages.size();
        int dotsW = count * DOT + (count - 1) * DOT_GAP;
        int x = card.mx() - dotsW / 2;
        int y = card.ey() + DOTS_Y;
        int primary = BBSSettings.primaryColor.get() & Colors.RGB;

        for (int i = 0; i < count; i++)
        {
            context.batcher.box(x, y, x + DOT, y + DOT, i == this.index ? Colors.A100 | primary : MUTED);

            x += DOT + DOT_GAP;
        }
    }

    /**
     * A tab switch: a big arrow with a big hit area, since it is the only thing at that side
     * of the screen. Not a {@code UIIcon} — that one draws its icon at its own size, always.
     */
    private static class UIArrow extends UIClickable<UIArrow>
    {
        private final Icon icon;

        public UIArrow(Icon icon, Consumer<UIArrow> callback)
        {
            super(callback);

            this.icon = icon;
        }

        @Override
        protected UIArrow get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            int color = this.hover ? Colors.WHITE : DIMMED;

            context.batcher.scaledIcon(this.icon, color, this.area.mx() - ARROW_SIZE / 2F, this.area.my() - ARROW_SIZE / 2F, ARROW_SIZE);
        }
    }
}
