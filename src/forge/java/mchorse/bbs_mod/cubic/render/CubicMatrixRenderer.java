package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.graphics.render.BufferBuilder;
import mchorse.bbs_mod.graphics.MatrixStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class CubicMatrixRenderer implements ICubicRenderer
{
    public List<Matrix4f> matrices;
    public List<Matrix4f> origins;

    public CubicMatrixRenderer(Model model)
    {
        this.matrices = new ArrayList<>();
        this.origins = new ArrayList<>();

        for (int i = 0; i < model.getAllGroupKeys().size(); i++)
        {
            this.matrices.add(new Matrix4f());
            this.origins.add(new Matrix4f());
        }
    }

    /**
     * The default sequence, split open only to snapshot the origin frame between
     * the translate and the pivot move. It must stay in lockstep with {@link
     * ICubicRenderer#applyGroupTransformations} — the offset leads there too, so
     * anything riding these matrices (gizmos, body parts, anchors, trackers,
     * shadows) follows a stretched bone instead of its un-stretched pose.
     */
    @Override
    public void applyGroupTransformations(MatrixStack stack, ModelGroup group)
    {
        ICubicRenderer.offsetGroup(stack, group);
        ICubicRenderer.translateGroup(stack, group);

        this.origins.get(group.index).set(stack.peek().getPositionMatrix());

        ICubicRenderer.moveToGroupPivot(stack, group);
        ICubicRenderer.rotateGroup(stack, group);
        ICubicRenderer.scaleGroup(stack, group);
        ICubicRenderer.moveBackFromGroupPivot(stack, group);
    }

    @Override
    public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        this.matrices.get(group.index).set(stack.peek().getPositionMatrix());

        return false;
    }
}