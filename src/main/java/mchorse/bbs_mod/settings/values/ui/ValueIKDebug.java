package mchorse.bbs_mod.settings.values.ui;

/**
 * The IK debug overlay's look, drawn by {@code ModelIKDebug}. The default is
 * minimal — only the goal and the pole marker, the chain wires, joints and the
 * effector left off — so the overlay reads as clean relationship handles.
 */
public class ValueIKDebug extends ValueModelDebug
{
    public final ValueDebugElement tip = this.element("tip", false, 0x4da3ff, 0.1F, ValueDebugElement.SHAPE_CROSS);
    public final ValueDebugElement target = this.element("target", 0xffffff, 0.15F, ValueDebugElement.SHAPE_DIAMOND);
    public final ValueDebugElement pole = this.element("pole", 0xff8c26, 0.05F, ValueDebugElement.SHAPE_CUBE);

    public ValueIKDebug(String id)
    {
        super(id, false, 0xe6ebf2, 0.05F, false, 0xe6ebf2, 0.07F);
    }
}
