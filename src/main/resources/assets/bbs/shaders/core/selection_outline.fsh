#version 150

/* The selected actor alone: each part in its pick id's colour, and its depth */
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
/* The world's depth (see SceneDepth) */
uniform sampler2D Sampler2;

uniform vec4 ColorModulator;
uniform vec4 OutlineColor;
/* The edited bone's pick id, 0 for the whole actor */
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
    /* One selection: the edited bone's part when there is one, else the whole actor */
    bool bone = SelectedIndex > 0;
    int here = seenId(texCoord);
    bool inside = bone ? here == SelectedIndex : here >= 0;

    /* A ring around it, never over it */
    if (inside)
    {
        discard;
    }

    float hits = 0.0;

    for (int i = 0; i < 8; i++)
    {
        float angle = float(i) * 0.785398;
        vec2 direction = vec2(cos(angle), sin(angle)) * Texel;

        for (int r = 1; r <= 2; r++)
        {
            int id = seenId(texCoord + direction * float(r));

            hits += (bone ? id == SelectedIndex : id >= 0) ? 1.0 : 0.0;
        }
    }

    if (hits <= 0.0)
    {
        discard;
    }

    fragColor = vec4(OutlineColor.rgb, OutlineColor.a * clamp(hits / 4.0, 0.0, 1.0)) * ColorModulator;
}
