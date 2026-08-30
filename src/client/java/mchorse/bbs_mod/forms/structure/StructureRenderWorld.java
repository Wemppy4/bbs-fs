package mchorse.bbs_mod.forms.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.LightSourceView;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Virtual world that feeds vanilla block/fluid renderers from a {@link StructureRenderData}:
 * neighbor lookups make smooth AO work, {@link #getColor} resolves grass/foliage/water tint
 * against a SELECTED biome (the form's "biome" property) instead of a real-world position.
 *
 * <p>Light is constant: full skylight, no block light propagation.</p>
 */
public class StructureRenderWorld implements BlockRenderView
{
    private final StructureRenderData data;
    private final Biome biome;
    private final LightingProvider lighting;

    public StructureRenderWorld(StructureRenderData data, String biomeId)
    {
        this.data = data;
        this.biome = resolveBiome(biomeId);

        /* Never queried for actual light (getLightLevel is overridden), but BlockRenderView
         * requires a non-null provider for default methods */
        this.lighting = new LightingProvider(new ChunkProvider()
        {
            @Nullable
            @Override
            public LightSourceView getChunk(int chunkX, int chunkZ)
            {
                return null;
            }

            @Override
            public net.minecraft.world.BlockView getWorld()
            {
                return StructureRenderWorld.this;
            }
        }, false, false);
    }

    private static Biome resolveBiome(String id)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null)
        {
            return null;
        }

        Registry<Biome> registry = mc.world.getRegistryManager().get(RegistryKeys.BIOME);
        Identifier identifier = Identifier.tryParse(id == null ? "" : id);
        Biome biome = identifier == null ? null : registry.get(identifier);

        if (biome == null)
        {
            biome = registry.get(BiomeKeys.PLAINS);
        }

        if (biome == null)
        {
            for (Biome b : registry)
            {
                return b;
            }
        }

        return biome;
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded)
    {
        /* Vanilla overworld directional shade */
        if (!shaded)
        {
            return 1F;
        }

        switch (direction)
        {
            case DOWN: return 0.5F;
            case UP: return 1F;
            case NORTH:
            case SOUTH: return 0.8F;
            default: return 0.6F;
        }
    }

    @Override
    public LightingProvider getLightingProvider()
    {
        return this.lighting;
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver colorResolver)
    {
        if (this.biome == null)
        {
            return -1;
        }

        return colorResolver.getColor(this.biome, pos.getX(), pos.getZ());
    }

    @Override
    public int getLightLevel(LightType type, BlockPos pos)
    {
        return type == LightType.SKY ? 15 : 0;
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        return this.data.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return this.getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight()
    {
        return Math.max(this.data.size.getY(), 16);
    }

    @Override
    public int getBottomY()
    {
        return 0;
    }
}
