package mchorse.bbs_mod.graphics.line;

import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.ui.framework.elements.utils.UIVertexBuffer;
import org.joml.Matrix4f;

public class SolidColorLineRenderer implements ILineRenderer
{
    private static SolidColorLineRenderer INSTANCE = new SolidColorLineRenderer();

    private Color color = new Color();

    public static ILineRenderer get(float r, float g, float b, float a)
    {
        return INSTANCE.setColor(r, g, b, a);
    }

    public static ILineRenderer get(Color color)
    {
        return INSTANCE.setColor(color);
    }

    public SolidColorLineRenderer setColor(float r, float g, float b, float a)
    {
        this.color.set(r, g, b, a);

        return this;
    }

    public SolidColorLineRenderer setColor(Color color)
    {
        this.color.copy(color);

        return this;
    }

    @Override
    public void render(UIVertexBuffer builder, Matrix4f matrix, LinePoint point)
    {
        builder.vertex(matrix, point.x, point.y, 0F).color(this.color.r, this.color.g, this.color.b, this.color.a).next();
    }
}