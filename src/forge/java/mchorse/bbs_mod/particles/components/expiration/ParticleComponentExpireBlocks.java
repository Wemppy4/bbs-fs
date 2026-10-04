package mchorse.bbs_mod.particles.components.expiration;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public abstract class ParticleComponentExpireBlocks extends ParticleComponentBase
{
    public List<String> blocks = new ArrayList<>();

    @Override
    public BaseType toData()
    {
        return DataStorageUtils.stringListToData(this.blocks);
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isList())
        {
            return super.fromData(data, parser);
        }

        this.blocks = DataStorageUtils.stringListFromData(data);

        return super.fromData(data, parser);
    }

    public IBlockState getBlock(ParticleEmitter emitter, Particle particle)
    {
        if (emitter.world == null)
        {
            return null;
        }

        Vector3d position = particle.getGlobalPosition(emitter);

        return emitter.world.getBlockState(new BlockPos(position.x, position.y, position.z));
    }
}