package mchorse.bbs_mod.particles.components.appearance;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.particles.ParticleParser;
import mchorse.bbs_mod.particles.components.IComponentParticleRender;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.components.appearance.colors.Solid;
import mchorse.bbs_mod.particles.components.appearance.colors.Tint;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.particles.ParticleVertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.joml.Matrix4f;

public class ParticleComponentAppearanceTinting extends ParticleComponentBase implements IComponentParticleRender
{
    public Tint color = new Solid(MolangParser.ONE, MolangParser.ONE, MolangParser.ONE, MolangParser.ONE);

    @Override
    protected void toData(MapType data)
    {
        BaseType color = this.color.toData();

        if (!ParticleParser.isEmpty(color))
        {
            data.put("color", color);
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

        if (map.has("color"))
        {
            BaseType color = map.get("color");

            if (color.isList() || BaseType.isPrimitive(color))
            {
                this.color = Tint.parseColor(color, parser);
            }
            else if (color.isMap())
            {
                this.color = Tint.parseGradient(color.asMap(), parser);
            }
        }

        return super.fromData(map, parser);
    }

    /* Interface implementations */

    @Override
    public void preRender(ParticleEmitter emitter, float transition)
    {}

    @Override
    public void render(ParticleEmitter emitter, VertexFormat format, Particle particle, ParticleVertexBuffer builder, Matrix4f matrix, int overlay, float transition)
    {
        this.renderUI(particle, builder, matrix, transition);
    }

    @Override
    public void renderUI(Particle particle, ParticleVertexBuffer builder, Matrix4f matrix, float transition)
    {
        if (this.color != null)
        {
            this.color.compute(particle);
        }
        else
        {
            particle.r = particle.g = particle.b = particle.a = 1;
        }
    }

    @Override
    public void postRender(ParticleEmitter emitter, float transition)
    {}

    @Override
    public int getSortingIndex()
    {
        return -10;
    }
}