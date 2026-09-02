#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

in vec2 texCoord;

out vec4 fragColor;

// Vanilla's box_blur, with the alpha channel carried through instead of averaged.
//
// Averaging alpha is what vanilla does, and it is right for the world, whose alpha is 1 everywhere.
// The interface is not: it is drawn into a buffer whose alpha varies, and averaging it down turns the
// blurred picture dark in patches. 1.21.1 kept the channel out of the way with
// RenderSystem.colorMask(true, true, true, false); the GPU rewrite removed colorMask, and a post pass
// cannot mask a channel from the outside, so the shader does it instead — the same trick the
// pre-1.21.1 blur program used when it summed alpha rather than averaging it.
//
// Like vanilla, this relies on GL_LINEAR sampling to halve the number of samples: it steps between
// pixels by 2 rather than over each one, and the last (the count is always odd) at half weight.
void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec2 sampleStep = oneTexel * BlurDir;

    vec4 blurred = vec4(0.0);
    float actualRadius = max(1.0, round(Radius));
    for (float a = -actualRadius + 0.5; a <= actualRadius; a += 2.0) {
        blurred += texture(InSampler, texCoord + sampleStep * a);
    }
    blurred += texture(InSampler, texCoord + sampleStep * actualRadius) / 2.0;
    fragColor = vec4((blurred / (actualRadius + 0.5)).rgb, texture(InSampler, texCoord).a);
}
