package mchorse.bbs_mod.mixin.client.sodium;

import mchorse.bbs_mod.client.TerrainCut;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Switches the orbit's wall cut on for each terrain pass, once its program is bound, and off
 * when the pass is drawn (see {@link TerrainCut}). Shadow passes draw with programs that have
 * no cut, so they are left alone.
 */
@Mixin(DefaultChunkRenderer.class)
public class DefaultChunkRendererMixin
{
    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/ShaderChunkRenderer;begin(Lme/jellysquid/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V", shift = At.Shift.AFTER),
        remap = false
    )
    private void onBegin(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, CallbackInfo info)
    {
        TerrainCut.begin(matrices.projection(), matrices.modelView(), camera.x, camera.y, camera.z);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void onEnd(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, CallbackInfo info)
    {
        TerrainCut.end();
    }
}
