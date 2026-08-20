#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

/* Two-pass translucency. On 1.21.1 the split rode a mutable `uniform int PassMode`; 1.21.5+ has no
 * mutable GlUniforms, so it is a compile-time define instead and each pass is its own registered
 * RenderPipeline variant (BBSShaders.getModelLayer). Same semantics as 1.21.1: pass 1 keeps only the
 * opaque texels (drawn immediately, writing depth), pass 2 keeps only the semi-transparent ones and
 * is replayed at the end of the frame sorted far-to-near by FormTranslucentQueue. The tests of 1/2
 * use the FINAL alpha, so form/bone colour alpha counts too. 0 = single pass.
 *
 * Passes 3/4 are the same partition made on the TEXTURE's own alpha, before the colour multiplies
 * in: a uniformly faded model (form alpha < 1) replays as this pair so its texture-opaque texels
 * still land first and its shading texels (a skin's second layer) still blend over THEM — one
 * final-alpha pass in buffer order let those texels blend with whatever stood behind the model,
 * and the model's shading visibly jumped the moment alpha left 100%. At form alpha == 1 the
 * partition is identical to 1/2, which is what keeps the boundary seamless. */
#ifndef PASS_MODE
#define PASS_MODE 0
#endif

void main()
{
    vec4 color = texture(Sampler0, texCoord0);

    if (color.a < 0.1)
    {
        discard;
    }

    float texAlpha = color.a;

    color *= vertexColor * ColorModulator;

#if PASS_MODE == 1
    if (color.a < 0.999)
    {
        discard;
    }
#elif PASS_MODE == 2
    if (color.a >= 0.999)
    {
        discard;
    }
#elif PASS_MODE == 3
    if (texAlpha < 0.999)
    {
        discard;
    }
#elif PASS_MODE == 4
    if (texAlpha >= 0.999)
    {
        discard;
    }
#endif

    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
