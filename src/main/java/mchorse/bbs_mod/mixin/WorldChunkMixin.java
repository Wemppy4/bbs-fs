package mchorse.bbs_mod.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import mchorse.bbs_mod.BBSMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public class WorldChunkMixin
{
    /* Every World.setBlockState overload funnels into this single, non-overloaded chunk method, so it is the
     * one place that catches all block changes - including World.breakBlock, which calls the four-argument
     * setBlockState directly. Targeting a method that is not overloaded also sidesteps Sinytra Connector's
     * overload mis-resolution under NeoForge.
     *
     * The replaced block entity is serialized to NBT at HEAD, before the block is replaced: onStateReplaced
     * (run later in this method) drops container contents into the world and clears the block entity, so
     * serializing any later would capture empty data. The replaced block state, on the other hand, is read
     * from the return value rather than from getBlockState - by the time the change is recorded the chunk
     * already holds the new state, and on Connector the injection even lands after the section write. */
    private static final String SET_BLOCK_STATE = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;";

    @Inject(method = SET_BLOCK_STATE, at = @At("HEAD"))
    private void captureReplacedBlockEntity(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> info, @Share("replaced") LocalRef<NbtCompound> replaced)
    {
        WorldChunk chunk = (WorldChunk) (Object) this;

        if (chunk.getWorld() instanceof ServerWorld world)
        {
            BlockEntity blockEntity = chunk.getBlockEntity(pos);

            if (blockEntity != null)
            {
                replaced.set(blockEntity.createNbtWithIdentifyingData(world.getRegistryManager()));
            }
        }
    }

    @Inject(method = SET_BLOCK_STATE, at = @At("RETURN"))
    private void recordChangedBlock(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> info, @Share("replaced") LocalRef<NbtCompound> replaced)
    {
        BlockState previous = info.getReturnValue();
        WorldChunk chunk = (WorldChunk) (Object) this;

        if (previous != null && chunk.getWorld() instanceof ServerWorld)
        {
            BBSMod.getActions().changedBlock(pos, previous, replaced.get());
        }
    }
}
