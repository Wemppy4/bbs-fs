package mchorse.bbs_mod.particles.components;

import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.particles.ParticleVertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.joml.Matrix4f;

public interface IComponentParticleRender extends IComponentBase
{
    public void preRender(ParticleEmitter emitter, float transition);

    public void render(ParticleEmitter emitter, VertexFormat format, Particle particle, ParticleVertexBuffer builder, Matrix4f matrix, int overlay, float transition);

    public void renderUI(Particle particle, ParticleVertexBuffer builder, Matrix4f matrix, float transition);

    public void postRender(ParticleEmitter emitter, float transition);
}