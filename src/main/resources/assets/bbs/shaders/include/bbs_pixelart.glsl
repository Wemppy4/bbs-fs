#version 150

/*
 * Sampling of pixel art drawn at a fractional scale.
 *
 * BBS's ui_scale is a float, so an 8 texel tall glyph can land on 14 screen
 * pixels: with nearest sampling some rows of texels get duplicated twice and
 * some once, which is what makes text and icons look ragged at 1.25/1.5/1.75.
 *
 * The fix is not bilinear filtering — that would just blur everything. The
 * inside of a texel stays flat and the seam between two texels is spread over
 * exactly one screen pixel. At an integer scale every screen pixel lands on a
 * texel centre and this collapses back into plain nearest sampling, so 1 and 2
 * look exactly like they did before.
 */

/**
 * Position (in texels) to sample the texture at, with the seam between texels
 * compressed to one screen pixel.
 */
vec2 bbs_pixelart_position(vec2 uv, vec2 size)
{
    vec2 texel = uv * size;
    vec2 seam = floor(texel + 0.5);
    vec2 pixel = max(fwidth(texel), vec2(1.0e-5));

    return seam + clamp((texel - seam) / pixel, vec2(-0.5), vec2(0.5));
}

/**
 * Bilinear tap done by hand: UI atlases are uploaded with a NEAREST filter and
 * this must not depend on the texture's filter state.
 *
 * Colour is weighted premultiplied, otherwise a fully transparent neighbour
 * (the empty padding around a glyph in the font atlas) would drag the edge of
 * the glyph towards black and leave a dark fringe.
 */
vec4 bbs_pixelart(sampler2D tex, vec2 uv)
{
    vec2 size = vec2(textureSize(tex, 0));
    vec2 texel = bbs_pixelart_position(uv, size) - 0.5;
    vec2 base = floor(texel);
    vec2 weight = texel - base;

    ivec2 last = ivec2(size) - 1;
    ivec2 i0 = clamp(ivec2(base), ivec2(0), last);
    ivec2 i1 = clamp(ivec2(base) + 1, ivec2(0), last);

    vec4 t00 = texelFetch(tex, ivec2(i0.x, i0.y), 0);
    vec4 t10 = texelFetch(tex, ivec2(i1.x, i0.y), 0);
    vec4 t01 = texelFetch(tex, ivec2(i0.x, i1.y), 0);
    vec4 t11 = texelFetch(tex, ivec2(i1.x, i1.y), 0);

    t00.rgb *= t00.a;
    t10.rgb *= t10.a;
    t01.rgb *= t01.a;
    t11.rgb *= t11.a;

    vec4 color = mix(mix(t00, t10, weight.x), mix(t01, t11, weight.x), weight.y);

    return color.a > 0.0 ? vec4(color.rgb / color.a, color.a) : vec4(0.0);
}

/**
 * Same, for the single channel glyphs of the unicode font, where the red
 * channel is the coverage and the other channels carry nothing.
 */
float bbs_pixelart_intensity(sampler2D tex, vec2 uv)
{
    vec2 size = vec2(textureSize(tex, 0));
    vec2 texel = bbs_pixelart_position(uv, size) - 0.5;
    vec2 base = floor(texel);
    vec2 weight = texel - base;

    ivec2 last = ivec2(size) - 1;
    ivec2 i0 = clamp(ivec2(base), ivec2(0), last);
    ivec2 i1 = clamp(ivec2(base) + 1, ivec2(0), last);

    float t00 = texelFetch(tex, ivec2(i0.x, i0.y), 0).r;
    float t10 = texelFetch(tex, ivec2(i1.x, i0.y), 0).r;
    float t01 = texelFetch(tex, ivec2(i0.x, i1.y), 0).r;
    float t11 = texelFetch(tex, ivec2(i1.x, i1.y), 0).r;

    return mix(mix(t00, t10, weight.x), mix(t01, t11, weight.x), weight.y);
}
