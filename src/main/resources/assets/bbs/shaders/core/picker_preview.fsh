#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(std140) uniform BBSPicker {
    vec4 HighlightColor;
    int Target;
};

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main()
{
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;

    if (color.a < 1.0)
    {
        discard;
    }

    int totalIndex = int(color.r * 255.0) | (int(color.g * 255.0) << 8) | (int(color.b * 255.0) << 16);

    if (totalIndex == Target)
    {
        color = HighlightColor;
    }
    else
    {
        discard;
    }

    fragColor = color * ColorModulator;
}
