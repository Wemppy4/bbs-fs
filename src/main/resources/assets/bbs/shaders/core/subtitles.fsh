#version 330

uniform sampler2D Sampler0;

layout(std140) uniform SubtitlesInfo {
    vec2 Blur;
    vec2 TextureSize;
};

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

vec4 texture_blur(sampler2D tex, vec2 uv, float blur)
{
    float tau = 6.28318530718;

    float directions = 24.0;
    float quality = 3.0;

    vec2 radius = blur / TextureSize;
    vec4 color = texture(tex, uv);

    for (float d = 0.0; d < tau; d += tau / directions)
    {
        for (float i= 1.0 / quality; i <= 1.0; i += 1.0 / quality)
        {
            color += texture(tex, uv + vec2(cos(d), sin(d)) * radius * i);
        }
    }

    // Output to screen
    color /= quality * directions - 15.0;

    return color;
}

void texture_opaque_blur(inout vec4 out_color, sampler2D tex, vec2 uv, float blur)
{
    if (out_color.a < 1)
    {
        vec2 radius = blur / TextureSize;

        for (float x = -blur; x <= blur; x++)
        {
            for (float y = -blur; y <= blur; y++)
            {
                if (texture(Sampler0, texCoord0 + radius * vec2(x, y)).a >= 1)
                {
                    out_color.rgb = vec3(0, 0, 0);
                    out_color.a = 1.0;

                    return;
                }
            }
        }
    }
}

void main()
{
    vec4 color = texture(Sampler0, texCoord0);

    float blur = Blur.x;
    float opaque = Blur.y;

    if (blur > 0)
    {
        if (opaque > 0)
        {
            texture_opaque_blur(color, Sampler0, texCoord0, blur);
        }
        else
        {
            vec4 blurred_color = texture_blur(Sampler0, texCoord0, blur);

            if (color.a < 1)
            {
                blurred_color.rgb = vec3(0, 0, 0);
                blurred_color.a *= 0.5;
                color = blurred_color;
            }
        }
    }

    fragColor = color * vertexColor;
}
