#version 150

#moj_import <fog.glsl>
#moj_import <bbs_pixelart.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main()
{
    vec4 color = vec4(bbs_pixelart_intensity(Sampler0, texCoord0)) * vertexColor * ColorModulator;

    /* Vanilla discards below 0.1, which would eat the smoothed edge of a glyph */
    if (color.a < 0.01)
    {
        discard;
    }

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
