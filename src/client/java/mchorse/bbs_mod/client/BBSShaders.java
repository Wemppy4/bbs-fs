package mchorse.bbs_mod.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Custom shader/render foundation for BBS, migrated from the 1.21.1 ShaderProgram + JSON
 * shader-program system to the 1.21.5+ GPU pipeline.
 *
 * In 1.21.1 each effect was a {@code net.minecraft.client.gl.ShaderProgram} constructed from a
 * {@code <name>.json} program definition that referenced {@code <name>.vsh}/{@code <name>.fsh}.
 * In 1.21.5+ {@code ShaderProgram}, {@code RenderSystem.setShader(...)} and
 * {@code GameRenderer.getXxxProgram()} were removed; the JSON program format is gone. Rendering
 * now goes through a {@link RenderPipeline} (declaring vertex/fragment shader, vertex format,
 * blend, depth, cull, samplers, uniforms) wrapped in a {@link RenderLayer} via
 * {@link RenderLayer#of(String, RenderSetup)} / {@link RenderSetup#builder(RenderPipeline)}.
 *
 * The GLSL assets (assets/bbs/shaders/core/*.vsh|*.fsh) are kept as-is and referenced through the
 * shader Identifier {@code bbs:core/<name>} (no file extension; the loader appends it).
 *
 * TODO(1.21.11 render): the kept GLSL is still in the old 1.21.1 header/import style
 * (#version 150, loose `uniform` declarations, #moj_import &lt;light.glsl&gt;/&lt;fog.glsl&gt;).
 * 1.21.5 moved built-in uniforms into std140 UBO blocks (Projection / Fog / Lighting / DynamicTransforms)
 * and changed the import set. Each kept .vsh/.fsh almost certainly needs its header rewritten to the
 * new layout-block style before it will link. That is an asset migration, tracked separately.
 *
 * TODO(1.21.11 render): the per-draw custom uniforms each effect used to set imperatively via
 * {@code program.getUniform("...")} (ColorModulator, Target, HighlightColor, Size, Filters, Blur,
 * TextureSize, IViewRotMat, the two Light directions, NormalMat) no longer exist as mutable
 * GlUniforms. They must be supplied as UBO entries / DynamicUniforms and uploaded per render pass.
 * The custom uniform set for each pipeline is documented below so the caller-migration phase can
 * wire them up. Verify at runtime.
 */
public class BBSShaders
{
    /* All BBS shaders used "add / srcalpha / 1-srcalpha" in their JSON, i.e. standard alpha blending. */
    private static final BlendFunction BLEND = BlendFunction.TRANSLUCENT;

    /* ---- model ----
     * VertexFormat: POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
     * Samplers: Sampler0 (albedo), Sampler1 (overlay), Sampler2 (lightmap)
     * Builtin std140 UBOs (1.21.5+): DynamicTransforms (ModelViewMat/ColorModulator),
     *                  Projection (ProjMat), Fog, Lighting (Light0/1_Direction).
     * The 1.21.1 per-instance NormalMat/IViewRotMat are gone: the pose normal matrix is now applied
     * CPU-side at buffer-build time (CubicCubeRenderer transforms each Normal before emitting it), so
     * the migrated bbs:core/model GLSL feeds the raw Normal straight into minecraft_mix_light.
     */
    private static final RenderPipeline MODEL = registerModel();

    /* ---- multilink ----
     * VertexFormat: POSITION_TEXTURE_COLOR
     * Samplers: Sampler0, Sampler3
     * Custom uniforms: ColorModulator(vec4), Size(vec2), Filters(vec4)
     */
    private static final RenderPipeline MULTILINK = register(
        "multilink", VertexFormats.POSITION_TEXTURE_COLOR,
        "Sampler0", "Sampler3"
    );

    /* ---- subtitles ----
     * VertexFormat: POSITION_TEXTURE_COLOR
     * Samplers: Sampler0
     * Custom uniforms: Blur(vec2), TextureSize(vec2)
     */
    private static final RenderPipeline SUBTITLES = register(
        "subtitles", VertexFormats.POSITION_TEXTURE_COLOR,
        "Sampler0"
    );

    /* ---- picker_preview ----
     * VertexFormat: POSITION_TEXTURE_COLOR
     * Samplers: Sampler0
     * Custom uniforms: ColorModulator(vec4), Target(int), HighlightColor(vec4)
     */
    private static final RenderPipeline PICKER_PREVIEW = register(
        "picker_preview", VertexFormats.POSITION_TEXTURE_COLOR,
        "Sampler0"
    );

    /* ---- picker_billboard ----
     * VertexFormat: POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
     * Samplers: Sampler0, Sampler2
     * Custom uniforms: IViewRotMat(mat3), Target(int)
     */
    private static final RenderPipeline PICKER_BILLBOARD = register(
        "picker_billboard", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
        "Sampler0", "Sampler2"
    );

    /* ---- picker_billboard_no_shading ----
     * VertexFormat: POSITION_TEXTURE_LIGHT_COLOR
     * Samplers: Sampler0, Sampler2
     * Custom uniforms: IViewRotMat(mat3), Target(int)
     */
    private static final RenderPipeline PICKER_BILLBOARD_NO_SHADING = register(
        "picker_billboard_no_shading", VertexFormats.POSITION_TEXTURE_LIGHT_COLOR,
        "Sampler0", "Sampler2"
    );

    /* ---- picker_particles ----
     * VertexFormat: POSITION_COLOR_TEXTURE_LIGHT
     * Samplers: Sampler0
     * Custom uniforms: ColorModulator(vec4), Target(int)
     */
    private static final RenderPipeline PICKER_PARTICLES = register(
        "picker_particles", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT,
        "Sampler0"
    );

    /* ---- picker_models ----
     * VertexFormat: POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
     * Samplers: Sampler0, Sampler2
     * Custom uniforms: IViewRotMat(mat3), Target(int)
     */
    private static final RenderPipeline PICKER_MODELS = register(
        "picker_models", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
        "Sampler0", "Sampler2"
    );

    /* ---- particles ----
     * The NORMAL (non-picking) particle pipeline, the faithful equivalent of the 1.21.1
     * GameRenderer::getParticleProgram the original ParticleFormRenderer used for non-shaders
     * rendering (the picker_particles pipeline is for picking only — it outputs a Target-index
     * colour, not the texture). Migrated std140 clone of vanilla's particle shader.
     * VertexFormat: POSITION_TEXTURE_COLOR_LIGHT (matches ParticleEmitter's non-shaders buffer)
     * Samplers: Sampler0 (albedo), Sampler2 (lightmap)
     * Builtin std140 UBOs: DynamicTransforms (ModelViewMat/ColorModulator), Projection, Fog.
     */
    private static final RenderPipeline PARTICLES = registerParticles();

    /* Lazily-built render layers (one per pipeline). RenderLayer.of caches nothing itself, so we
     * memoize here to keep a single instance the immediate buffer source can key on. */
    private static RenderLayer modelLayer;
    private static RenderLayer multiLinkLayer;
    private static RenderLayer subtitlesLayer;
    private static RenderLayer pickerPreviewLayer;
    private static RenderLayer pickerBillboardLayer;
    private static RenderLayer pickerBillboardNoShadingLayer;
    private static RenderLayer pickerParticlesLayer;
    private static RenderLayer pickerModelsLayer;
    private static RenderLayer particlesLayer;

    /**
     * Kept for API compatibility with the old {@code BBSShaders.setup()} callsite
     * (UIUtilityOverlayPanel). Pipelines are now registered once statically with the vanilla
     * pipeline registry and are reloaded by the engine on resource reload, so there is nothing to
     * re-create here. Left as a no-op.
     *
     * TODO(1.21.11 render): verify at runtime that no explicit re-registration is needed after a
     * resource-pack reload; if it is, move the register(...) calls here behind a guard.
     */
    public static void setup()
    {
    }

    /* ----------------------------------------------------------------------------------------
     * Public API — pipeline accessors. Names kept stable with the 1.21.1 ShaderProgram getters;
     * return type changed ShaderProgram -> RenderPipeline (the faithful 1.21.5 equivalent).
     * ---------------------------------------------------------------------------------------- */

    public static RenderPipeline getModel()
    {
        return MODEL;
    }

    public static RenderPipeline getMultilinkProgram()
    {
        return MULTILINK;
    }

    public static RenderPipeline getSubtitlesProgram()
    {
        return SUBTITLES;
    }

    public static RenderPipeline getPickerPreviewProgram()
    {
        return PICKER_PREVIEW;
    }

    public static RenderPipeline getPickerBillboardProgram()
    {
        return PICKER_BILLBOARD;
    }

    public static RenderPipeline getPickerBillboardNoShadingProgram()
    {
        return PICKER_BILLBOARD_NO_SHADING;
    }

    public static RenderPipeline getPickerParticlesProgram()
    {
        return PICKER_PARTICLES;
    }

    public static RenderPipeline getPickerModelsProgram()
    {
        return PICKER_MODELS;
    }

    /* ----------------------------------------------------------------------------------------
     * Public API — render-layer accessors. These wrap the pipeline in a RenderLayer ready for a
     * VertexConsumerProvider. Use these from form/UI renderers instead of the old
     * RenderSystem.setShader(...) + manual buffer flow.
     * ---------------------------------------------------------------------------------------- */

    public static RenderLayer getModelLayer()
    {
        if (modelLayer == null)
        {
            modelLayer = layer("model", MODEL, true);
        }

        return modelLayer;
    }

    public static RenderLayer getMultilinkLayer()
    {
        if (multiLinkLayer == null)
        {
            multiLinkLayer = layer("multilink", MULTILINK, false);
        }

        return multiLinkLayer;
    }

    public static RenderLayer getSubtitlesLayer()
    {
        if (subtitlesLayer == null)
        {
            subtitlesLayer = layer("subtitles", SUBTITLES, false);
        }

        return subtitlesLayer;
    }

    public static RenderLayer getPickerPreviewLayer()
    {
        if (pickerPreviewLayer == null)
        {
            pickerPreviewLayer = layer("picker_preview", PICKER_PREVIEW, false);
        }

        return pickerPreviewLayer;
    }

    public static RenderLayer getPickerBillboardLayer()
    {
        if (pickerBillboardLayer == null)
        {
            pickerBillboardLayer = layer("picker_billboard", PICKER_BILLBOARD, true);
        }

        return pickerBillboardLayer;
    }

    public static RenderLayer getPickerBillboardNoShadingLayer()
    {
        if (pickerBillboardNoShadingLayer == null)
        {
            pickerBillboardNoShadingLayer = layer("picker_billboard_no_shading", PICKER_BILLBOARD_NO_SHADING, true);
        }

        return pickerBillboardNoShadingLayer;
    }

    public static RenderLayer getPickerParticlesLayer()
    {
        if (pickerParticlesLayer == null)
        {
            pickerParticlesLayer = layer("picker_particles", PICKER_PARTICLES, false);
        }

        return pickerParticlesLayer;
    }

    public static RenderLayer getPickerModelsLayer()
    {
        if (pickerModelsLayer == null)
        {
            pickerModelsLayer = layer("picker_models", PICKER_MODELS, true);
        }

        return pickerModelsLayer;
    }

    /**
     * The normal (non-picking) particle layer. Built with only useLightmap() (Sampler2) — the
     * POSITION_TEXTURE_COLOR_LIGHT format has no overlay (UV1), so unlike {@link #layer} it must NOT
     * call useOverlay(). Sampler0 (the per-emitter texture) is fed via the global texture binding
     * ParticleEmitter.render performs before the draw, same as BillboardFormRenderer.
     */
    public static RenderLayer getParticlesLayer()
    {
        if (particlesLayer == null)
        {
            RenderSetup.Builder setup = RenderSetup.builder(PARTICLES)
                .expectedBufferSize(RenderLayer.field_64008)
                .translucent()
                .useLightmap();

            particlesLayer = RenderLayer.of(BBSMod.MOD_ID + "_particles", setup.build());
        }

        return particlesLayer;
    }

    /* ----------------------------------------------------------------------------------------
     * Builders
     * ---------------------------------------------------------------------------------------- */

    /**
     * Build and register the model pipeline. Identical to {@link #register} but additionally declares
     * the four builtin std140 UBO blocks the migrated {@code bbs:core/model} GLSL imports
     * (light.glsl / fog.glsl / dynamictransforms.glsl / projection.glsl). Declared in the same order
     * the vanilla entity pipeline uses (DynamicTransforms, Projection, Fog, Lighting) so the engine
     * binds them; without these the model shader fails to link and every world draw is a no-op.
     */
    private static RenderPipeline registerModel()
    {
        Identifier shader = Identifier.of(BBSMod.MOD_ID, "core/model");

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/model"))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2");

        return RenderPipelines.register(builder.build());
    }

    /**
     * Build and register the particles pipeline. Like {@link #registerModel} it declares the builtin
     * std140 UBOs the migrated {@code bbs:core/particles} GLSL imports (fog / dynamictransforms /
     * projection), but no Lighting block (particles are not directionally lit) and the
     * POSITION_TEXTURE_COLOR_LIGHT format the emitter builds. Sampler0 = albedo, Sampler2 = lightmap.
     */
    private static RenderPipeline registerParticles()
    {
        Identifier shader = Identifier.of(BBSMod.MOD_ID, "core/particles");

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/particles"))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler2");

        return RenderPipelines.register(builder.build());
    }

    /**
     * Build and register a RenderPipeline for a BBS core shader. The vertex and fragment shader
     * both resolve to the GLSL asset {@code bbs:core/<name>} (.vsh/.fsh).
     */
    private static RenderPipeline register(String name, VertexFormat format, String... samplers)
    {
        Identifier shader = Identifier.of(BBSMod.MOD_ID, "core/" + name);

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/" + name))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexFormat(format, VertexFormat.DrawMode.QUADS)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false);

        for (String sampler : samplers)
        {
            builder.withSampler(sampler);
        }

        return RenderPipelines.register(builder.build());
    }

    /**
     * Wrap a pipeline in a RenderLayer. The expected buffer size mirrors the vanilla entity-layer
     * default; affectsOutline/translucent are passed through to RenderSetup.
     */
    private static RenderLayer layer(String name, RenderPipeline pipeline, boolean useLightmapOverlay)
    {
        RenderSetup.Builder setup = RenderSetup.builder(pipeline)
            .expectedBufferSize(RenderLayer.field_64008)
            .translucent();

        if (useLightmapOverlay)
        {
            setup.useLightmap().useOverlay();
        }

        return RenderLayer.of(BBSMod.MOD_ID + "_" + name, setup.build());
    }
}
