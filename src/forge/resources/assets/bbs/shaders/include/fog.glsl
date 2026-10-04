// Minecraft 1.20 fog equations required by the unmodified BBS core shaders.
vec4 linear_fog(vec4 color, float distance, float start, float end, vec4 fogColor) {
    if (distance <= start) return color;
    float amount = distance < end ? smoothstep(start, end, distance) : 1.0;
    return vec4(mix(color.rgb, fogColor.rgb, amount * fogColor.a), color.a);
}
float linear_fog_fade(float distance, float start, float end) {
    if (distance <= start) return 1.0;
    if (distance >= end) return 0.0;
    return smoothstep(end, start, distance);
}
float fog_distance(mat4 modelView, vec3 position, int shape) {
    if (shape == 0) return length((modelView * vec4(position, 1.0)).xyz);
    float xz = length((modelView * vec4(position.x, 0.0, position.z, 1.0)).xyz);
    float y = length((modelView * vec4(0.0, position.y, 0.0, 1.0)).xyz);
    return max(xz, y);
}
