package mchorse.bbs_mod.forms.renderers.utils;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;

/**
 * A world of exactly one block, sitting at {@link BlockPos#ORIGIN} with air all around it.
 *
 * <p>Fluids are the one thing vanilla never bakes into a block model: a water or lava state
 * renders as {@link net.minecraft.block.BlockRenderType#INVISIBLE}, and its geometry gets
 * generated per chunk section by {@link net.minecraft.client.render.block.FluidRenderer},
 * which needs a world to ask about neighbours, light and the biome tint. A form has no world,
 * so it hands the fluid renderer this one: air on every side (so every face draws), the
 * form's own packed light, and the biome tint read from wherever the camera stands.</p>
 *
 * <p>The origin is not arbitrary — the fluid renderer emits vertices at
 * {@code pos.getX() & 15}, chunk local coordinates, so only a position at a section corner
 * puts the block into 0..1.</p>
 */
public class SingleBlockRenderView implements BlockRenderView
{
    /** Water tint of the plains biome — the fallback when there is no world to sample. */
    private static final int DEFAULT_TINT = 0x3F76E4;

    private BlockState state = Blocks.AIR.getDefaultState();
    private FluidState fluidState = Fluids.EMPTY.getDefaultState();
    private int light;

    public SingleBlockRenderView set(BlockState state, int light)
    {
        this.state = state;
        this.fluidState = state.getFluidState();
        this.light = light;

        return this;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        return pos.equals(BlockPos.ORIGIN) ? this.state : Blocks.AIR.getDefaultState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return pos.equals(BlockPos.ORIGIN) ? this.fluidState : Fluids.EMPTY.getDefaultState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        return null;
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded)
    {
        if (!shaded)
        {
            return 1F;
        }

        return switch (direction)
        {
            case DOWN -> 0.5F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
            default -> 1F;
        };
    }

    @Override
    public LightingProvider getLightingProvider()
    {
        /* Never reached: both light lookups below are overridden, and they are the only
         * things that would go through a lighting provider. */
        return null;
    }

    @Override
    public int getLightLevel(LightType type, BlockPos pos)
    {
        return type == LightType.SKY ? (this.light >> 20) & 0xF : (this.light >> 4) & 0xF;
    }

    @Override
    public int getBaseLightLevel(BlockPos pos, int ambientDarkness)
    {
        return Math.max(this.getLightLevel(LightType.SKY, pos) - ambientDarkness, this.getLightLevel(LightType.BLOCK, pos));
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver colorResolver)
    {
        ClientWorld world = MinecraftClient.getInstance().world;

        if (world != null)
        {
            /* The form has no place in the world of its own — biome tint comes from where
             * the camera is, so a water form matches the water it stands next to. */
            return world.getColor(BlockPos.ofFloored(MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos()), colorResolver);
        }

        return DEFAULT_TINT;
    }

    @Override
    public int getHeight()
    {
        return 384;
    }

    @Override
    public int getBottomY()
    {
        return -64;
    }
}
