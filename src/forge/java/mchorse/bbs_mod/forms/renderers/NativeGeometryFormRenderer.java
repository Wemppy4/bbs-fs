package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.joml.Matrix4f;

/** Shared native preview boundary. Geometry still goes through the ordinary form lifecycle. */
public abstract class NativeGeometryFormRenderer<T extends Form> extends FormRenderer<T>
{
    private final StubEntity preview = new StubEntity();

    protected NativeGeometryFormRenderer(T form) { super(form); }

    @Override protected void renderInUI(UIContext ui, int x1, int y1, int x2, int y2)
    {
        ui.batcher.flush();
        MatrixStack stack = new MatrixStack();
        stack.peek().getPositionMatrix().set(NativeTextureRenderer.currentMatrix())
            .mul(ui.batcher.getContext().getMatrices().peek().getPositionMatrix())
            .mul(this.getPreviewMatrix(ui, x1, y1, x2, y2));
        stack.peek().getNormalMatrix().set(stack.peek().getPositionMatrix()).invert().transpose();
        /* Original form thumbnails keep lighting in the model frame despite the
         * negative GUI Y scale (the same correction used by ModelFormRenderer). */
        if(!(this.form instanceof mchorse.bbs_mod.forms.forms.BillboardForm))
        {
            org.joml.Vector3f normalScale = stack.peek().getNormalMatrix().getScale(new org.joml.Vector3f());
            stack.peek().getNormalMatrix().scale(1F / normalScale.x, -1F / normalScale.y, 1F / normalScale.z);
        }
        this.preview.setWorld(net.minecraft.client.Minecraft.getMinecraft().world);
        this.preview.setForm(this.form);
        try (NativeFormDraw.State state = new NativeFormDraw.State();
            mchorse.bbs_mod.graphics.render.RenderSystem.LightScope lighting = mchorse.bbs_mod.graphics.render.RenderSystem.guiLighting())
        {
            net.minecraft.client.renderer.GlStateManager.enableDepth();
            net.minecraft.client.renderer.GlStateManager.depthMask(true);
            net.minecraft.client.renderer.GlStateManager.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            mchorse.bbs_mod.forms.FormUtilsClient.render(this.form, new FormRenderingContext()
                .set(FormRenderType.ENTITY, this.preview, stack, 0x00f000f0, 10 << 16, ui.getTransition()).inUI());
        }
    }
}
