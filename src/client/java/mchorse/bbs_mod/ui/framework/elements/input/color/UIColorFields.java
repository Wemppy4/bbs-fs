package mchorse.bbs_mod.ui.framework.elements.input.color;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.MathUtils;

/**
 * The channels of a color as a row of numbers: red, green and blue, or hue, saturation and
 * value, with alpha last when the picker edits it.
 *
 * <p>The numbers are shown the way the rest of the world writes them — RGB counting to 255,
 * hue in degrees, everything else in percent — while the color itself stays in 0..1 floats.
 * This element only reports which channel was edited and what it now is, normalized; what
 * that means for the color is the picker's business, so the hue of a black color isn't lost
 * on the way through.</p>
 */
public class UIColorFields extends UIElement
{
    /** Channels including alpha. */
    public static final int CHANNELS = 4;

    private static final IKey[] RGB_LABELS = {UIKeys.COLOR_CHANNEL_RED, UIKeys.COLOR_CHANNEL_GREEN, UIKeys.COLOR_CHANNEL_BLUE, UIKeys.COLOR_CHANNEL_ALPHA};
    private static final IKey[] HSV_LABELS = {UIKeys.COLOR_CHANNEL_HUE, UIKeys.COLOR_CHANNEL_SATURATION, UIKeys.COLOR_CHANNEL_VALUE, UIKeys.COLOR_CHANNEL_ALPHA};

    /** What a full channel counts to on screen. */
    private static final float[] RGB_SCALE = {255F, 255F, 255F, 255F};
    private static final float[] HSV_SCALE = {360F, 100F, 100F, 100F};

    /** A channel was edited to a value in 0..1. */
    @FunctionalInterface
    public interface ChannelConsumer
    {
        void accept(int channel, float value);
    }

    private final UITrackpad[] fields = new UITrackpad[CHANNELS];
    private final ChannelConsumer callback;

    private boolean hsv;
    private boolean alpha;
    private boolean built;

    public UIColorFields(ChannelConsumer callback)
    {
        super();

        this.callback = callback;

        for (int i = 0; i < CHANNELS; i++)
        {
            final int channel = i;
            UITrackpad field = new UITrackpad((value) -> this.channelEdited(channel, value));

            field.h(UIConstants.CONTROL_HEIGHT);

            this.fields[i] = field;
        }

        this.row(2);
        this.h(UIConstants.CONTROL_HEIGHT);
    }

    /** Which channels are shown, and in what units. */
    public void mode(boolean hsv, boolean alpha)
    {
        if (this.built && this.hsv == hsv && this.alpha == alpha)
        {
            return;
        }

        this.hsv = hsv;
        this.alpha = alpha;
        this.built = true;

        this.removeAll();

        int count = alpha ? CHANNELS : CHANNELS - 1;

        for (int i = 0; i < count; i++)
        {
            UITrackpad field = this.fields[i];

            field.limit(0D, this.scale(i), true);
            field.increment(1D);
            field.values(1D);
            field.tooltip((hsv ? HSV_LABELS : RGB_LABELS)[i]);

            this.add(field);
        }
    }

    /**
     * Show the given channels, all normalized. A field the user is typing into is left
     * alone — it would fight the caret otherwise.
     */
    public void update(float c0, float c1, float c2, float a)
    {
        this.show(0, c0);
        this.show(1, c1);
        this.show(2, c2);
        this.show(3, a);
    }

    private void show(int channel, float value)
    {
        UITrackpad field = this.fields[channel];

        if (!field.isFocused())
        {
            field.setValue(Math.round(MathUtils.clamp(value, 0F, 1F) * this.scale(channel)));
        }
    }

    private void channelEdited(int channel, double value)
    {
        if (this.callback != null)
        {
            this.callback.accept(channel, (float) MathUtils.clamp(value / this.scale(channel), 0D, 1D));
        }
    }

    private float scale(int channel)
    {
        return (this.hsv ? HSV_SCALE : RGB_SCALE)[channel];
    }
}
