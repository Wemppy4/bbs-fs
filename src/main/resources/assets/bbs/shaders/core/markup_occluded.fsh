#version 150

/* The world's depth, captured at the end of its pass (see SceneDepth) */
uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
/* The world projection's terms: distance = DepthParams.y / (ndcZ + DepthParams.x) */
uniform vec2 DepthParams;
/* Where the world was drawn in the depth texture (fractions: x, y, width, height) */
uniform vec4 DepthViewport;
/* The world's picture on screen, in framebuffer pixels from the bottom left */
uniform vec4 OverlayFrame;
/* Where the anti-aliasing layer's buffer sits in the framebuffer */
uniform vec2 LayerOffset;

in float inverseDistance;
in vec4 vertexColor;

out vec4 fragColor;

void main()
{
    vec2 frame = (gl_FragCoord.xy + LayerOffset - OverlayFrame.xy) / OverlayFrame.zw;
    float depth = texture(Sampler0, DepthViewport.xy + frame * DepthViewport.zw).r;

    /* Nothing drawn there: the sky */
    if (depth < 1.0)
    {
        float scene = DepthParams.y / (depth * 2.0 - 1.0 + DepthParams.x);
        float own = 1.0 / max(inverseDistance, 0.000001);

        /* Hidden behind a block or a model; a little slack for lines lying on a surface */
        if (own > scene + max(0.15, scene * 0.004))
        {
            discard;
        }
    }

    fragColor = vertexColor * ColorModulator;
}
