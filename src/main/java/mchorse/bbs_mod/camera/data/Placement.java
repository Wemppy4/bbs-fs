package mchorse.bbs_mod.camera.data;

import mchorse.bbs_mod.data.IDataSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.Objects;

/**
 * Where an overlay (subtitle, image, video) sits on the screen.
 *
 * Coordinates live in a resolution independent "virtual frame" that is always
 * {@link #HEIGHT} units tall (the width follows the frame's aspect ratio), so
 * a film authored at one resolution exports identically at any other. Window
 * and anchor are fractions (0..1); offset and scale are in frame units.
 */
public class Placement implements IDataSerializable<BaseType>
{
    /** The virtual frame's height in units (a 16:9 frame is 1920x1080 units). */
    public static final float HEIGHT = 1080F;

    public float windowX = 0.5F;
    public float windowY = 0.5F;
    public float anchorX = 0.5F;
    public float anchorY = 0.5F;
    public float offsetX;
    public float offsetY;
    public float scale = 1F;

    /**
     * Read the flat fields overlay clips carried before the placement object
     * existed. Those lived in half framebuffer pixels, which at the reference
     * 1080p window are half a frame unit each - hence the doubling.
     */
    public static Placement fromLegacy(MapType data, String scaleKey, float defaultScale)
    {
        Placement placement = new Placement();

        placement.windowX = data.getFloat("windowX", 0.5F);
        placement.windowY = data.getFloat("windowY", 0.5F);
        placement.anchorX = data.getFloat("anchorX", 0.5F);
        placement.anchorY = data.getFloat("anchorY", 0.5F);
        placement.offsetX = data.getFloat("x", 0F) * 2F;
        placement.offsetY = data.getFloat("y", 0F) * 2F;
        placement.scale = data.getFloat(scaleKey, defaultScale) * 2F;

        return placement;
    }

    public Placement()
    {}

    public Placement(float scale)
    {
        this.scale = scale;
    }

    public Placement copy()
    {
        Placement placement = new Placement();

        placement.set(this);

        return placement;
    }

    public void set(Placement placement)
    {
        this.windowX = placement.windowX;
        this.windowY = placement.windowY;
        this.anchorX = placement.anchorX;
        this.anchorY = placement.anchorY;
        this.offsetX = placement.offsetX;
        this.offsetY = placement.offsetY;
        this.scale = placement.scale;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Placement placement)
        {
            return this.windowX == placement.windowX
                && this.windowY == placement.windowY
                && this.anchorX == placement.anchorX
                && this.anchorY == placement.anchorY
                && this.offsetX == placement.offsetX
                && this.offsetY == placement.offsetY
                && this.scale == placement.scale;
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.windowX, this.windowY, this.anchorX, this.anchorY, this.offsetX, this.offsetY, this.scale);
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();

        data.putFloat("windowX", this.windowX);
        data.putFloat("windowY", this.windowY);
        data.putFloat("anchorX", this.anchorX);
        data.putFloat("anchorY", this.anchorY);
        data.putFloat("offsetX", this.offsetX);
        data.putFloat("offsetY", this.offsetY);
        data.putFloat("scale", this.scale);

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();

        this.windowX = map.getFloat("windowX", 0.5F);
        this.windowY = map.getFloat("windowY", 0.5F);
        this.anchorX = map.getFloat("anchorX", 0.5F);
        this.anchorY = map.getFloat("anchorY", 0.5F);
        this.offsetX = map.getFloat("offsetX", 0F);
        this.offsetY = map.getFloat("offsetY", 0F);
        this.scale = map.getFloat("scale", 1F);
    }
}
