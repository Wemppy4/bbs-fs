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
        NbtList sizeList = root.getList("size", NbtElement.INT_TYPE);
        Vec3i size = new Vec3i(sizeList.getInt(0), sizeList.getInt(1), sizeList.getInt(2));

        NbtList paletteNbt;

        if (root.contains("palette", NbtElement.LIST_TYPE))
        {
            paletteNbt = root.getList("palette", NbtElement.COMPOUND_TYPE);
        }
        else
        {
            /* "palettes" variant: several random palettes, the first one is good enough */
            NbtList palettes = root.getList("palettes", NbtElement.LIST_TYPE);

            paletteNbt = palettes.isEmpty() ? new NbtList() : palettes.getList(0);
        }

        BlockState[] palette = new BlockState[paletteNbt.size()];

        for (int i = 0; i < palette.length; i++)
        {
            palette[i] = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), paletteNbt.getCompound(i));
        }

        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Map<BlockPos, NbtCompound> blockEntities = new LinkedHashMap<>();
        NbtList blocksNbt = root.getList("blocks", NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < blocksNbt.size(); i++)
        {
            NbtCompound block = blocksNbt.getCompound(i);
            int stateIndex = block.getInt("state");

            if (stateIndex < 0 || stateIndex >= palette.length)
            {
                continue;
            }

            BlockState state = palette[stateIndex];

            if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID))
            {
                continue;
            }

            NbtList posList = block.getList("pos", NbtElement.INT_TYPE);
            BlockPos pos = new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2));

            blocks.put(pos, state);

            if (block.contains("nbt", NbtElement.COMPOUND_TYPE))
            {
                blockEntities.put(pos, block.getCompound("nbt"));
            }
        }

        return new StructureRenderData(id, size, blocks, blockEntities);
    }
}
