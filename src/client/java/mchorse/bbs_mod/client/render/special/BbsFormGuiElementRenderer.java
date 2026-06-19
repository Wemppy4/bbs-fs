package mchorse.bbs_mod.client.render.special;

import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Renders a BBS form preview into the special-element off-screen FBO that the base
 * {@link SpecialGuiElementRenderer} binds; the deferred GUI then composites it into the form-list cell.
 *
 * <p>PHASE 1 (current): {@code render} only logs, to confirm the mixin-registered renderer is invoked per
 * submitted cell (i.e. that the closed special-element registry was widened, {@code addSpecialElement}
 * routes here, and the base calls us). No vertex drawing yet — so nothing composites; validation is via the
 * log. PHASE 2 will draw the form's model here: override the base's orthographic projection with the BBS
 * perspective, build the camera-baked per-vertex MatrixStack, flip {@code ModelPreviewRenderer.ACTIVE} so
 * {@code ModelInstance.render} takes the proven {@code entityCutoutNoCull} immediate branch into the bound
 * FBO, then restore.</p>
 */
public class BbsFormGuiElementRenderer extends SpecialGuiElementRenderer<BbsFormGuiElementRenderState>
{
    private static int counter;

    public BbsFormGuiElementRenderer(Immediate vertexConsumers)
    {
        super(vertexConsumers);
    }

    @Override
    public Class<BbsFormGuiElementRenderState> getElementClass()
    {
        return BbsFormGuiElementRenderState.class;
    }

    @Override
    protected void render(BbsFormGuiElementRenderState state, MatrixStack matrices)
    {
        /* PHASE 1 diagnostic (strip in Phase 2): proves register + addSpecialElement routing + base call. */
        if (counter++ % 120 == 0)
        {
            System.out.println("[BBS list preview] render fired (" + counter + ") rect=" + state.x1() + "," + state.y1()
                + " " + (state.x2() - state.x1()) + "x" + (state.y2() - state.y1()));
        }
    }

    @Override
    protected String getName()
    {
        return "bbs form";
    }
}
