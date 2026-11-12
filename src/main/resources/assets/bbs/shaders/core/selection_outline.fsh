#version 150

/* The selected actor alone: each part in its pick id's colour, and its depth */
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
/* The world's depth (see SceneDepth) */
uniform sampler2D Sampler2;

uniform vec4 ColorModulator;
uniform vec4 OutlineColor;
/* The selected bone: its pick id (0 for none) and colour */
uniform vec4 BoneColor;
uniform int SelectedIndex;
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

/* The pick id of the part seen there: -1 where the actor isn't drawn or the world stands in front */
int seenId(vec2 uv)
{
    vec4 mask = texture(Sampler0, uv);

    if (mask.a < 0.5)
    {
        return -1;
    }

    if (UseScene > 0.5)
    {
        float scene = texture(Sampler2, SceneViewport.xy + uv * SceneViewport.zw).r;

        if (scene < 1.0)
        {
            float own = MaskDepth.y / (texture(Sampler1, uv).r * 2.0 - 1.0 + MaskDepth.x);
            float world = SceneDepth.y / (scene * 2.0 - 1.0 + SceneDepth.x);

            if (own > world + max(0.1, world * 0.01))
            {
                return -1;
            }
        }
    }

    return int(mask.r * 255.0 + 0.5) | (int(mask.g * 255.0 + 0.5) << 8) | (int(mask.b * 255.0 + 0.5) << 16);
}

void main()
{
    int here = seenId(texCoord);
    bool bone = SelectedIndex > 0;
    bool inBone = bone && here == SelectedIndex;
    float actorHits = 0.0;
    float boneHits = 0.0;

    for (int i = 0; i < 8; i++)
    {
        float angle = float(i) * 0.785398;
        vec2 direction = vec2(cos(angle), sin(angle)) * Texel;

        for (int r = 1; r <= 2; r++)
        {
            int id = seenId(texCoord + direction * float(r));

            actorHits += id >= 0 ? 1.0 : 0.0;
            boneHits += bone && id == SelectedIndex ? 1.0 : 0.0;
        }
    }

    /* The selected bone: a ring around its part, over whatever is beside it, and a faint tint inside */
    if (bone && !inBone && boneHits > 0.0)
    {
        fragColor = vec4(BoneColor.rgb, BoneColor.a * clamp(boneHits / 4.0, 0.0, 1.0)) * ColorModulator;

        return;
    }

    if (inBone)
    {
        fragColor = vec4(BoneColor.rgb, BoneColor.a * 0.15) * ColorModulator;

        return;
    }

    /* The actor: a ring around the silhouette, never over it */
    if (here >= 0 || actorHits <= 0.0)
    {
        discard;
    }

    fragColor = vec4(OutlineColor.rgb, OutlineColor.a * clamp(actorHits / 4.0, 0.0, 1.0)) * ColorModulator;
}
