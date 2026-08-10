package mchorse.bbs_mod.cubic.render.vao;

/**
 * Triangle-soup geometry for a {@link ModelVAO}: one entry per vertex, positions and normals in
 * triples, texture coordinates in pairs.
 *
 * <p>1.21.1 carried a fourth 'tangents' channel for Iris normal mapping. The draw format
 * ({@code POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL}) has no tangent attribute, so nothing ever read
 * it back; re-add it with a real computation if tangent-space normal mapping is ever wired up.</p>
 */
public record ModelVAOData(float[] vertices, float[] normals, float[] texCoords)
{}
