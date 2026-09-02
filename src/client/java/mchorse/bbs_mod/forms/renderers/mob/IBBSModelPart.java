package mchorse.bbs_mod.forms.renderers.mob;

import net.minecraft.client.model.ModelPart;

import java.util.Map;

/**
 * What a vanilla {@link ModelPart} knows about its own place in the skeleton, published by
 * {@code ModelPartMixin}.
 *
 * <p>Vanilla parts are anonymous: the tree is built bottom-up by {@code ModelPartData.createPart},
 * which hands every parent a {@code Map<String, ModelPart>} of its children and then throws the
 * names away. The mixin catches them in the constructor, so every part in the game ends up knowing
 * its own name and its parent — the same stable strings {@code EntityModelPartNames} declares
 * ({@code head}, {@code left_arm}), NOT the Java field names the mob form used to scrape by
 * reflection. Field names are remapped to intermediary in a production jar, which is why a pose
 * authored in a dev environment used to be unreadable in a released one.</p>
 */
public interface IBBSModelPart
{
    /**
     * {@link ModelPart} is final, so it cannot be cast to this interface directly even though the
     * mixin makes every instance implement it — the hop through {@link Object} is what the compiler
     * needs, and doing it here keeps that noise out of every call site.
     */
    public static IBBSModelPart of(ModelPart part)
    {
        return (IBBSModelPart) (Object) part;
    }

    /** The name this part is addressed by within its parent, or null at the root of a tree. */
    public String bbs$getName();

    public void bbs$setName(String name);

    /** The part this one hangs off, or null at the root of a tree. */
    public ModelPart bbs$getParent();

    public void bbs$setParent(ModelPart parent);

    /** The part's children by name — vanilla's own map, republished rather than copied. */
    public Map<String, ModelPart> bbs$children();
}
