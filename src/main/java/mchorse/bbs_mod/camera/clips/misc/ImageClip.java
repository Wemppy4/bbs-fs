package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.List;

public class ImageClip extends CameraClip
{
    public ValueLink texture = new ValueLink("texture", null);
    public ValueInt x = new ValueInt("x", 0);
    public ValueInt y = new ValueInt("y", 0);
    public ValueFloat windowX = new ValueFloat("windowX", 0.5F);
    public ValueFloat windowY = new ValueFloat("windowY", 0.5F);
    public ValueFloat anchorX = new ValueFloat("anchorX", 0.5F);
    public ValueFloat anchorY = new ValueFloat("anchorY", 0.5F);
    public ValueFloat scale = new ValueFloat("scale", 1F);
    public ValueInt color = new ValueInt("color", Colors.WHITE);
    public ValueBoolean fullscreen = new ValueBoolean("fullscreen", false);
    public ValueBoolean smooth = new ValueBoolean("smooth", true);
    public ValueTransform transform = new ValueTransform("transform", new Transform());

    private ImageOverlay image = new ImageOverlay();

    public static List<ImageOverlay> getImages(ClipContext context)
    {
        return context.clipData.get("images", ArrayList::new);
    }

    public ImageClip()
    {
        this.add(this.texture);
        this.add(this.x);
        this.add(this.y);
        this.add(this.windowX);
        this.add(this.windowY);
        this.add(this.anchorX);
        this.add(this.anchorY);
        this.add(this.scale);
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

        this.image.update(this.texture.get(), this.x.get(), this.y.get(), this.scale.get(), color, this.fullscreen.get(), this.smooth.get());
        this.image.updateWindow(this.windowX.get(), this.windowY.get());
        this.image.updateAnchor(this.anchorX.get(), this.anchorY.get());
        this.image.updateTransform(this.transform.get(), factor);
        images.add(this.image);
    }

    @Override
    protected Clip create()
    {
        return new ImageClip();
    }
}
