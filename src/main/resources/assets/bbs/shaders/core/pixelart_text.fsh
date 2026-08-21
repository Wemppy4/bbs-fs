#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <bbs:bbs_pixelart.glsl>

// Vanilla's rendertype_text.fsh with two changes: the atlas is sampled through bbs_pixelart (the seam
// between texels compressed into one screen pixel), and the discard threshold drops from 0.1 to 0.01 —
// vanilla's would eat the smoothed edge of every glyph. The vertex shader is vanilla's own, so the
// varyings below are exactly the ones it writes.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = bbs_pixelart(Sampler0, texCoord0) * vertexColor * ColorModulator;

    if (color.a < 0.01) {
        discard;
    }

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
