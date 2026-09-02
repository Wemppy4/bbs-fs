package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.IPlaceableClip;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValuePlacement;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.List;

public class ImageClip extends CameraClip implements IPlaceableClip
{
    public ValueLink texture = new ValueLink("texture", null);
    public ValuePlacement placement = new ValuePlacement("placement", new Placement());
    public ValueInt color = new ValueInt("color", Colors.WHITE);
    public ValueBoolean fullscreen = new ValueBoolean("fullscreen", false);
    public ValueBoolean smooth = new ValueBoolean("smooth", true);
    public ValueTransform transform = new ValueTransform("transform", new Transform());

    private ImageOverlay image = new ImageOverlay();

    public static List<ImageOverlay> getImages(ClipContext context)
    {
        return context.clipData.get("images", ArrayList::new);
    }

    public ImageOverlay getOverlay()
    {
        return this.image;
    }

    @Override
    public ValuePlacement getPlacement()
    {
        /* Nothing to move while it covers the frame. */
        return this.fullscreen.get() ? null : this.placement;
    }

    @Override
    public OverlayBox getOverlayBox()
    {
        return this.image.box;
    }

    public ImageClip()
    {
        this.add(this.texture);
        this.add(this.placement);
        this.add(this.color);
        this.add(this.fullscreen);
        this.add(this.smooth);
        this.add(this.transform);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        List<ImageOverlay> images = getImages(context);
        float factor = this.envelope.factorEnabled(this.duration.get(), context.relativeTick + context.transition);
        int color = Colors.setA(this.color.get(), factor * Colors.getA(this.color.get()));

        this.image.update(this.texture.get(), this.placement.get(), color, this.fullscreen.get(), this.smooth.get());
        this.image.updateTransform(this.transform.get(), factor);
        images.add(this.image);
    }

    @Override
    protected Clip create()
    {
        return new ImageClip();
    }
}
