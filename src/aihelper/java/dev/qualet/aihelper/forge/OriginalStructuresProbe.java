package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.structure.StructureManager;
import mchorse.bbs_mod.forms.structure.StructureRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.OutputStream;
import java.util.List;

/** Native NBT/provider/cache/light tests. Only temporary files inside ai_test and its asset library. */
public final class OriginalStructuresProbe
{
    public static JsonObject run(JsonObject params) throws Exception
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getIntegratedServer() == null) throw new IllegalStateException("Integrated test world required");
        Path world = mc.getIntegratedServer().getWorld(0).getSaveHandler().getWorldDirectory().toPath();
        if (!world.getFileName().toString().equals("ai_test")) throw new IllegalStateException("Only ai_test is allowed");
        String name = "__aihelper_structure_" + System.currentTimeMillis();
        Path global = BBSMod.getAssetsPath("structures/" + name + ".nbt").toPath();
        Path local = world.resolve("bbs/assets/structures/" + name + ".nbt");
        Path legacy = world.resolve("structures/" + name + ".nbt");
        Path generated = world.resolve("generated/aihelper/structures/" + name + ".nbt");
        String id = StructureManager.assetId(name);
        JsonObject result = new JsonObject();
        try
        {
            write(global, structure(true));
            write(local, structure(false));
            write(legacy, structure(true));
            write(generated, structure(true));
            StructureManager.invalidate();
            List<String> ids = StructureManager.getStructureIds();
            result.addProperty("listedAssets", ids.contains(id));
            result.addProperty("listedLegacy", ids.contains("minecraft:" + name));
            result.addProperty("listedGenerated", ids.contains("aihelper:" + name));
            StructureRenderData data = StructureManager.get(id);
            result.addProperty("globalPriority", data != null && data.getBlockState(BlockPos.ORIGIN).getBlock() == Blocks.GLOWSTONE);
            result.addProperty("geometry", data != null && data.getBlocks().size() == 3 && data.size.getX() == 3);
            result.addProperty("tileNbt", data != null && "Probe chest".equals(data.getBlockEntities().get(new BlockPos(2, 0, 0)).getString("CustomName")));
            result.addProperty("legacyLoad", StructureManager.get("minecraft:" + name) != null);
            result.addProperty("generatedLoad", StructureManager.get("aihelper:" + name) != null);
            result.addProperty("emitterLight", data.getLighting().getLightLevel(EnumSkyBlock.BLOCK, BlockPos.ORIGIN));
            result.addProperty("adjacentLight", data.getLighting().getLightLevel(EnumSkyBlock.BLOCK, new BlockPos(0, 1, 0)));
            result.addProperty("opaqueLight", data.getLighting().getLightLevel(EnumSkyBlock.BLOCK, new BlockPos(1, 0, 0)));
            result.addProperty("skyLight", data.getLighting().getLightLevel(EnumSkyBlock.SKY, new BlockPos(0, 1, 0)));
            result.addProperty("distantLight", data.getLighting().getLightLevel(EnumSkyBlock.BLOCK, new BlockPos(16, 0, 0)));
            write(global, structure(false));
            result.addProperty("cached", StructureManager.get(id) == data);
            int before = StructureManager.getGeneration();
            StructureManager.invalidate();
            StructureRenderData changed = StructureManager.get(id);
            result.addProperty("invalidated", changed != data && changed.getBlockState(BlockPos.ORIGIN).getBlock() == Blocks.STONE
                && StructureManager.getGeneration() > before);
            String previewId = StructureManager.nextPreviewId();
            StructureRenderData preview = StructureRenderData.create(previewId, data.size, data.getBlocks(), data.getBlockEntities());
            StructureManager.setPreview(preview);
            StructureManager.invalidate();
            result.addProperty("previewSurvivesInvalidation", StructureManager.get(previewId) == preview);
            result.addProperty("uniquePreviewIds", !previewId.equals(StructureManager.nextPreviewId()));
            result.addProperty("pathBoundary", StructureManager.get("assets:../../escape") == null && StructureManager.get("../../escape") == null);
            result.addProperty("ok", true);
            return result;
        }
        finally
        {
            StructureManager.setPreview(null);
            for (Path file : new Path[] {global, local, legacy, generated}) Files.deleteIfExists(file);
            StructureManager.invalidate();
        }
    }

    private static void write(Path file, NBTTagCompound nbt) throws Exception
    {
        Files.createDirectories(file.getParent());
        try (OutputStream stream = Files.newOutputStream(file)) { CompressedStreamTools.writeCompressed(nbt, stream); }
    }

    private static NBTTagList vector(int x, int y, int z)
    {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagInt(x)); list.appendTag(new NBTTagInt(y)); list.appendTag(new NBTTagInt(z));
        return list;
    }

    private static NBTTagCompound structure(boolean glow)
    {
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("size", vector(3, 2, 1));
        root.setInteger("DataVersion", 1343);
        NBTTagList palette = new NBTTagList();
        palette.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(), (glow ? Blocks.GLOWSTONE : Blocks.STONE).getDefaultState()));
        palette.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(), Blocks.STONE.getDefaultState()));
        palette.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(), Blocks.CHEST.getDefaultState()));
        palette.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(), Blocks.AIR.getDefaultState()));
        palette.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(), Blocks.STRUCTURE_VOID.getDefaultState()));
        root.setTag("palette", palette);
        NBTTagList blocks = new NBTTagList();
        for (int i = 0; i < 5; i++)
        {
            NBTTagCompound block = new NBTTagCompound();
            block.setInteger("state", i);
            block.setTag("pos", vector(i % 3, i / 3, 0));
            if (i == 2)
            {
                NBTTagCompound chest = new NBTTagCompound();
                chest.setString("id", "minecraft:chest"); chest.setString("CustomName", "Probe chest");
                block.setTag("nbt", chest);
            }
            blocks.appendTag(block);
        }
        root.setTag("blocks", blocks);
        return root;
    }
}
