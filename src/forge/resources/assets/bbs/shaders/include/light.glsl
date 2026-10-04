// Minecraft 1.20 lighting equations required by the unmodified BBS core shaders.
#define MINECRAFT_LIGHT_POWER (0.6)
#define MINECRAFT_AMBIENT_LIGHT (0.4)
vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {
    float a = max(0.0, dot(normalize(lightDir0), normal));
    float b = max(0.0, dot(normalize(lightDir1), normal));
    return vec4(color.rgb * min(1.0, (a + b) * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT), color.a);
}
vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}
