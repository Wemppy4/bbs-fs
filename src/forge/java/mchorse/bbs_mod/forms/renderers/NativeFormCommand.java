package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.GlStateManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A finished native draw: immutable transform/material, no form state or animation re-evaluation. */
public class NativeFormCommand extends FormTranslucentQueue.DrawCommand
{
    private final FormRenderingContext context;
    private final Color tint,overlay;
    private final boolean vertexLight;
    private final Runnable geometry;
    public NativeFormCommand(FormRenderingContext source,Color tint,Color overlay,boolean vertexLight,boolean cull,boolean depthWrite,boolean flat,Runnable geometry)
    {
        super(source.stack.peek().getPositionMatrix().getTranslation(new Vector3f()),flat?normal(source):null,cull,depthWrite);
        this.context=new FormRenderingContext().set(source.type,source.entity,new MatrixStack(),source.light,source.overlay,source.transition);
        this.context.ui=source.ui;this.context.color=source.color;this.context.camera.copy(source.camera);
        this.matrix(source.stack.peek().getPositionMatrix());
        this.tint=tint.copy();this.overlay=overlay.copy();this.vertexLight=vertexLight;this.geometry=geometry;
    }
    private static Vector3f normal(FormRenderingContext context)
    {
        Matrix4f m=context.stack.peek().getPositionMatrix();return new Vector3f(m.m00(),m.m01(),m.m02()).cross(m.m10(),m.m11(),m.m12());
    }
    public NativeFormCommand matrix(Matrix4f matrix)
    {
        this.context.stack.peek().getPositionMatrix().set(matrix);
        this.context.stack.peek().getNormalMatrix().set(matrix).invert().transpose();return this;
    }
    @Override public void draw()
    {
        try(NativeFormDraw draw=new NativeFormDraw(context,tint,overlay,vertexLight))
        {
            GlStateManager.depthMask(depthWrite);if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
            geometry.run();
        }
    }
}
