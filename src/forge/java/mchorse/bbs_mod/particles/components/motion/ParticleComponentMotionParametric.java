package mchorse.bbs_mod.particles.components.motion;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.ParticleUtils;
import mchorse.bbs_mod.particles.components.IComponentParticleInitialize;
import mchorse.bbs_mod.particles.components.IComponentParticleUpdate;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import org.joml.Vector3f;

public class ParticleComponentMotionParametric extends ParticleComponentMotion implements IComponentParticleInitialize, IComponentParticleUpdate
{
    public MolangExpression[] position = {MolangParser.ZERO, MolangParser.ZERO, MolangParser.ZERO};
    public MolangExpression rotation = MolangParser.ZERO;

    @Override
    protected void toData(MapType data)
    {
        /* Always write the owned axis (even if zero) so the parametric choice survives a round-trip;
         * otherwise an all-zero parametric component would serialize empty and be dropped. */
        if (this.drivesPosition)
        {
            data.put("relative_position", ParticleUtils.vectorToList(this.position));
        }

        if (this.drivesRotation)
        {
            data.put("rotation", this.rotation.toData());
        }
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return super.fromData(data, parser);
        }

        MapType map = data.asMap();

        /* Which axes this parametric component owns is inferred from the present fields. */
        this.drivesPosition = map.has("relative_position");
        this.drivesRotation = map.has("rotation");

        if (this.drivesPosition && map.get("relative_position").isList())
        {
            ParticleUtils.vectorFromList(map.getList("relative_position"), this.position, parser);
        }

        if (this.drivesRotation)
        {
            this.rotation = parser.parseDataSilently(map.get("rotation"));
        }

        return super.fromData(map, parser);
    }

    @Override
    public void apply(ParticleEmitter emitter, Particle particle)
    {
        if (this.drivesPosition)
        {
            particle.manualPosition = true;
            particle.initialPosition.set(particle.position);

            this.applyPosition(particle);
        }

        if (this.drivesRotation)
        {
            particle.manualRotation = true;
            particle.rotation = (float) this.rotation.get();
        }
    }

    @Override
    public void update(ParticleEmitter emitter, Particle particle)
    {
        if (this.drivesPosition)
        {
            this.applyPosition(particle);
        }

        if (this.drivesRotation)
        {
            particle.rotation = (float) this.rotation.get();
        }
    }

    private void applyPosition(Particle particle)
    {
        Vector3f position = new Vector3f((float) this.position[0].get(), (float) this.position[1].get(), (float) this.position[2].get());

        particle.matrix.transform(position);
        particle.position.x = particle.initialPosition.x + position.x;
        particle.position.y = particle.initialPosition.y + position.y;
        particle.position.z = particle.initialPosition.z + position.z;
    }

    @Override
    public int getSortingIndex()
    {
        return 10;
    }
}
