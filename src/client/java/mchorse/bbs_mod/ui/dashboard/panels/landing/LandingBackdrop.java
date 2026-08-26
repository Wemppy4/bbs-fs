package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.colors.Oklab;

/**
 * What the landing screen sits on: a dark ground with a few large, soft lights in the accent
 * colour drifting slowly behind the card, and a drift of tiny sparks rising through them.
 *
 * <p>The scene answers the cursor: the lights slide a little against it, each by its own
 * amount, so the ground reads as deep; the sparks are brushed aside by it; and a faint light
 * of its own follows it. All of that is eased, so nothing snaps.</p>
 *
 * <p>Everything else is a function of the clock and the card's own colours, so there is nothing
 * to reset and a change of the primary colour shows up at once. The companion lights are the
 * accent turned around the hue wheel in Oklab, so they stay in the same family whatever colour
 * the user picked.</p>
 */
public class LandingBackdrop
{
    /** The lights: positions and radii in fractions of the area, periods in seconds. */
    private static final float[][] LIGHTS = {
        /* x, y, radius (of height), sway x, sway y, period, hue turn in degrees, parallax */
        {0.22F, 0.78F, 0.62F, 0.12F, 0.08F, 29F, 0F, 0.10F},
        {0.80F, 0.55F, 0.50F, 0.10F, 0.14F, 37F, 55F, 0.06F},
        {0.55F, 1.05F, 0.42F, 0.18F, 0.06F, 23F, -45F, 0.03F},
    };

    private static final int SEGMENTS = 48;
    private static final float LIGHT_ALPHA = 0.32F;

    private static final int SPARKS = 48;
    private static final float SPARK_MIN_SPEED = 5F;
    private static final float SPARK_MAX_SPEED = 13F;
    private static final float SPARK_ALPHA = 0.55F;

    /** The ground darkens toward the bottom, so the lights have something to shine against. */
    private static final float GROUND_BOTTOM = 0.55F;

    /** How quickly the scene catches up with the cursor, per second; higher is snappier. */
    private static final float FOLLOW_RATE = 5F;

    /** The cursor's own light. */
    private static final float CURSOR_LIGHT_RADIUS = 0.3F;
    private static final float CURSOR_LIGHT_ALPHA = 0.16F;

    /** How far around the cursor sparks are brushed aside, and how far they go. */
    private static final float BRUSH_RADIUS = 90F;
    private static final float BRUSH_PUSH = 28F;

    private final Oklab oklab = new Oklab();

    /* The cursor as the scene sees it: eased toward the real one, and how much it is "here" */
    private float cursorX;
    private float cursorY;
    private float presence;
    private float lastTime;

    public void render(UIContext context, Area area)
    {
        float time = context.getTickTransition() / 20F;
        int base = BBSSettings.baseSurface();
        int accent = BBSSettings.primaryColor.get() & Colors.RGB;

        this.followCursor(context, area, time);

        context.batcher.gradientVBox(area.x, area.y, area.ex(), area.ey(), Colors.A100 | base, Colors.A100 | Colors.mulRGB(base, GROUND_BOTTOM));

        context.batcher.clip(area, context);
        this.renderLights(context, area, time, accent);
        this.renderCursorLight(context, area, accent);
        this.renderSparks(context, area, time, accent);
        context.batcher.unclip(context);
    }

    private void followCursor(UIContext context, Area area, float time)
    {
        float delta = Math.max(0F, Math.min(0.1F, time - this.lastTime));
        float ease = 1F - (float) Math.exp(-delta * FOLLOW_RATE);
        boolean inside = area.isInside(context.mouseX, context.mouseY);

        this.lastTime = time;

        if (inside)
        {
            this.cursorX += (context.mouseX - this.cursorX) * ease;
            this.cursorY += (context.mouseY - this.cursorY) * ease;
        }

        this.presence += ((inside ? 1F : 0F) - this.presence) * ease;
    }

    private void renderLights(UIContext context, Area area, float time, int accent)
    {
        /* The cursor's offset from the middle, -0.5..0.5 of the area; the lights slide against it */
        float lookX = (this.cursorX - area.mx()) / area.w * this.presence;
        float lookY = (this.cursorY - area.my()) / area.h * this.presence;

        for (float[] light : LIGHTS)
        {
            double phase = time / light[5] * Math.PI * 2;
            float parallax = light[7];
            int x = area.x + (int) ((light[0] + Math.sin(phase) * light[3] - lookX * parallax) * area.w);
            int y = area.y + (int) ((light[1] + Math.cos(phase * 0.7) * light[4] - lookY * parallax) * area.h);
            int radius = (int) (light[2] * area.h);
            int color = this.turnHue(accent, light[6]);

            context.batcher.dropCircleShadow(x, y, radius, 0, SEGMENTS, Colors.setA(color, LIGHT_ALPHA), Colors.setA(color, 0F));
        }
    }

    private void renderCursorLight(UIContext context, Area area, int accent)
    {
        if (this.presence < 0.01F)
        {
            return;
        }

        int radius = (int) (CURSOR_LIGHT_RADIUS * area.h);

        context.batcher.dropCircleShadow((int) this.cursorX, (int) this.cursorY, radius, 0, SEGMENTS, Colors.setA(accent, CURSOR_LIGHT_ALPHA * this.presence), Colors.setA(accent, 0F));
    }

    /**
     * Sparks drift up through the lights: each one has its own lane, speed, sway and twinkle,
     * all read off its index, and wraps back to the bottom once it has left through the top.
     */
    private void renderSparks(UIContext context, Area area, float time, int accent)
    {
        int cycle = area.h + 20;

        for (int i = 0; i < SPARKS; i++)
        {
            float lane = hash(i, 1);
            float speed = SPARK_MIN_SPEED + (SPARK_MAX_SPEED - SPARK_MIN_SPEED) * hash(i, 2);
            float sway = (float) Math.sin(time * (0.3F + 0.4F * hash(i, 3)) + hash(i, 4) * Math.PI * 2) * 0.02F;
            float travel = (time * speed + hash(i, 5) * cycle) % cycle;

            float x = area.x + (lane + sway) * area.w;
            float y = area.ey() + 10 - travel;

            /* Brushed aside by the cursor: the closer, the further */
            float dx = x - this.cursorX;
            float dy = y - this.cursorY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance < BRUSH_RADIUS && this.presence > 0F)
            {
                float push = 1F - distance / BRUSH_RADIUS;
                float factor = push * push * BRUSH_PUSH * this.presence / Math.max(distance, 1F);

                x += dx * factor;
                y += dy * factor;
            }

            /* Born below the edge, gone above it; in between, a twinkle of its own */
            float edge = Math.min(1F, Math.min(area.ey() - y, y - area.y) / (area.h * 0.2F));

            if (edge <= 0F)
            {
                continue;
            }

            float twinkle = 0.6F + 0.4F * (float) Math.sin(time * (1F + 2F * hash(i, 6)) + hash(i, 7) * Math.PI * 2);
            int size = hash(i, 8) < 0.3F ? 2 : 1;
            int color = Colors.setA(Colors.lerp(Colors.WHITE, accent, hash(i, 9)), SPARK_ALPHA * edge * twinkle);
            int px = (int) x;
            int py = (int) y;

            context.batcher.box(px, py, px + size, py + size, color);
        }
    }

    /** The accent turned around the hue wheel, at the same lightness — a sibling, not a stranger. */
    private int turnHue(int color, float degrees)
    {
        if (degrees == 0F)
        {
            return color;
        }

        this.oklab.set(color);

        double angle = Math.toRadians(degrees);
        float a = this.oklab.a;
        float b = this.oklab.b;

        this.oklab.a = (float) (a * Math.cos(angle) - b * Math.sin(angle));
        this.oklab.b = (float) (a * Math.sin(angle) + b * Math.cos(angle));

        return this.oklab.toRGB() & Colors.RGB;
    }

    /** A stable pseudo-random number in [0, 1) for a spark and one of its traits. */
    private static float hash(int index, int trait)
    {
        int n = index * 374761393 + trait * 668265263;

        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;

        return (n & 0xffffff) / (float) 0x1000000;
    }
}
