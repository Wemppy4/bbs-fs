package mchorse.bbs_mod.mixin.client.iris;

import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(CustomUniforms.class)
public interface CustomUniformsAccessor
{
    @Accessor(value = "uniformOrder", remap = false)
    List<CachedUniform> bbs$uniformOrder();
}
