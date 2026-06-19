#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;

layout(std140) uniform MultilinkInfo {
    vec4 Filters;
    vec2 Size;
};

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main()
{
    vec2 coord = texCoord0 * Size;

    coord.x = floor(coord.x);
    coord.y = floor(coord.y);

    int pixelate = int(Filters.r);
    int erase = int(Filters.g);

    if (erase == 1)
    {
        coord.x = mod(coord.x, 16);
        coord.y = mod(coord.y, 16) + 240;
        coord /= vec2(256, 256);

        fragColor = (texture(Sampler0, texCoord0).a > 0.6 ? 1 : 0) * texture(Sampler3, coord);
    }
    else
    {
        coord.x -= mod(coord.x, pixelate);
        coord.y -= mod(coord.y, pixelate);
        coord /= Size;

        fragColor = texture(Sampler0, coord) * ColorModulator;
    }

    fragColor *= vertexColor;
}
