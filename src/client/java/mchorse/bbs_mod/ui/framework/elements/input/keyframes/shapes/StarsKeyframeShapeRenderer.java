package mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3x2fc;

public class StarsKeyframeShapeRenderer implements IKeyframeShapeRenderer
{
    private final int numBranches;
    private final Icon icon;
    private final IKey label;

    public StarsKeyframeShapeRenderer(int numBranches, Icon icon, IKey label)
    {
        this.numBranches = numBranches;
        this.icon = icon;
        this.label = label;
    }

    @Override
    public IKey getLabel()
    {
        return this.label;
    }

    @Override
    public Icon getIcon()
    {
        return this.icon;
    }

    @Override
    public void renderKeyframe(UIContext uiContext, VertexConsumer builder, Matrix3x2fc matrix, int x, int y, int offset, int c)
    {
        float fOffset = offset * 2F;
        float baseWidth = fOffset * 0.5F;
        float tipWidth = fOffset * 0.16F;

        for (int i = 0; i < this.numBranches; i++)
        {
            float angle = -90F + (360F / this.numBranches) * i;

            float angle1 = (float) Math.toRadians(angle);
            float cos = (float) Math.cos(angle1);
            float sin = (float) Math.sin(angle1);

            float baseLeft_x = (float) x + 0F * cos - baseWidth * sin;
            float baseLeft_y = (float) y + 0F * sin + baseWidth * cos;
            float baseRight_x = (float) x + 0F * cos + baseWidth * sin;
            float baseRight_y = (float) y + 0F * sin - baseWidth * cos;

            float tipLeft_x = (float) x + fOffset * cos - tipWidth * sin;
            float tipLeft_y = (float) y + fOffset * sin + tipWidth * cos;
            float tipRight_x = (float) x + fOffset * cos + tipWidth * sin;
            float tipRight_y = (float) y + fOffset * sin - tipWidth * cos;

            builder.vertex(matrix, baseLeft_x, baseLeft_y).color(c);
            builder.vertex(matrix, tipLeft_x, tipLeft_y).color(c);
            builder.vertex(matrix, tipRight_x, tipRight_y).color(c);
            builder.vertex(matrix, baseRight_x, baseRight_y).color(c);
        }
    }

    @Override
    public void renderKeyframeBackground(UIContext uiContext, VertexConsumer builder, Matrix3x2fc matrix, int x, int y, int offset, int c)
    {
        float centerSize = offset * 0.2F;
        float half = centerSize * 1.25F;

        builder.vertex(matrix, x - half, y - half).color(c);
        builder.vertex(matrix, x - half, y + half).color(c);
        builder.vertex(matrix, x + half, y + half).color(c);
        builder.vertex(matrix, x + half, y - half).color(c);
    }
}
