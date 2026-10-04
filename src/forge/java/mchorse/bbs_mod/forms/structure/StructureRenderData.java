package mchorse.bbs_mod.forms.structure;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.NBTTagList;
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
    private final Map<BlockPos, IBlockState> blocks;

    /** Structure-local position → block entity NBT (chests, signs, beds, ...). */
    private final Map<BlockPos, NBTTagCompound> blockEntities;

    /** Traced on first use: light depends on the blocks alone, so it outlives biome changes and rebakes. */
    private StructureLighting lighting;

    private StructureRenderData(String id, Vec3i size, Map<BlockPos, IBlockState> blocks, Map<BlockPos, NBTTagCompound> blockEntities)
    {
        this.id = id;
        this.size = size;
        this.blocks = Collections.unmodifiableMap(blocks);
        this.blockEntities = Collections.unmodifiableMap(blockEntities);
    }

    public static StructureRenderData create(String id, Vec3i size, Map<BlockPos, IBlockState> blocks, Map<BlockPos, NBTTagCompound> entities)
    {
        Map<BlockPos, IBlockState> blockCopy = new LinkedHashMap<>();
        Map<BlockPos, NBTTagCompound> entityCopy = new LinkedHashMap<>();
        blocks.forEach((pos, state) -> blockCopy.put(pos.toImmutable(), state));
        entities.forEach((pos, nbt) -> entityCopy.put(pos.toImmutable(), nbt.copy()));
        return new StructureRenderData(id, new Vec3i(size.getX(), size.getY(), size.getZ()), blockCopy, entityCopy);
    }

    public Map<BlockPos, IBlockState> getBlocks()
    {
        return this.blocks;
    }

    public Map<BlockPos, NBTTagCompound> getBlockEntities()
    {
        return this.blockEntities;
    }

    /** How this structure is lit — shared by both fake worlds, so they agree; see {@link StructureLighting}. */
    public StructureLighting getLighting()
    {
        if (this.lighting == null)
        {
            this.lighting = StructureLighting.compute(this);
        }

        return this.lighting;
    }

    public IBlockState getBlockState(BlockPos pos)
    {
        IBlockState state = this.blocks.get(pos);

        return state == null ? Blocks.AIR.getDefaultState() : state;
    }

    public boolean isEmpty()
    {
        return this.blocks.isEmpty();
    }

    public static StructureRenderData parse(String id, NBTTagCompound root)
    {
        NBTTagList sizeList = root.getTagList("size", Constants.NBT.TAG_INT);
        Vec3i size = new Vec3i(sizeList.getIntAt(0), sizeList.getIntAt(1), sizeList.getIntAt(2));

        NBTTagList paletteNbt;

        if (root.hasKey("palette", Constants.NBT.TAG_LIST))
        {
            paletteNbt = root.getTagList("palette", Constants.NBT.TAG_COMPOUND);
        }
        else
        {
            /* "palettes" variant: several random palettes, the first one is good enough */
            NBTTagList palettes = root.getTagList("palettes", Constants.NBT.TAG_LIST);

            paletteNbt = palettes.tagCount() == 0 ? new NBTTagList() : (NBTTagList) palettes.get(0);
        }

        IBlockState[] palette = new IBlockState[paletteNbt.tagCount()];

        for (int i = 0; i < palette.length; i++)
        {
            palette[i] = NBTUtil.readBlockState(paletteNbt.getCompoundTagAt(i));
        }

        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        Map<BlockPos, NBTTagCompound> blockEntities = new LinkedHashMap<>();
        NBTTagList blocksNbt = root.getTagList("blocks", Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < blocksNbt.tagCount(); i++)
        {
            NBTTagCompound block = blocksNbt.getCompoundTagAt(i);
            int stateIndex = block.getInteger("state");

            if (stateIndex < 0 || stateIndex >= palette.length)
            {
                continue;
            }

            IBlockState state = palette[stateIndex];

            if (state.getBlock() == Blocks.AIR || state.getBlock() == Blocks.STRUCTURE_VOID)
            {
                continue;
            }

            NBTTagList posList = block.getTagList("pos", Constants.NBT.TAG_INT);
            BlockPos pos = new BlockPos(posList.getIntAt(0), posList.getIntAt(1), posList.getIntAt(2));

            blocks.put(pos, state);

            if (block.hasKey("nbt", Constants.NBT.TAG_COMPOUND))
            {
                blockEntities.put(pos, block.getCompoundTag("nbt"));
            }
        }

        return new StructureRenderData(id, size, blocks, blockEntities);
    }
}
