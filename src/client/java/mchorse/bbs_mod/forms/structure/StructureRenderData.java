package mchorse.bbs_mod.forms.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed structure NBT (the vanilla structure block format): size + non-air blocks. Parsing is
 * done by hand instead of {@code StructureTemplate.place()} because placing requires a
 * {@code ServerWorldAccess} while we only need block states for client-side rendering.
 */
public class StructureRenderData
{
    public final String id;
    public final Vec3i size;

    /** Structure-local position → state, insertion order = file order. Air and structure void excluded. */
    private final Map<BlockPos, BlockState> blocks;

    /** Structure-local position → block entity NBT (chests, signs, beds, ...). */
    private final Map<BlockPos, NbtCompound> blockEntities;

    private StructureRenderData(String id, Vec3i size, Map<BlockPos, BlockState> blocks, Map<BlockPos, NbtCompound> blockEntities)
    {
        this.id = id;
        this.size = size;
        this.blocks = Collections.unmodifiableMap(blocks);
        this.blockEntities = Collections.unmodifiableMap(blockEntities);
    }

    public Map<BlockPos, BlockState> getBlocks()
    {
        return this.blocks;
    }

    public Map<BlockPos, NbtCompound> getBlockEntities()
    {
        return this.blockEntities;
    }

    public BlockState getBlockState(BlockPos pos)
    {
        BlockState state = this.blocks.get(pos);

        return state == null ? Blocks.AIR.getDefaultState() : state;
    }

    public boolean isEmpty()
    {
        return this.blocks.isEmpty();
    }

    public static StructureRenderData parse(String id, NbtCompound root)
    {
        /* 1.21.11 rebuilt the NBT getters: getList/getCompound/getInt take no element type any more
         * and return an Optional, each with a plain twin (getListOrEmpty / getCompoundOrEmpty /
         * getInt(key, default)). The type argument used to be the guard against a wrong-typed tag;
         * the empty/default value is that guard now, so the reads below keep their old outcome on a
         * malformed structure file. */
        NbtList sizeList = root.getListOrEmpty("size");
        Vec3i size = new Vec3i(sizeList.getInt(0, 0), sizeList.getInt(1, 0), sizeList.getInt(2, 0));

        NbtList paletteNbt;

        if (root.contains("palette"))
        {
            paletteNbt = root.getListOrEmpty("palette");
        }
        else
        {
            /* "palettes" variant: several random palettes, the first one is good enough */
            NbtList palettes = root.getListOrEmpty("palettes");

            paletteNbt = palettes.isEmpty() ? new NbtList() : palettes.getListOrEmpty(0);
        }

        BlockState[] palette = new BlockState[paletteNbt.size()];

        for (int i = 0; i < palette.length; i++)
        {
            /* Registry implements RegistryEntryLookup itself now — getReadOnlyWrapper() is gone. */
            palette[i] = NbtHelper.toBlockState(Registries.BLOCK, paletteNbt.getCompoundOrEmpty(i));
        }

        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Map<BlockPos, NbtCompound> blockEntities = new LinkedHashMap<>();
        NbtList blocksNbt = root.getListOrEmpty("blocks");

        for (int i = 0; i < blocksNbt.size(); i++)
        {
            NbtCompound block = blocksNbt.getCompoundOrEmpty(i);
            int stateIndex = block.getInt("state", 0);

            if (stateIndex < 0 || stateIndex >= palette.length)
            {
                continue;
            }

            BlockState state = palette[stateIndex];

            if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID))
            {
                continue;
            }

            NbtList posList = block.getListOrEmpty("pos");
            BlockPos pos = new BlockPos(posList.getInt(0, 0), posList.getInt(1, 0), posList.getInt(2, 0));

            blocks.put(pos, state);

            block.getCompound("nbt").ifPresent((nbt) -> blockEntities.put(pos, nbt));
        }

        return new StructureRenderData(id, size, blocks, blockEntities);
    }
}
