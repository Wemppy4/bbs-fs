package mchorse.bbs_mod.forms.structure;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

/**
 * The wand's region as a structure before it is saved: read out of the client world through the
 * same {@link StructureTemplate} the server will use, so the save dialog shows exactly what the
 * file is going to hold. The client world is enough for that — block states and block entity data
 * are all the renderer wants, and entities are left out.
 */
public class StructurePreview
{
    /** Regions above this many blocks are saved blind: reading and tesselating them would stall the dialog. */
    public static final long LIMIT = 200_000;

    /** @return the region parsed the way a structure file is, or null without a world */
    public static StructureRenderData capture(String id, BlockPos min, Vec3i size)
    {
        ClientWorld world = MinecraftClient.getInstance().world;

        if (world == null)
        {
            return null;
        }

        StructureTemplate template = new StructureTemplate();

        template.saveFromWorld(world, min, size, false, Blocks.STRUCTURE_VOID);

        return StructureRenderData.parse(id, template.writeNbt(new NbtCompound()));
    }
}
