package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.renderers.VanillaBoneHierarchy;
import mchorse.bbs_mod.forms.renderers.VanillaRendererBones;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModelLoader;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityModelLoader.class)
public class EntityModelLoaderMixin
{
    @Inject(method = "getModelPart", at = @At("RETURN"))
    private void bbs$registerBoneHierarchy(EntityModelLayer layer, CallbackInfoReturnable<ModelPart> info)
    {
        VanillaBoneHierarchy.register(layer, info.getReturnValue());
    }

    @Inject(method = "reload", at = @At("HEAD"))
    private void bbs$clearBoneHierarchies(ResourceManager manager, CallbackInfo info)
    {
        VanillaRendererBones.clear();
        VanillaBoneHierarchy.clear();
    }
}
