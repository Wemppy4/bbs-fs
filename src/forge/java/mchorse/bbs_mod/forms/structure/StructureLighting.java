package mchorse.bbs_mod.forms.structure;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import java.util.Map;

/** Original brightest-first structure light flood, using Forge block light/opacity hooks. */
public class StructureLighting
{
    public static final int MAX_LEVEL = 15;
    private static final EnumFacing[] DIRECTIONS = EnumFacing.values();
    private final Long2ByteMap blockLight;

    private StructureLighting(Long2ByteMap blockLight) { this.blockLight = blockLight; }

    public static float getBrightness(EnumFacing direction, boolean shaded)
    {
        if (!shaded) return 1F;
        switch (direction)
        {
            case DOWN: return 0.5F;
            case UP: return 1F;
            case NORTH: case SOUTH: return 0.8F;
            default: return 0.6F;
        }
    }

    public int getLightLevel(EnumSkyBlock type, BlockPos pos)
    {
        return type == EnumSkyBlock.SKY ? MAX_LEVEL : this.blockLight.get(pos.toLong());
    }

    public static StructureLighting compute(StructureRenderData data)
    {
        Long2ByteMap levels = new Long2ByteOpenHashMap();
        LongList[] pending = new LongList[MAX_LEVEL + 1];
        IBlockAccess view = new LightView(data);
        for (Map.Entry<BlockPos, IBlockState> entry : data.getBlocks().entrySet())
        {
            int luminance = Math.max(0, Math.min(MAX_LEVEL, entry.getValue().getLightValue(view, entry.getKey())));
            if (luminance == 0) continue;
            long packed = entry.getKey().toLong();
            levels.put(packed, (byte) luminance);
            queue(pending, luminance).add(packed);
        }
        for (int level = MAX_LEVEL; level > 1; level--)
        {
            LongList queue = pending[level];
            if (queue == null) continue;
            for (int i = 0; i < queue.size(); i++)
            {
                long packed = queue.getLong(i);
                if (levels.get(packed) != level) continue;
                BlockPos source = BlockPos.fromLong(packed);
                for (EnumFacing direction : DIRECTIONS)
                {
                    BlockPos target = source.offset(direction);
                    long targetPacked = target.toLong();
                    if (levels.get(targetPacked) >= level - 1) continue;
                    IBlockState state = data.getBlockState(target);
                    int opacity = state.getLightOpacity(view, target);
                    // Matches World#getRawLight in 1.12, including opaque emitters.
                    if (opacity >= MAX_LEVEL && state.getLightValue(view, target) > 0) opacity = 1;
                    int next = level - Math.max(1, opacity);
                    if (next > levels.get(targetPacked))
                    {
                        levels.put(targetPacked, (byte) next);
                        if (next > 1) queue(pending, next).add(targetPacked);
                    }
                }
            }
        }
        return new StructureLighting(levels);
    }

    private static LongList queue(LongList[] pending, int level)
    {
        if (pending[level] == null) pending[level] = new LongArrayList();
        return pending[level];
    }

    private static class LightView implements IBlockAccess
    {
        private final StructureRenderData data;
        LightView(StructureRenderData data) { this.data = data; }
        public TileEntity getTileEntity(BlockPos pos) { return null; }
        public IBlockState getBlockState(BlockPos pos) { return this.data.getBlockState(pos); }
        public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getMaterial() == Material.AIR; }
        public int getCombinedLight(BlockPos pos, int light) { return 15 << 20 | light << 4; }
        public Biome getBiome(BlockPos pos) { return Biomes.PLAINS; }
        public int getStrongPower(BlockPos pos, EnumFacing direction) { return getBlockState(pos).getStrongPower(this, pos, direction); }
        public WorldType getWorldType() { return WorldType.DEFAULT; }
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean fallback) { return getBlockState(pos).isSideSolid(this, pos, side); }
    }
}
