package mchorse.bbs_mod.particles.components.shape;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ParticleComponentShapeDisc extends ParticleComponentShapeSphere
{
    public MolangExpression[] normal = {MolangParser.ZERO, MolangParser.ONE, MolangParser.ZERO};

    @Override
    public void toData(MapType data)
    {
        super.toData(data);

        ListType list = new ListType();

        for (MolangExpression expression : this.normal)
        {
            list.add(expression.toData());
        }

        data.put("plane_normal", list);
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return super.fromData(data, parser);
        }

        MapType map = data.asMap();

        if (map.has("plane_normal"))
        {
            BaseType normal = map.get("plane_normal");

            if (normal.isString())
            {
                String axis = normal.asString().toLowerCase();

                if (axis.equals("x"))
                {
                    this.normal[0] = MolangParser.ONE;
                    this.normal[1] = MolangParser.ZERO;
                }
                else if (axis.equals("z"))
                {
                    this.normal[1] = MolangParser.ZERO;
                    this.normal[2] = MolangParser.ONE;
                }
            }
            else
            {
                ListType array = map.getList("plane_normal");

                if (array.size() >= 3)
                {
                    this.normal[0] = parser.parseDataSilently(array.get(0));
                    this.normal[1] = parser.parseDataSilently(array.get(1), MolangParser.ONE);
                    this.normal[2] = parser.parseDataSilently(array.get(2));
                }
            }
        }

        return super.fromData(map, parser);
    }

    @Override
    public void apply(ParticleEmitter emitter, Particle particle)
    {
        float centerX = (float) this.offset[0].get();
        float centerY = (float) this.offset[1].get();
        float centerZ = (float) this.offset[2].get();

        Vector3f forward = new Vector3f((float) this.normal[0].get(), (float) this.normal[1].get(), (float) this.normal[2].get());

        if (forward.distanceSquared(0, 0, 0) == 0)
        {
            forward.set(0, 1, 0);
        }

        forward.normalize();

        Matrix4f rotation = new Matrix4f(Matrices.direction(forward));
        Vector4f position = new Vector4f((float) Math.random() - 0.5F, 0, (float) Math.random() - 0.5F, 0);
        position.normalize();
        rotation.transform(position);

        position.mul((float) (this.radius.get() * (this.surface ? 1 : Math.random())));
        position.add(new Vector4f(centerX, centerY, centerZ, 0));

        particle.position.x += position.x;
        particle.position.y += position.y;
        particle.position.z += position.z;

        this.direction.applyDirection(particle, centerX, centerY, centerZ);
    }
}