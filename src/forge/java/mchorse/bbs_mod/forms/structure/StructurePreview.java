package mchorse.bbs_mod.forms.structure;

import net.minecraft.init.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

/**
 * The wand's region as a structure before it is saved: read out of the client world through the
 * same {@link Template} the server will use, so every block in the dialog is the block
 * that lands in the file. The client world is enough — block states and block entity data are all
 * the renderer wants.
 *
 * <p>Entities are the one thing left out where the server takes them. Nothing renders them from a
 * structure, so capturing them would cost a walk over the region's entities to draw nothing; the
 * file still gets them.</p>
 */
public class StructurePreview
{
    /** Regions above this many blocks are saved blind: reading and tesselating them would stall the dialog. */
    public static final long LIMIT = 200_000;

    /** @return the region parsed the way a structure file is, or null without a world */
    public static StructureRenderData capture(String id, BlockPos min, Vec3i size)
    {
        WorldClient world = Minecraft.getMinecraft().world;

        if (world == null)
        {
            return null;
        }

        Template template = new Template();

        template.takeBlocksFromWorld(world, min, new BlockPos(size), false, Blocks.STRUCTURE_VOID);

        return StructureRenderData.parse(id, template.writeToNBT(new NBTTagCompound()));
    }
}
