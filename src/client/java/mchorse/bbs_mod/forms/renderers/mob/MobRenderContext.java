package mchorse.bbs_mod.forms.renderers.mob;

import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.model.ModelPart;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * What the mob form is doing right now, for the mixins that sit inside vanilla's entity render.
 *
 * <p>The form hands its work to {@code EntityRenderDispatcher} and cannot pass anything down that
 * call, so the few things vanilla's insides need — which rig this is, which pose to add, and later
 * on where to write bone ids and matrices — are published here for the duration of the render and
 * taken back down straight after. The previous value is kept and restored rather than nulled, so a
 * mob form nested under another one leaves the outer one intact.</p>
 */
public class MobRenderContext
{
    private static MobRenderContext current;

    private final MobRig rig;
    private final Pose pose;
    private final Pose poseOverlay;
    private final Map<ModelPart, Transform> saved = new IdentityHashMap<>();

    private StencilMap stencilMap;
    private MobRenderContext previous;

    public static MobRenderContext current()
    {
        return current;
    }

    /** Arms the context. ALWAYS pair with {@link #pop()} in a finally. */
    public static MobRenderContext push(MobRig rig, Pose pose, Pose poseOverlay)
    {
        MobRenderContext context = new MobRenderContext(rig, pose, poseOverlay);

        context.previous = current;
        current = context;

        return context;
    }

    private MobRenderContext(MobRig rig, Pose pose, Pose poseOverlay)
    {
        this.rig = rig;
        this.pose = pose;
        this.poseOverlay = poseOverlay;
    }

    public MobRig rig()
    {
        return this.rig;
    }

    /** Arms the id pass: from here on every model part draws with its own id in the light channel. */
    public MobRenderContext picking(StencilMap stencilMap)
    {
        this.stencilMap = stencilMap;

        return this;
    }

    public boolean isPicking()
    {
        return this.stencilMap != null;
    }

    /**
     * What to put in the light channel of a part being drawn into the pick buffer. The picker
     * shader reads it as an OFFSET from the form's own id ({@code Target + texCoord2.x}), so 0 is
     * the form itself and a bone is its position in {@link MobRig#ordered()} plus one - the same
     * order {@code MobFormRenderer.updateStencilMap} registers names in.
     *
     * <p>A part that is not in the rig - armor, an elytra, a held item, a model built outside the
     * named-children path - keeps 0 and therefore picks as the whole form, which is what a click
     * on a mob's helmet should select anyway.</p>
     *
     * <p>Returns 0 wholesale when the pass is not incrementing: that is the Alt sweep across
     * replays, where every entity is one flat id and a per-bone offset would land on a NEIGHBOUR's
     * id (see {@code StencilMap.addPicking}, which does not advance objectIndex there).</p>
     */
    public int partLight(ModelPart part)
    {
        if (this.rig == null || this.stencilMap == null || !this.stencilMap.increment)
        {
            return 0;
        }

        int index = this.rig.index(part);

        return index < 0 ? 0 : index + 1;
    }

    /**
     * Adds the form's pose stack onto the angles vanilla just computed, remembering what it
     * overwrote. Called from inside the entity render, right after {@code setAngles}.
     */
    public void applyPose()
    {
        if (this.rig == null || this.pose == null)
        {
            return;
        }

        MobPoseApplier.apply(this.rig, MobPoseApplier.merge(this.pose, this.poseOverlay), this.saved);
    }

    /**
     * Puts the model's own transforms back. Called at the end of the entity render, and again by
     * {@link #pop()} — the second call is the net under an exception thrown mid-render, which
     * would otherwise leave the posed parts in the world's real entities.
     */
    public void restorePose()
    {
        MobPoseApplier.restore(this.saved);
    }

    public void pop()
    {
        this.restorePose();

        current = this.previous;
        this.previous = null;
    }
}
