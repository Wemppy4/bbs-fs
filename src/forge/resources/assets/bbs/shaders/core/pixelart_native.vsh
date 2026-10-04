#version 150 compatibility

out vec2 texCoord0;
out vec4 vertexColor;

void main()
{
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    texCoord0 = gl_MultiTexCoord0.xy;
    vertexColor = gl_Color;
}
