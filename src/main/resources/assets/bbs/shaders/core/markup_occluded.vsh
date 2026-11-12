#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float inverseDistance;
out vec4 vertexColor;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    /* 1 / distance from the eye: screen-linear on a flat overlay, unlike the distance itself */
    inverseDistance = UV0.x;
    vertexColor = Color;
}
