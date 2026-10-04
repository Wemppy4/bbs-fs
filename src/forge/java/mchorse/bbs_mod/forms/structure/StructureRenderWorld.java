package mchorse.bbs_mod.forms.structure;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

/** Native block-render view: structure-local neighbors, selected biome and traced light. */
public class StructureRenderWorld implements IBlockAccess
{
    private final StructureRenderData data;
    private final Biome biome;

    public StructureRenderWorld(StructureRenderData data, String biomeId)
    {
        this.data = data;
        Biome selected = null;
        if (biomeId != null && !biomeId.isEmpty())
        {
            try { selected = Biome.REGISTRY.getObject(new ResourceLocation(biomeId)); }
            catch (IllegalArgumentException ignored) {}
        }
        this.biome = selected == null ? Biomes.PLAINS : selected;
    }

    public StructureRenderData getData() { return this.data; }
    public IBlockState getBlockState(BlockPos pos) { return this.data.getBlockState(pos); }
    public TileEntity getTileEntity(BlockPos pos) { return null; }
    public Biome getBiome(BlockPos pos) { return this.biome; }
    public WorldType getWorldType() { return WorldType.DEFAULT; }
    public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getMaterial() == Material.AIR; }
    public int getStrongPower(BlockPos pos, EnumFacing direction) { return getBlockState(pos).getStrongPower(this, pos, direction); }
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean fallback) { return getBlockState(pos).isSideSolid(this, pos, side); }
    public int getLightLevel(EnumSkyBlock type, BlockPos pos) { return this.data.getLighting().getLightLevel(type, pos); }
    public int getCombinedLight(BlockPos pos, int lightValue)
    {
        return getLightLevel(EnumSkyBlock.SKY, pos) << 20 | Math.max(lightValue, getLightLevel(EnumSkyBlock.BLOCK, pos)) << 4;
    }
    public float getBrightness(EnumFacing direction, boolean shaded) { return StructureLighting.getBrightness(direction, shaded); }
}
