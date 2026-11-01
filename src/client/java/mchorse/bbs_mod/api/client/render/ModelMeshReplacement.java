package mchorse.bbs_mod.api.client.render;

import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;

import java.util.List;

/**
 * What of one cubic model form an addon draws itself on this draw: the cubes BBS leaves out of its
 * own drawing, and the meshes drawn with their bones instead. Handed out by
 * {@link mchorse.bbs_mod.api.client.events.ModelMeshEvents#REPLACE}.
 *
 * <p>The model's baked geometry is shared by every form using the model, so nothing is taken out of
 * the bake: BBS skips the hidden cubes as it draws this form, and draws every other cube the way it
 * always does.</p>
 */
public interface ModelMeshReplacement
{
    /** Whether BBS leaves {@code cube} of {@code group} out of its own drawing of this form. */
    boolean hides(ModelGroup group, ModelCube cube);

    /** The meshes drawn with {@code group} — its colour, light, materials and picking — in the model's root frame; empty for none. */
    List<DeformedMesh> meshes(ModelGroup group);

    /** Changes whenever {@link #hides} would answer differently: it keys the cached geometry BBS bakes on the CPU. */
    long key();
}
