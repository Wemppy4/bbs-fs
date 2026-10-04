package mchorse.bbs_mod.graphics.line;

import mchorse.bbs_mod.ui.framework.elements.utils.UIVertexBuffer;
import org.joml.Matrix4f;

public interface ILineRenderer <T>
{
    public void render(UIVertexBuffer builder, Matrix4f matrix, LinePoint<T> point);
}