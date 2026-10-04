package mchorse.bbs_mod.forge;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import java.util.Random;

/** Original chroma blocks: solid, unbreakable backdrop with no drops or debris. */
public final class ChromaBlock extends Block
{
    public ChromaBlock(String color)
    {
        super(Material.ROCK);setRegistryName("bbs","chroma_"+color);setTranslationKey("bbs.chroma_"+color);
        setBlockUnbreakable();setResistance(3600000F);setCreativeTab(CommonProxy.BBS_TAB);
    }
    @Override public int quantityDropped(Random random) { return 0; }
    @Override @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean addHitEffects(IBlockState state,net.minecraft.world.World world,RayTraceResult target,ParticleManager manager) { return true; }
    @Override @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean addDestroyEffects(net.minecraft.world.World world,BlockPos pos,ParticleManager manager) { return true; }
}
