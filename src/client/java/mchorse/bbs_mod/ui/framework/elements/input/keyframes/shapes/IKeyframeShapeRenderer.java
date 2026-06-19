package mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3x2fc;

public interface IKeyframeShapeRenderer
{
    public IKey getLabel();

    public Icon getIcon();

    public void renderKeyframe(UIContext uiContext, VertexConsumer builder, Matrix3x2fc matrix4f, int x, int y, int offset, int c);

    public default void renderKeyframeBackground(UIContext uiContext, VertexConsumer builder, Matrix3x2fc matrix4f, int x, int y, int offset, int c)
    {}
}