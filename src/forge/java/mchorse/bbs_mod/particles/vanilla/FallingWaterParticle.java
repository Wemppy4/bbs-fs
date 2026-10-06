package mchorse.bbs_mod.particles.vanilla;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The falling stage of a water drip, without 1.12's forty-tick hanging stage. */
public class FallingWaterParticle extends Particle
{
    public FallingWaterParticle(World world, double x, double y, double z)
    {
        super(world, x, y, z);
        this.setSize(0.01F, 0.01F);
        this.setParticleTextureIndex(112);
        this.setRBGColorF(0.2F, 0.3F, 1F);
        this.particleMaxAge = (int) (64D / (this.rand.nextDouble() * 0.8D + 0.2D));
    }

    @Override
    public void onUpdate()
    {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (this.particleMaxAge-- <= 0)
        {
            this.setExpired();
            return;
        }

        this.motionY -= 0.06D;
        this.move(this.motionX, this.motionY, this.motionZ);

        if (this.onGround)
        {
            this.setExpired();
            this.world.spawnParticle(EnumParticleTypes.WATER_SPLASH, this.posX, this.posY, this.posZ, 0, 0, 0);
        }

        this.motionX *= 0.98D;
        this.motionY *= 0.98D;
        this.motionZ *= 0.98D;

        BlockPos pos = new BlockPos(this.posX, this.posY, this.posZ);
        IBlockState state = this.world.getBlockState(pos);

        if (state.getMaterial() == Material.WATER && state.getBlock() instanceof BlockLiquid)
        {
            double surface = pos.getY() + 1D - BlockLiquid.getLiquidHeightPercent(state.getValue(BlockLiquid.LEVEL));

            if (this.posY < surface) this.setExpired();
        }
    }
}
