#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <bbs:bbs_pixelart.glsl>

// The single-channel half of the pair: the unicode font's glyphs carry coverage in red and nothing in
// the other channels, so the smoothing is done on that one channel and splatted (vanilla's .rrrr).

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = vec4(bbs_pixelart_intensity(Sampler0, texCoord0)) * vertexColor * ColorModulator;

    if (color.a < 0.01) {
        discard;
    }

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
