#version 150

uniform sampler2D Sampler0;

/* Animation phase for the marching-ants pattern; 0 or 1 (toggles each tick). */
uniform float Phase;

/* Screen (UI) pixels per document texel. Lets the outline keep a constant on-screen thickness and
 * dash size regardless of canvas size / zoom, instead of being one (possibly huge) document pixel. */
uniform float Scale;

in vec2 texCoord0;

out vec4 fragColor;

/* The selection mask, sampled as 1.0 inside the selection and 0.0 outside. Anything sampled outside
 * the texture counts as unselected, so a selection touching the document border still gets an edge. */
float maskAt(vec2 uv)
{
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0)
    {
        return 0.0;
    }

    return texture(Sampler0, uv).r;
}

void main()
{
    ivec2 size = textureSize(Sampler0, 0);
    vec2 texel = 1.0 / vec2(size);

    /* The outline lives on the white (selected) side: only selected texels can be drawn. */
    if (maskAt(texCoord0) < 0.5)
    {
        discard;
    }

    /* Which sides of this texel face an unselected texel (4-neighbourhood) — those are the edges. */
    bool edgeLeft  = maskAt(texCoord0 + vec2(-texel.x, 0.0)) < 0.5;
    bool edgeRight = maskAt(texCoord0 + vec2( texel.x, 0.0)) < 0.5;
    bool edgeUp    = maskAt(texCoord0 + vec2(0.0, -texel.y)) < 0.5;
    bool edgeDown  = maskAt(texCoord0 + vec2(0.0,  texel.y)) < 0.5;

    if (!(edgeLeft || edgeRight || edgeUp || edgeDown))
    {
        discard;
    }

    /* Keep the outline a constant 1 screen pixel thick, hugging the inner edge: measure the distance
     * (in texel fractions) to the nearest boundary side and keep only that one-pixel band. When zoomed
     * out (Scale < 1) the whole border texel passes, which is the thinnest possible. */
    float thickness = 1.0 / max(Scale, 0.0001);
    vec2 inTexel = fract(texCoord0 * vec2(size));
    float dist = 1.0;

    if (edgeLeft)  dist = min(dist, inTexel.x);
    if (edgeRight) dist = min(dist, 1.0 - inTexel.x);
    if (edgeUp)    dist = min(dist, inTexel.y);
    if (edgeDown)  dist = min(dist, 1.0 - inTexel.y);

    if (dist > thickness)
    {
        discard;
    }

    /* Checkerboard marching ants in screen space (constant square size at any zoom). Using the
     * per-axis cell parity (not x+y) keeps the cells axis-aligned squares instead of diagonal bands. */
    vec2 screen = texCoord0 * vec2(size) * Scale;
    float cell = 4.0;
    float cx = floor(screen.x / cell);
    float cy = floor(screen.y / cell);
    bool white = mod(cx + cy + floor(Phase), 2.0) < 1.0;

    fragColor = white ? vec4(1.0, 1.0, 1.0, 1.0) : vec4(0.0, 0.0, 0.0, 1.0);
}
