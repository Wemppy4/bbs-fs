package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.renderers.mob.IBBSModelPart;
import mchorse.bbs_mod.forms.renderers.mob.MobRenderContext;
import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements IBBSModelPart
{
    @Shadow @Final private Map<String, ModelPart> children;

    @Unique
    private String bbs$name;

    @Unique
    private ModelPart bbs$parent;

    @Override
    public String bbs$getName()
    {
        return this.bbs$name;
    }

    @Override
    public void bbs$setName(String name)
    {
        this.bbs$name = name;
    }

    @Override
    public ModelPart bbs$getParent()
    {
        return this.bbs$parent;
    }

    @Override
    public void bbs$setParent(ModelPart parent)
    {
        this.bbs$parent = parent;
    }

    @Override
    public Map<String, ModelPart> bbs$children()
    {
        return this.children;
    }

    /**
     * The bone id a mob form's part draws with while the pick buffer is being filled.
     *
     * <p>The picker shader reads the light channel as an offset from the form's own id, so
     * overriding the argument here is what turns one flat mob silhouette into a per-limb id map.
     * It has to be the argument rather than the cuboid draw: vanilla passes the same {@code light}
     * down to the children, so every part inherits its parent's id until its own hook replaces it
     * - which is exactly right for a sub-part the rig does not know about.</p>
     *
     * <p>This runs for every model part in the game, so the guard is one static field read and
     * nothing else.</p>
     */
    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int bbs$pickingLight(int light)
    {
        MobRenderContext context = MobRenderContext.current();

        return context == null || !context.isPicking() ? light : context.partLight((ModelPart) (Object) this);
    }

    /**
     * Names and links the whole tree, one part at a time. The tree is assembled bottom-up, so by
     * the time any part is constructed its children already exist and their names are right here
     * in the constructor's own argument — no traversal, no reflection, and it costs one pass over
     * a handful of entries per part, once per resource reload.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbs$nameChildren(List<ModelPart.Cuboid> cuboids, Map<String, ModelPart> children, CallbackInfo info)
    {
        for (Map.Entry<String, ModelPart> entry : children.entrySet())
        {
            IBBSModelPart child = (IBBSModelPart) (Object) entry.getValue();

            child.bbs$setName(entry.getKey());
            child.bbs$setParent((ModelPart) (Object) this);
        }
    }
}
