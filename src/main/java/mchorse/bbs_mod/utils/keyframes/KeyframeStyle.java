package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.Objects;

/**
 * How a keyframe looks on a timeline: its shape, its own colour and whether that shape reads as a
 * solid blob or as a ring around a dark core.
 *
 * <p>Every keyframe owns one, and a fresh keyframe copies the one configured in the settings. The
 * colour is nullable on purpose: absent means "whatever colour the track is", which is what almost
 * every keyframe wants and what lets a recoloured track recolour its keyframes along with it.</p>
 *
 * <p>It writes itself into the keyframe's own map rather than into a nested one, so films saved
 * before the style became an object still load: the {@code shape} and {@code color} keys are the
 * same keys {@link Keyframe} used to write by hand.</p>
 */
public class KeyframeStyle
{
    private KeyframeShape shape = KeyframeShape.SQUARE;
    private Color color;
    private boolean filled;

    public KeyframeShape getShape()
    {
        return this.shape;
    }

    public void setShape(KeyframeShape shape)
    {
        this.shape = shape == null ? KeyframeShape.SQUARE : shape;
    }

    public Color getColor()
    {
        return this.color;
    }

    public void setColor(Color color)
    {
        this.color = color;
    }

    public boolean isFilled()
    {
        return this.filled;
    }

    public void setFilled(boolean filled)
    {
        this.filled = filled;
    }

    public KeyframeStyle copy()
    {
        KeyframeStyle style = new KeyframeStyle();

        style.copy(this);

        return style;
    }

    public void copy(KeyframeStyle style)
    {
        this.shape = style.shape;
        this.color = style.color == null ? null : style.color.copy();
        this.filled = style.filled;
    }

    /**
     * Back to what a keyframe looks like with nothing configured - which is also what an absent
     * entry in saved data means, so {@link #fromData(MapType)} starts here.
     */
    public void reset()
    {
        this.shape = KeyframeShape.SQUARE;
        this.color = null;
        this.filled = false;
    }

    public boolean isDefault()
    {
        return this.shape == KeyframeShape.SQUARE && this.color == null && !this.filled;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof KeyframeStyle style)
        {
            return this.shape == style.shape
                && this.filled == style.filled
                && Objects.equals(this.color, style.color);
        }

        return false;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.shape, this.color, this.filled);
    }

    public void toData(MapType data)
    {
        if (this.color != null) data.putInt("color", this.color.getRGBColor());
        if (this.shape != KeyframeShape.SQUARE) data.putString("shape", this.shape.toString().toUpperCase());
        if (this.filled) data.putBool("filled", true);
    }

    public void fromData(MapType map)
    {
        this.reset();

        if (map.has("shape")) this.shape = KeyframeShape.fromString(map.getString("shape"));
        if (map.has("color")) this.color = Color.rgb(map.getInt("color"));
        if (map.has("filled")) this.filled = map.getBool("filled");
    }
}
