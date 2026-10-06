package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBody;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

/** Per-block body and item data, using Forge's contextual block queries. */
public class ModelBlock extends BlockContainer
{
    public static BooleanSupplier editingCheck = () -> false;
    public ModelBlock()
    {
        super(Material.ROCK);
        this.setRegistryName("bbs", "model"); this.setTranslationKey("bbs.model");
        this.setHardness(0F); this.setLightOpacity(0); this.setCreativeTab(CreativeTabs.DECORATIONS);
    }
    private static ModelTileEntity tile(IBlockAccess world, BlockPos pos)
    {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof ModelTileEntity ? (ModelTileEntity) tile : null;
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ModelTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.INVISIBLE; }
    @Override @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean addHitEffects(IBlockState state, World world, RayTraceResult target, net.minecraft.client.particle.ParticleManager manager) { return true; }
    @Override @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean addDestroyEffects(World world, BlockPos pos, net.minecraft.client.particle.ParticleManager manager) { return true; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos)
    {
        ModelTileEntity tile = tile(world, pos);
        AxisAlignedBB box = tile == null ? FULL_BLOCK_AABB : tile.getShape();
        return editingCheck.getAsBoolean() ? box.union(FULL_BLOCK_AABB) : box;
    }
    @Override @Nullable public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos)
    {
        ModelTileEntity tile = tile(world, pos);
        return tile != null && tile.getProperties().getBody().isSolid() ? tile.getShape() : NULL_AABB;
    }
    @Override @Nullable public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end)
    {
        if (ModelCameraHooks.isCameraTrace())
        {
            ModelTileEntity tile = tile(world, pos);
            return tile != null && tile.getProperties().getBody().isCameraCollision()
                ? this.rayTrace(pos, start, end, tile.getShape()) : null;
        }
        return super.collisionRayTrace(state, world, pos, start, end);
    }
    @Override public boolean isPassable(IBlockAccess world, BlockPos pos)
    {
        ModelTileEntity tile = tile(world, pos);
        return tile == null || !tile.getProperties().getBody().isSolid();
    }
    @Override public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos)
    {
        ModelTileEntity tile = tile(world, pos);
        return tile == null ? 0 : tile.getProperties().getBody().getLightLevel();
    }
    @Override public SoundType getSoundType(IBlockState state, World world, BlockPos pos, @Nullable Entity entity)
    {
        ModelTileEntity tile = tile(world, pos);
        return tile == null ? SoundType.STONE : tile.getProperties().getBody().getSound().group;
    }
    @Override public float getBlockHardness(IBlockState state, World world, BlockPos pos)
    {
        ModelTileEntity tile = tile(world, pos);
        return tile == null ? 0F : tile.getProperties().getBody().getHardness();
    }
    @Override public float getPlayerRelativeBlockHardness(IBlockState state, EntityPlayer player, World world, BlockPos pos)
    {
        float hardness = this.getBlockHardness(state, world, pos);
        return hardness <= 0F ? 1F : super.getPlayerRelativeBlockHardness(state, player, world, pos);
    }
    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player)
    {
        return this.stack(world, pos);
    }
    private ItemStack stack(IBlockAccess world, BlockPos pos)
    {
        ItemStack stack = new ItemStack(this);
        ModelTileEntity tile = tile(world, pos);
        if (tile != null)
        {
            NBTTagCompound root = new NBTTagCompound();
            root.setTag("BlockEntityTag", tile.writeToNBT(new NBTTagCompound()));
            stack.setTagCompound(root);
        }
        return stack;
    }
    @Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune)
    {
        drops.add(this.stack(world, pos));
    }
    @Override public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest)
    {
        return willHarvest || super.removedByPlayer(state, world, pos, player, false);
    }
    @Override public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity te, ItemStack tool)
    {
        super.harvestBlock(world, player, pos, state, te, tool);
        world.setBlockToAir(pos);
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack)
    {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        world.checkLight(pos);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
        EnumHand hand, EnumFacing face, float x, float y, float z)
    {
        ModelTileEntity tile = tile(world, pos);
        if (tile == null || !player.capabilities.isCreativeMode) return false;
        if (world.isRemote && hand == EnumHand.MAIN_HAND) BBSMod.proxy.openModel(tile);
        return true;
    }
}
