package mchorse.bbs_mod.utils.colors;

/**
 * Oklab, the perceptual colour space the interface's surfaces are derived in.
 *
 * The reason it is worth a class of its own is that a step in {@link #l} reads
 * as the same step in depth whatever the colour: nudging a tinted surface up by
 * 0.022 looks like the same move as nudging a neutral grey one, and sRGB is not
 * even close to that. That property is what lets the entire tonal ladder of the
 * interface fall out of a single colour the user picks.
 *
 * The instance is mutable and carries a scratch buffer, so it is meant to be
 * kept around and reused rather than allocated per conversion.
 */
public class Oklab
{
    /**
     * Perceptual lightness, 0 (black) to 1 (white). The ladder moves along this
     * axis and nothing else.
     */
    public float l;

    /** Green to red axis: half of the tint, which the ladder carries unchanged. */
    public float a;

    /** Blue to yellow axis: the other half of the tint. */
    public float b;

    /**
     * How many halvings the gamut search takes. Twelve puts the chroma error
     * below a thousandth, which is far finer than an 8 bit channel can show.
     */
    private static final int GAMUT_STEPS = 12;

    /** Slack for the gamut test, so rounding noise does not count as clipping. */
    private static final double GAMUT_EPSILON = 1e-6;

    private final double[] linear = new double[3];

    /**
     * Read a colour in. Alpha is ignored — the ladder deals in opaque surfaces.
     */
    public Oklab set(int color)
    {
        double r = toLinear((color >> 16) & 0xff);
        double g = toLinear((color >> 8) & 0xff);
        double b = toLinear(color & 0xff);

        double longW = Math.cbrt(0.4122214708D * r + 0.5363325363D * g + 0.0514459929D * b);
        double mediumW = Math.cbrt(0.2119034982D * r + 0.6806995451D * g + 0.1073969566D * b);
        double shortW = Math.cbrt(0.0883024619D * r + 0.2817188376D * g + 0.6299787005D * b);

        this.l = (float) (0.2104542553D * longW + 0.7936177850D * mediumW - 0.0040720468D * shortW);
        this.a = (float) (1.9779984951D * longW - 2.4285922050D * mediumW + 0.4505937099D * shortW);
        this.b = (float) (0.0259040371D * longW + 0.7827717662D * mediumW - 0.8086757660D * shortW);

        return this;
    }

    public int toRGB()
    {
        return this.toRGB(this.l);
    }

    /**
     * The same colour taken to another lightness, packed opaque. Out of gamut
     * results give up chroma rather than lightness: a level of the ladder that
     * cannot hold the full tint still lands where the ladder says it should,
     * just paler. Clipping the channels instead would slide it off both its
     * lightness and its hue, which is the trap the previous light ramp fell
     * into.
     */
    public int toRGB(float lightness)
    {
        float chroma = 1F;

        this.toLinearSRGB(lightness, this.a, this.b);

        if (!this.inGamut())
        {
            float low = 0F;
            float high = 1F;

            for (int i = 0; i < GAMUT_STEPS; i++)
            {
                float middle = (low + high) / 2F;

                this.toLinearSRGB(lightness, this.a * middle, this.b * middle);

                if (this.inGamut())
                {
                    low = middle;
                }
                else
                {
                    high = middle;
                }
            }

            chroma = low;

            this.toLinearSRGB(lightness, this.a * chroma, this.b * chroma);
        }

        return 0xff000000
            | (toByte(this.linear[0]) << 16)
            | (toByte(this.linear[1]) << 8)
            | toByte(this.linear[2]);
    }

    private void toLinearSRGB(float l, float a, float b)
    {
        double longW = l + 0.3963377774D * a + 0.2158037573D * b;
        double mediumW = l - 0.1055613458D * a - 0.0638541728D * b;
        double shortW = l - 0.0894841775D * a - 1.2914855480D * b;

        longW = longW * longW * longW;
        mediumW = mediumW * mediumW * mediumW;
        shortW = shortW * shortW * shortW;

        this.linear[0] = 4.0767416621D * longW - 3.3077115913D * mediumW + 0.2309699292D * shortW;
        this.linear[1] = -1.2684380046D * longW + 2.6097574011D * mediumW - 0.3413193965D * shortW;
        this.linear[2] = -0.0041960863D * longW - 0.7034186147D * mediumW + 1.7076147010D * shortW;
    }

    private boolean inGamut()
    {
        for (double channel : this.linear)
        {
            if (channel < -GAMUT_EPSILON || channel > 1D + GAMUT_EPSILON)
            {
                return false;
            }
        }

        return true;
    }

    private static double toLinear(int channel)
    {
        double value = channel / 255D;

        return value <= 0.04045D ? value / 12.92D : Math.pow((value + 0.055D) / 1.055D, 2.4D);
    }

    private static int toByte(double linear)
    {
        double value = linear <= 0D ? 0D : (linear >= 1D ? 1D : linear);

        value = value <= 0.0031308D ? value * 12.92D : 1.055D * Math.pow(value, 1D / 2.4D) - 0.055D;

        return (int) Math.round(value * 255D);
    }
}
