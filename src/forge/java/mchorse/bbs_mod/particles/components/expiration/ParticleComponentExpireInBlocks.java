package mchorse.bbs_mod.particles.components.expiration;

import mchorse.bbs_mod.particles.components.IComponentParticleUpdate;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.minecraft.block.state.IBlockState;

public class ParticleComponentExpireInBlocks extends ParticleComponentExpireBlocks implements IComponentParticleUpdate
{
    @Override
    public void update(ParticleEmitter emitter, Particle particle)
    {
        if (particle.isDead() || emitter.world == null)
        {
            return;
        }

        IBlockState current = this.getBlock(emitter, particle);

        for (String block : this.blocks)
        {
            if (current.getBlock().getRegistryName().toString().equals(block))
            {
                particle.setDead();

                return;
            }
        }
    }
}