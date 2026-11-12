#version 150

/* The selected actor alone: its silhouette (alpha) and its depth */
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
/* The world's depth (see SceneDepth) */
uniform sampler2D Sampler2;

uniform vec4 ColorModulator;
uniform vec4 OutlineColor;
/* One screen pixel in the mask's coordinates */
uniform vec2 Texel;
/* Depth terms of the two projections: distance = y / (ndcZ + x) */
uniform vec2 MaskDepth;
uniform vec2 SceneDepth;
/* Where the world was drawn in its depth texture (fractions) */
uniform vec4 SceneViewport;
uniform float UseScene;

in vec2 texCoord;

out vec4 fragColor;

/* Where the actor is seen: drawn there, and nothing of the world stands in front */
float seen(vec2 uv)
{
    if (texture(Sampler0, uv).a < 0.5)
    {
        return 0.0;
    }

    if (UseScene < 0.5)
    {
        return 1.0;
    }

    float scene = texture(Sampler2, SceneViewport.xy + uv * SceneViewport.zw).r;

    if (scene >= 1.0)
    {
        return 1.0;
    }

    float own = MaskDepth.y / (texture(Sampler1, uv).r * 2.0 - 1.0 + MaskDepth.x);
    float world = SceneDepth.y / (scene * 2.0 - 1.0 + SceneDepth.x);

    return own <= world + max(0.1, world * 0.01) ? 1.0 : 0.0;
}

void main()
{
    /* The outline goes around the silhouette, never over the actor */
    if (seen(texCoord) > 0.5)
    {
        discard;
    }

    float hits = 0.0;

    for (int i = 0; i < 8; i++)
    {
        float angle = float(i) * 0.785398;
        vec2 direction = vec2(cos(angle), sin(angle)) * Texel;

        hits += seen(texCoord + direction);
        hits += seen(texCoord + direction * 2.0);
    }

    if (hits <= 0.0)
    {
        discard;
    }

    fragColor = vec4(OutlineColor.rgb, OutlineColor.a * clamp(hits / 4.0, 0.0, 1.0)) * ColorModulator;
}
