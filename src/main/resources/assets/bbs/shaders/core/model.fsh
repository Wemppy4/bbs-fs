#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int PassMode;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec4 normal;

out vec4 fragColor;

void main()
{
    vec4 color = texture(Sampler0, texCoord0);

    if (color.a < 0.1)
    {
        discard;
    }

    color *= vertexColor * ColorModulator;

    /* Two-pass translucency: pass 1 keeps only opaque texels (they write depth), pass 2 —
     * deferred, sorted far-to-near, no depth write — keeps only the semi-transparent ones.
     * The test uses final alpha, so form/bone color alpha counts too. PassMode 0 = single pass. */
    if (PassMode == 1 && color.a < 0.999)
    {
        discard;
    }

    if (PassMode == 2 && color.a >= 0.999)
    {
        discard;
    }

    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}