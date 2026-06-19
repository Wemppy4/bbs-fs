package mchorse.bbs_mod.ui.framework.elements.input.multilink;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.UICanvasEditor;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.FilteredLink;

public class UIMultiLinkEditor extends UICanvasEditor
{
    public UITexturePicker picker;
    public FilteredLink link;

    public UIToggle autoSize;
    public UITrackpad sizeW;
    public UITrackpad sizeH;

    public UIColor color;
    public UITrackpad scale;
    public UIToggle scaleToLargest;
    public UITrackpad shiftX;
    public UITrackpad shiftY;

    public UITrackpad pixelate;
    public UIToggle erase;

    public UIMultiLinkEditor(UITexturePicker picker)
    {
        super();

        this.picker = picker;

        this.autoSize = new UIToggle(UIKeys.TEXTURE_EDITOR_AUTO_SIZE, (toggle) ->
        {
            this.link.autoSize = toggle.getValue();
            this.resizeCanvas();
        });
        this.autoSize.tooltip(UIKeys.TEXTURE_EDITOR_AUTO_SIZE_TOOLTIP);
        this.sizeW = new UITrackpad((value) ->
        {
            this.link.sizeW = value.intValue();
            this.resizeCanvas();
        });
        this.sizeW.integer().limit(0).tooltip(UIKeys.TEXTURE_EDITOR_SIZE_W);
        this.sizeH = new UITrackpad((value) ->
        {
            this.link.sizeH = value.intValue();
            this.resizeCanvas();
        });
        this.sizeH.integer().limit(0).tooltip(UIKeys.TEXTURE_EDITOR_SIZE_H);

        this.color = new UIColor((value) -> this.link.color = value).withAlpha();
        this.color.direction(Direction.TOP).tooltip(UIKeys.TEXTURE_EDITOR_COLOR);
        this.scale = new UITrackpad((value) -> this.link.scale = value.floatValue());
        this.scale.limit(0).metric();
        this.scaleToLargest = new UIToggle(UIKeys.TEXTURE_EDITOR_SCALE_TO_LARGEST, (toggle) -> this.link.scaleToLargest = toggle.getValue());
        this.shiftX = new UITrackpad((value) -> this.link.shiftX = value.intValue());
        this.shiftX.integer();
        this.shiftY = new UITrackpad((value) -> this.link.shiftY = value.intValue());
        this.shiftY.integer();

        this.pixelate = new UITrackpad((value) -> this.link.pixelate = value.intValue());
        this.pixelate.integer().limit(1);
        this.erase = new UIToggle(UIKeys.TEXTURE_EDITOR_ERASE, (toggle) -> this.link.erase = toggle.getValue());
        this.erase.tooltip(UIKeys.TEXTURE_EDITOR_ERASE_TOOLTIP, Direction.TOP);

        this.editor.add(this.color);
        this.editor.add(UI.label(UIKeys.TEXTURE_EDITOR_SCALE).background(), this.scale, this.scaleToLargest);
        this.editor.add(UI.label(UIKeys.TEXTURE_EDITOR_SHIFT).background(), this.shiftX, this.shiftY);
        this.editor.add(UI.label(UIKeys.TEXTURE_EDITOR_PIXELATE).background(), this.pixelate, this.erase);
        this.editor.add(UI.label(UIKeys.TEXTURE_EDITOR_CUSTOM_SIZE).background(), this.autoSize, this.sizeW, this.sizeH);
    }

    public void resetView()
    {
        int w = 0;
        int h = 0;

        for (FilteredLink child : this.picker.multiLink.children)
        {
            Texture texture = BBSModClient.getTextures().getTexture(child.path);

            w = Math.max(w, child.getWidth(texture.width));
            h = Math.max(h, child.getHeight(texture.height));
        }

        this.setSize(w, h);
        this.color.picker.removeFromParent();
    }

    private void resizeCanvas()
    {
        int w = 0;
        int h = 0;

        for (FilteredLink child : this.picker.multiLink.children)
        {
            try
            {
                Texture texture = BBSModClient.getTextures().getTexture(child.path);

                w = Math.max(w, child.getWidth(texture.width));
                h = Math.max(h, child.getHeight(texture.height));
            }
            catch (Exception e)
            {}
        }

        if (w != this.getWidth() || h != this.getHeight())
        {
            this.setSize(w, h);
        }
    }

    public void close()
    {
        this.color.picker.removeFromParent();
    }

    public void setLink(FilteredLink link)
    {
        this.link = link;

        this.color.setColor(link.color);
        this.scale.setValue(link.scale);
        this.scaleToLargest.setValue(link.scaleToLargest);
        this.shiftX.setValue(link.shiftX);
        this.shiftY.setValue(link.shiftY);

        this.pixelate.setValue(link.pixelate);
        this.erase.setValue(link.erase);

        this.autoSize.setValue(link.autoSize);
        this.sizeW.setValue(link.sizeW);
        this.sizeH.setValue(link.sizeH);
    }

    @Override
    protected void startDragging(UIContext context)
    {
        super.startDragging(context);

        if (this.mouse == 0)
        {
            this.lastT = this.link.shiftX;
            this.lastV = this.link.shiftY;
        }
    }

    @Override
    protected void dragging(UIContext context)
    {
        super.dragging(context);

        if (this.dragging && this.mouse == 0)
        {
            double dx = (context.mouseX - this.lastX) / this.scaleX.getZoom();
            double dy = (context.mouseY - this.lastY) / this.scaleY.getZoom();

            if (Window.isShiftPressed()) dx = 0;
            if (Window.isCtrlPressed()) dy = 0;

            this.link.shiftX = (int) (dx) + (int) this.lastT;
            this.link.shiftY = (int) (dy) + (int) this.lastV;

            this.shiftX.setValue(this.link.shiftX);
            this.shiftY.setValue(this.link.shiftY);
        }
    }

    @Override
    protected boolean shouldDrawCanvas(UIContext context)
    {
        return this.picker.multiLink != null;
    }

    @Override
    protected void renderCanvasFrame(UIContext context)
    {
        for (FilteredLink child : this.picker.multiLink.children)
        {
            Texture texture = context.render.getTextures().getTexture(child.path);

            int ow = texture.width;
            int oh = texture.height;
            int ww = ow;
            int hh = oh;

            if (child.scaleToLargest)
            {
                ww = this.w;
                hh = this.h;
            }
            else if (child.scale != 1)
            {
                ww = (int) (ww * child.scale);
                hh = (int) (hh * child.scale);
            }

            if (ww > 0 && hh > 0)
            {
                Area area = this.calculate(-this.w / 2 + child.shiftX, -this.h / 2 + child.shiftY, -this.w / 2 + child.shiftX + ww, -this.h / 2 + child.shiftY + hh);
                boolean needsMultLinkShader = child.pixelate > 1 || child.erase;

                if (child == this.picker.currentFiltered)
                {
                    context.batcher.box(area.x, area.y, area.ex(), area.ey(), Colors.setA(Colors.RED, 0.25F));
                }

                /* TODO(1.21.11 render): the pixelate/erase "multilink" filter.
                 * The bbs:core/multilink GLSL is now migrated to #version 330 std140 and its pipeline
                 * (BBSShaders.getMultilinkProgram()/getMultilinkLayer()) declares the builtin
                 * DynamicTransforms/Projection UBOs + the custom MultilinkInfo UBO (Filters/Size) + the
                 * Sampler0/Sampler3 atlas samplers. What is STILL missing is the per-draw dispatch:
                 * texturedBox(Supplier,...) routes through the AdoptedTexture -> GUI_TEXTURED bridge
                 * (the Supplier/pipeline is IGNORED), so the pixelate/erase shader is NOT applied and the
                 * MultilinkInfo UBO is never uploaded. Reviving it needs a custom two-phase-GUI draw: a
                 * manual RenderPass binding getMultilinkProgram(), setUniform("MultilinkInfo", <Std140 slice
                 * of Filters/Size>), the Sampler0 texture + Sampler3 erase-atlas, rendered into an off-screen
                 * target composited via a SpecialGuiElementRenderer (cf. BbsFormGuiElementRenderer). Until
                 * then the child draws unfiltered. Tracked as the separate "revive GUI custom shaders" work. */
                final RenderPipeline finalProgram = needsMultLinkShader
                    ? BBSShaders.getMultilinkProgram()
                    : net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;

                context.batcher.texturedBox(() -> finalProgram, texture.id, child.color, area.x, area.y, area.w, area.h, 0, 0, texture.width, texture.height, texture.width, texture.height);
            }
        }
    }
}