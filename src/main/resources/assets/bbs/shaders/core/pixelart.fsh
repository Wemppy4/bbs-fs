#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <bbs:bbs_pixelart.glsl>

// Textured UI quads (icons, BBS textures, in-panel previews), drawn by the pipeline derived from
// vanilla's GUI_TEXTURED — same vertex shader (minecraft:core/position_tex_color), only the sampling
// differs. Vanilla discards on a.a == 0.0; the smoothed edge lands just above that, so the threshold
// stays where the seam ends.

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = bbs_pixelart(Sampler0, texCoord0) * vertexColor;

    if (color.a < 0.01) {
        discard;
    }

    fragColor = color * ColorModulator;
}
