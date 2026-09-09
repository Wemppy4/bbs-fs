package mchorse.bbs_mod.forms.structure;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * How a structure form is lit, for both of the fake worlds a structure renders against:
 * {@link StructureRenderWorld} (block/fluid geometry) and {@link StructureWorld} (block
 * entities). They have no common supertype worth sharing — one is a {@code BlockRenderView},
 * the other a full {@code World} — but a structure that shaded its blocks one way and its
 * chests another would be a bug, so the answers live here rather than in each.
 *
 * <p>Sky light is constant: a structure is lit as if standing in open overworld daylight, with
 * vanilla directional face shade. Block light is traced for real — the structure's own emitters
 * (torches, glowstone, lanterns) flood their surroundings the way they would in the world, so a
 * torch lights the room around it instead of only itself. The form's own {@code lighting}
 * property and the world light around it are applied later, at replay, where the baked block
 * light survives as the floor ({@code BakedStructure.render}) and the baked sky light is
 * replaced outright.</p>
 *
 * <p>One instance per {@link StructureRenderData} — the trace depends on the blocks and nothing
 * else, so it outlives biome changes and rebakes; see {@link StructureRenderData#getLighting()}.</p>
 */
public class StructureLighting
{
    /** Brightest a light level goes, and thus how far an emitter can reach. */
    public static final int MAX_LEVEL = 15;

    private static final Direction[] DIRECTIONS = Direction.values();

    /** Vanilla overworld directional face shade. {@code shaded == false} means the caller wants none. */
    public static float getBrightness(Direction direction, boolean shaded)
    {
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

    /** Packed structure-local position → block light level. Only lit cells are in here. */
    private final Long2ByteMap blockLight;

    private StructureLighting(Long2ByteMap blockLight)
    {
        this.blockLight = blockLight;
    }

    /** Full sun overhead; block light is whatever the structure's own emitters flooded into this cell. */
    public int getLightLevel(LightType type, BlockPos pos)
    {
        return type == LightType.SKY ? MAX_LEVEL : this.blockLight.get(pos.asLong());
    }

    /**
     * Trace the structure's block light: seed every emitter with its own luminance, then flood
     * outwards. Nothing bounds the flood but the light level itself — a cell 15 steps from the
     * last emitter is dark — which is what lets light leave the structure, travel through the
     * air around it and come back in through an opening, exactly as it would in the world.
     */
    public static StructureLighting compute(StructureRenderData data)
    {
        Long2ByteMap levels = new Long2ByteOpenHashMap();
        LongList[] pending = new LongList[MAX_LEVEL + 1];

        for (Map.Entry<BlockPos, BlockState> entry : data.getBlocks().entrySet())
        {
            int luminance = entry.getValue().getLuminance();

            if (luminance <= 0)
            {
                continue;
            }

            long packed = entry.getKey().asLong();

            levels.put(packed, (byte) luminance);
            queue(pending, luminance).add(packed);
        }

        /* Structures with nothing that glows are the common case — they cost a scan and no more */
        if (!levels.isEmpty())
        {
            propagate(data, levels, pending);
        }

        return new StructureLighting(levels);
    }

    /**
     * Brightest level first, so a cell is expanded once: every level a cell can still be raised to
     * comes from a source at least as bright as the one being processed, and those are all behind
     * us. Stale queue entries (a cell that was queued dim and then reached brighter) are dropped by
     * comparing against the level it ended up with.
     */
    private static void propagate(StructureRenderData data, Long2ByteMap levels, LongList[] pending)
    {
        BlockView view = new LightView(data);
        BlockPos.Mutable source = new BlockPos.Mutable();
        BlockPos.Mutable target = new BlockPos.Mutable();

        /* Level 1 has nothing to give: a step costs at least 1 */
        for (int level = MAX_LEVEL; level > 1; level--)
        {
            LongList queue = pending[level];

            if (queue == null)
            {
                continue;
            }

            for (int i = 0; i < queue.size(); i++)
            {
                long packed = queue.getLong(i);

                if (levels.get(packed) != level)
                {
                    continue;
                }

                source.set(packed);

                BlockState sourceState = data.getBlockState(source);

                for (Direction direction : DIRECTIONS)
                {
                    target.set(source, direction);

                    long targetPacked = target.asLong();

                    if (levels.get(targetPacked) >= level - 1)
                    {
                        continue;
                    }

                    BlockState targetState = data.getBlockState(target);

                    /* Vanilla's own step cost: the target's opacity, or a full block's worth when
                     * the two touching faces cover the square between them (a slab floor stops
                     * light even though the slab itself is see-through) */
                    int opacity = ChunkLightProvider.getRealisticOpacity(
                        view, sourceState, source, targetState, target, direction, targetState.getOpacity(view, target));
                    int next = level - Math.max(1, opacity);

                    if (next > levels.get(targetPacked))
                    {
                        levels.put(targetPacked, (byte) next);

                        if (next > 1)
                        {
                            queue(pending, next).add(targetPacked);
                        }
                    }
                }
            }
        }
    }

    private static LongList queue(LongList[] pending, int level)
    {
        LongList list = pending[level];

        if (list == null)
        {
            list = new LongArrayList();
            pending[level] = list;
        }

        return list;
    }

    /**
     * The minimal {@link BlockView} the vanilla opacity and shape helpers ask for while light is
     * traced. Block entities have no say in opacity, and the height limit is stretched past the
     * structure by an emitter's reach on both ends, because the flood deliberately leaves the
     * structure's bounds.
     */
    private record LightView(StructureRenderData data) implements BlockView
    {
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
            return this.data.getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight()
        {
            return this.data.size.getY() + MAX_LEVEL * 2;
        }

        @Override
        public int getBottomY()
        {
            return -MAX_LEVEL;
        }
    }
}
