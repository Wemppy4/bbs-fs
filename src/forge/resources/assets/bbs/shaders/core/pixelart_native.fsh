#version 150 compatibility

#moj_import <bbs_pixelart.glsl>

uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main()
{
    vec4 color = bbs_pixelart(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.01) discard;
    fragColor = color;
}
