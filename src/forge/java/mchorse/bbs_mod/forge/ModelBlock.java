package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

public class ModelBlock extends BlockContainer
{
    public ModelBlock() {
        super(Material.ROCK);
        setRegistryName("bbs", "model"); setTranslationKey("bbs.model");
        setHardness(0.5F); setCreativeTab(CreativeTabs.DECORATIONS);
    }
    public TileEntity createNewTileEntity(World world, int meta) { return new ModelTileEntity(); }
    public boolean isOpaqueCube(IBlockState state) { return false; }
    public boolean isFullCube(IBlockState state) { return false; }
    public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.INVISIBLE; }
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
        EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (world.isRemote && hand == EnumHand.MAIN_HAND) BBSMod.proxy.openModel((ModelTileEntity) world.getTileEntity(pos));
        return true;
    }
}
