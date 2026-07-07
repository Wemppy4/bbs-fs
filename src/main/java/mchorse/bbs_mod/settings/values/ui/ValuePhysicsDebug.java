package mchorse.bbs_mod.settings.values.ui;

/**
 * The physics debug overlay's look, drawn by {@code ModelPhysicsDebug}. The
 * whole chain is shown by default; the wind element's size is the arrow length
 * per unit of force.
 */
public class ValuePhysicsDebug extends ValueModelDebug
{
    public final ValueDebugElement root = this.element("root", false, 0xffa82c, 0.45F, ValueDebugElement.SHAPE_RING);
    public final ValueDebugElement tip = this.element("tip", false, 0x4da3ff, 0.2F, ValueDebugElement.SHAPE_SPHERE);
    public final ValueDebugElement attach = this.element("attach", false, 0x38d68c, 0.12F, ValueDebugElement.SHAPE_DIAMOND);
    public final ValueDebugElement wind = this.line("wind", true, 0x8cf2ff, 2F, 0.1F, 5F);

    public ValuePhysicsDebug(String id)
    {
        super(id, false, 0xe6ebf2, 0.2F, true, 0xffffff, 0.12F);
    }
}
