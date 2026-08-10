package mchorse.bbs_mod.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
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

    /**
     * A model pipeline is registered per (variant, world) pair. The world axis exists for shaderpacks:
     * the world copy of a pipeline is assigned to one of the pack's programs
     * ({@link BBSRendering#assignIrisPipeline}), which is the only way its draws land in the pack's
     * G-buffers and survive the end-of-frame composite. Assignment is per pipeline and permanent, so
     * the shared copy — which also draws the form editor's and film panel's previews into BBS's own
     * framebuffers — must never be the assigned one: a run proved the pack's program follows it there
     * and clips the preview against the world's depth. {@link BBSRendering#isIrisWorldForms()} is the
     * switch between the two.
     */
    private record PipelineKey(ModelVariant variant, boolean world)
    {}

    /**
     * Registered model pipelines by variant; each is a separate GLSL compile (different PASS_MODE define).
     *
     * <p>MUST stay above the pipeline fields below: static fields initialise in declaration order, and
     * those fields call {@link #modelPipeline} — which reads this map. Declared after them it is still
     * null at that point, and the whole class fails to initialise.
     */
    private static final java.util.Map<PipelineKey, RenderPipeline> modelPipelines = new java.util.HashMap<>();

    /* ---- model ----
     * VertexFormat: POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
     * Samplers: Sampler0 (albedo), Sampler1 (overlay), Sampler2 (lightmap)
     * Builtin std140 UBOs (1.21.5+): DynamicTransforms (ModelViewMat/ColorModulator),
     *                  Projection (ProjMat), Fog, Lighting (Light0/1_Direction).
     * The 1.21.1 per-instance NormalMat/IViewRotMat are gone: the pose normal matrix is now applied
     * CPU-side at buffer-build time (CubicCubeRenderer transforms each Normal before emitting it), so
     * the migrated bbs:core/model GLSL feeds the raw Normal straight into minecraft_mix_light.
     *
     * The field exists for its class-load side effect: it registers the variant every ordinary form
     * draw asks for, so it is compiled with the rest of the pipeline set rather than mid-frame.
     * Consumers reach it through {@link #getModelLayer}, which finds it memoized in modelPipelines.
     */
    @SuppressWarnings("unused")
    private static final RenderPipeline MODEL = modelPipeline(new ModelVariant(FormTranslucentQueue.PASS_SINGLE, true, false), false);

    /* ---- model (culled) ----
     * The MODEL pipeline with backface culling ON, for geometry built as explicit front/back face
     * pairs — the billboard quad emits both faces with opposite winding AND opposite normals, and
     * relies on the GPU to drop the one turned away from the viewer. Without culling both survive:
     * they are coplanar, the back one is emitted second, and LEQUAL lets equal depth through, so
     * the quad ends up showing its back face and is lit from behind (mix_light 0.40 instead of the
     * front face's ~1.0). On 1.21.1 these draws went through the global GL state, where vanilla
     * keeps culling enabled — only LabelFormRenderer and cubic models with !isCulling() switched it
     * off around their own draws, and the billboard's deferred command was flagged cull = true.
     *
     * Registered eagerly for the same reason as MODEL; reached via {@link #getBoundCulledModelLayer()}.
     */
    @SuppressWarnings("unused")
    private static final RenderPipeline MODEL_CULLED = modelPipeline(new ModelVariant(FormTranslucentQueue.PASS_SINGLE, true, true), false);

    /**
     * What distinguishes one model-pipeline variant from another. On 1.21.1 all three were mutable
     * state around a single program — {@code PassMode} a loose uniform, depth-write and cull global GL
     * toggles flipped per draw by {@link FormTranslucentQueue#flush()}. 1.21.5+ has none of that: a
     * pipeline is immutable, so each combination the form renderers actually ask for is its own
     * registered pipeline, built on demand from the same {@code bbs:core/model} GLSL with PASS_MODE
     * supplied as a shader define.
     *
     * @param pass       {@link FormTranslucentQueue#PASS_SINGLE}/{@code PASS_OPAQUE}/{@code PASS_TRANSLUCENT}
     * @param depthWrite solid geometry keeps writing depth so it self-occludes; flat single-quad forms don't
     * @param cull       the model's own culling flag (see MODEL_CULLED)
     */
    public record ModelVariant(int pass, boolean depthWrite, boolean cull)
    {
        public static final ModelVariant SINGLE = new ModelVariant(FormTranslucentQueue.PASS_SINGLE, true, false);

        public ModelVariant withPass(int pass)
        {
            return new ModelVariant(pass, this.depthWrite, this.cull);
        }

        public ModelVariant withDepthWrite(boolean depthWrite)
        {
            return new ModelVariant(this.pass, depthWrite, this.cull);
        }

        public ModelVariant withCull(boolean cull)
        {
            return new ModelVariant(this.pass, this.depthWrite, cull);
        }

        private String suffix()
        {
            return (this.pass == FormTranslucentQueue.PASS_OPAQUE ? "_opaque" : this.pass == FormTranslucentQueue.PASS_TRANSLUCENT ? "_translucent" : "")
                + (this.depthWrite ? "" : "_nodepth")
                + (this.cull ? "_culled" : "");
        }
    }


    /* ---- multilink ----
     * VertexFormat: POSITION_TEXTURE_COLOR
     * Samplers: Sampler0, Sampler3
     * Builtin std140 UBOs (1.21.5+): DynamicTransforms (ModelViewMat/ColorModulator), Projection (ProjMat).
     * Custom std140 UBO MultilinkInfo: Filters(vec4), Size(vec2) — the BBS-specific pixelate/erase params
     * the 1.21.1 shader set imperatively as loose `uniform`s. No fog/lighting (2D UI shader).
     */
    private static final RenderPipeline MULTILINK = registerMultilink();

    /* ---- subtitles ----
     * VertexFormat: POSITION_TEXTURE_COLOR
     * Samplers: Sampler0
     * Builtin std140 UBOs (1.21.5+): DynamicTransforms (ModelViewMat), Projection (ProjMat).
     * Custom std140 UBO SubtitlesInfo: Blur(vec2), TextureSize(vec2) — the BBS-specific blur params
     * the 1.21.1 shader set imperatively as loose `uniform`s. No ColorModulator/fog/lighting (2D UI shader).
     */
    private static final RenderPipeline SUBTITLES = registerSubtitles();

    /**
     * The std140 UBO block name shared by every migrated picker shader. The block packs the two
     * BBS-custom uniforms the old loose {@code uniform int Target} / {@code uniform vec4 HighlightColor}
     * became (vec4 first for std140 16-byte alignment):
     * <pre>layout(std140) uniform BBSPicker { vec4 HighlightColor; int Target; };</pre>
     * It is uploaded per draw by {@link mchorse.bbs_mod.client.render.picker.BBSPickerRenderer}; the
     * RenderLayer immediate path cannot carry it (it binds only the engine builtins), so picker draws
     * go through that renderer's manual render pass instead of {@link RenderLayer#draw}.
     */
    public static final String PICKER_UNIFORM = "BBSPicker";

    /* ---- picker_preview ----
     * VertexFormat: POSITION_TEXTURE_COLOR
     * Samplers: Sampler0
     * Builtin std140 UBOs: DynamicTransforms (ModelViewMat/ColorModulator), Projection (ProjMat).
     * Custom std140 UBO: BBSPicker (HighlightColor vec4, Target int).
     */
    private static final RenderPipeline PICKER_PREVIEW = registerPicker(
        "picker_preview", VertexFormats.POSITION_TEXTURE_COLOR
    );

    /* ---- picker_billboard ----
     * VertexFormat: POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
     * Samplers: Sampler0
     * Custom std140 UBO: BBSPicker (Target int).
     */
    private static final RenderPipeline PICKER_BILLBOARD = registerPicker(
        "picker_billboard", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
    );

    /* ---- picker_billboard_no_shading ----
     * VertexFormat: POSITION_TEXTURE_LIGHT_COLOR
     * Samplers: Sampler0
     * Custom std140 UBO: BBSPicker (Target int).
     */
    private static final RenderPipeline PICKER_BILLBOARD_NO_SHADING = registerPicker(
        "picker_billboard_no_shading", VertexFormats.POSITION_TEXTURE_LIGHT_COLOR
    );

    /* ---- picker_particles ----
     * VertexFormat: POSITION_COLOR_TEXTURE_LIGHT
     * Samplers: Sampler0
     * Builtin std140 UBO: DynamicTransforms (ColorModulator), Projection.
     * Custom std140 UBO: BBSPicker (Target int).
     */
    private static final RenderPipeline PICKER_PARTICLES = registerPicker(
        "picker_particles", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT
    );

    /* ---- picker_models ----
     * VertexFormat: POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
     * Samplers: Sampler0
     * Custom std140 UBO: BBSPicker (Target int); per-vertex sub-index added from UV2.x in the shader.
     */
    private static final RenderPipeline PICKER_MODELS = registerPicker(
        "picker_models", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
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
    private static final RenderPipeline PARTICLES = registerParticles(false);

    /**
     * The world copy of the particle pipeline, assigned to the pack's PARTICLES program (see
     * {@link PipelineKey} for why the shared one cannot be). Built lazily on the first world draw
     * under a pack rather than in {@code <clinit>} — the shared fields above initialise in
     * declaration order and nothing here may run before the pipeline cache exists.
     */
    private static RenderPipeline particlesWorld;

    /* ---- billboard (no shading) ----
     * The unlit billboard pipeline: full texture brightness, no directional light, no lightmap —
     * the faithful equivalent of the 1.21.1 no-shading path, which drew through the vanilla
     * GameRenderer::getPositionTexColorProgram. Reuses vanilla's own position_tex_color GLSL, so
     * there is no BBS shader asset to maintain.
     * VertexFormat: POSITION_TEXTURE_COLOR; Samplers: Sampler0 (albedo).
     * Builtin std140 UBOs: DynamicTransforms (ModelViewMat/ColorModulator), Projection.
     * TRIANGLES: BillboardFormRenderer builds two explicit triangles per side, and the item
     * capture path re-emits by the layer's draw mode — declaring TRIANGLES here keeps both the
     * immediate draw and the re-emit in the buffer's native mode. */
    private static final RenderPipeline BILLBOARD = registerBillboard();

    /* Lazily-built render layers (one per pipeline). RenderLayer.of caches nothing itself, so we
     * memoize here to keep a single instance the immediate buffer source can key on.
     *
     * Only the layers something actually draws through live here. The picker_preview / picker_models /
     * picker_billboard_no_shading effects are dispatched by BBSPickerRenderer's manual render pass
     * instead — a RenderLayer binds only the engine builtins and so can never carry the BBSPicker UBO
     * those shaders read (see PICKER_UNIFORM). */
    private static RenderLayer billboardLayer;
    private static RenderLayer pickerBillboardLayer;
    private static RenderLayer pickerParticlesLayer;
    private static RenderLayer particlesLayer;
    private static RenderLayer particlesWorldLayer;

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

    /**
     * Model layers keyed by variant + the texture they sample. Keyed by texture because a layer with no
     * texture leaves Sampler0 to whatever the driver had — which is how models drew blurred even though
     * their textures were NEAREST and GL agreed. (The form editor never showed it: its preview draws
     * through a vanilla entity layer keyed on the adopted texture, which carries that texture's own
     * sampler.) Keyed by variant because pass/depth-write/cull are pipeline state on 1.21.5+.
     */
    private record ModelLayerKey(ModelVariant variant, net.minecraft.util.Identifier texture, boolean world)
    {}

    private static final java.util.Map<ModelLayerKey, RenderLayer> texturedModelLayers = new java.util.HashMap<>();

    /**
     * The model layer for {@code variant}, bound to {@code texture} (null = no texture bound). Inside
     * the world-forms span with a shaderpack on it resolves to the world copy of the pipeline — the
     * one assigned to the pack's entity program (see {@link PipelineKey}); everywhere else, and always
     * without a pack, the shared copy with BBS's own shader.
     */
    public static RenderLayer getModelLayer(ModelVariant variant, net.minecraft.util.Identifier texture)
    {
        return texturedModelLayers.computeIfAbsent(new ModelLayerKey(variant, texture, BBSRendering.isIrisWorldForms()), (key) ->
        {
            RenderSetup.Builder setup = RenderSetup.builder(modelPipeline(key.variant(), key.world()))
                .expectedBufferSize(RenderLayer.field_64008)
                .translucent()
                .useLightmap()
                .useOverlay();

            if (key.texture() != null)
            {
                setup.texture("Sampler0", key.texture());
            }

            return RenderLayer.of(BBSMod.MOD_ID + "_model" + key.variant().suffix() + (key.world() ? "_world" : "")
                + (key.texture() == null ? "" : "_" + key.texture().getPath()), setup.build());
        });
    }

    /**
     * The model layer for {@code variant}, bound to whatever texture the renderers last bound through BBS's
     * own texture manager — which is how the immediate model path has always chosen its texture. Resolving
     * it into a real TextureSetup is what keeps Sampler0 on that texture's own sampler.
     */
    public static RenderLayer getBoundModelLayer(ModelVariant variant)
    {
        mchorse.bbs_mod.graphics.texture.Texture bound = mchorse.bbs_mod.BBSModClient.getTextures().getLastBound();

        return getModelLayer(variant, bound == null ? null : mchorse.bbs_mod.graphics.texture.AdoptedTexture.identifier(bound));
    }

    public static RenderLayer getModelLayer(net.minecraft.util.Identifier texture)
    {
        return getModelLayer(ModelVariant.SINGLE, texture);
    }

    public static RenderLayer getBoundModelLayer()
    {
        return getBoundModelLayer(ModelVariant.SINGLE);
    }

    /**
     * The backface-culled single-pass model layer: for geometry that emits front AND back faces itself and
     * expects the GPU to keep only the one facing the viewer (see MODEL_CULLED).
     */
    public static RenderLayer getBoundCulledModelLayer()
    {
        return getBoundModelLayer(ModelVariant.SINGLE.withCull(true));
    }

    /** Unlit billboard layers keyed by texture, mirroring {@link #getModelLayer(net.minecraft.util.Identifier)}. */
    private static final java.util.Map<net.minecraft.util.Identifier, RenderLayer> texturedBillboardLayers = new java.util.HashMap<>();

    /**
     * The unlit (no-shading) billboard layer bound to the last texture the BBS texture manager bound —
     * same resolution rule as {@link #getBoundModelLayer()}. Full texture brightness: vanilla
     * position_tex_color applies neither directional light nor the lightmap, which is exactly how the
     * 1.21.1 no-shading billboard drew.
     */
    public static RenderLayer getBoundBillboardLayer()
    {
        mchorse.bbs_mod.graphics.texture.Texture bound = mchorse.bbs_mod.BBSModClient.getTextures().getLastBound();
        net.minecraft.util.Identifier id = bound == null ? null : mchorse.bbs_mod.graphics.texture.AdoptedTexture.identifier(bound);

        if (id == null)
        {
            if (billboardLayer == null)
            {
                billboardLayer = RenderLayer.of(BBSMod.MOD_ID + "_billboard", RenderSetup.builder(BILLBOARD)
                    .expectedBufferSize(RenderLayer.field_64008)
                    .translucent()
                    .build());
            }

            return billboardLayer;
        }

        return texturedBillboardLayers.computeIfAbsent(id, (key) -> RenderLayer.of(
            BBSMod.MOD_ID + "_billboard_" + key.getPath(),
            RenderSetup.builder(BILLBOARD)
                .expectedBufferSize(RenderLayer.field_64008)
                .translucent()
                .texture("Sampler0", key)
                .build()));
    }

    /** The untextured single-pass model layer (Sampler0 left to the driver — see {@link #getModelLayer(ModelVariant, net.minecraft.util.Identifier)}). */
    public static RenderLayer getModelLayer()
    {
        return getModelLayer(ModelVariant.SINGLE, null);
    }

    public static RenderLayer getPickerBillboardLayer()
    {
        if (pickerBillboardLayer == null)
        {
            pickerBillboardLayer = layer("picker_billboard", PICKER_BILLBOARD, true);
        }

        return pickerBillboardLayer;
    }

    public static RenderLayer getPickerParticlesLayer()
    {
        if (pickerParticlesLayer == null)
        {
            pickerParticlesLayer = layer("picker_particles", PICKER_PARTICLES, false);
        }

        return pickerParticlesLayer;
    }

    /**
     * The normal (non-picking) particle layer. Built with only useLightmap() (Sampler2) — the
     * POSITION_TEXTURE_COLOR_LIGHT format has no overlay (UV1), so unlike {@link #layer} it must NOT
     * call useOverlay(). Sampler0 (the per-emitter texture) is fed via the global texture binding
     * ParticleEmitter.render performs before the draw, same as BillboardFormRenderer.
     */
    public static RenderLayer getParticlesLayer()
    {
        if (BBSRendering.isIrisWorldForms())
        {
            if (particlesWorldLayer == null)
            {
                if (particlesWorld == null)
                {
                    particlesWorld = registerParticles(true);
                }

                particlesWorldLayer = RenderLayer.of(BBSMod.MOD_ID + "_particles_world", RenderSetup.builder(particlesWorld)
                    .expectedBufferSize(RenderLayer.field_64008)
                    .translucent()
                    .useLightmap()
                    .build());
            }

            return particlesWorldLayer;
        }

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
     * Build and register the model pipeline. It declares
     * the four builtin std140 UBO blocks the migrated {@code bbs:core/model} GLSL imports
     * (light.glsl / fog.glsl / dynamictransforms.glsl / projection.glsl). Declared in the same order
     * the vanilla entity pipeline uses (DynamicTransforms, Projection, Fog, Lighting) so the engine
     * binds them; without these the model shader fails to link and every world draw is a no-op.
     *
     * <p>One pipeline per {@link ModelVariant}: PASS_MODE rides in as a shader define (the 1.21.1 loose
     * uniform is gone), and depth-write/cull are pipeline state now instead of GL toggles flipped around
     * the draw. Registered on first request and memoized — the engine compiles the GLSL lazily, so a
     * variant nothing asks for costs nothing.
     */
    private static RenderPipeline modelPipeline(ModelVariant variant, boolean world)
    {
        PipelineKey key = new PipelineKey(variant, world);
        RenderPipeline existing = modelPipelines.get(key);

        if (existing != null)
        {
            return existing;
        }

        Identifier shader = Identifier.of(BBSMod.MOD_ID, "core/model");

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/model" + variant.suffix() + (world ? "_world" : "")))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(variant.depthWrite())
            .withCull(variant.cull())
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2");

        if (variant.pass() != FormTranslucentQueue.PASS_SINGLE)
        {
            builder.withShaderDefine("PASS_MODE", variant.pass());
        }

        RenderPipeline pipeline = RenderPipelines.register(builder.build());

        if (world)
        {
            /* The model pipeline IS a vanilla entity pipeline with BBS's shader in it — same vertex
             * format, same blend, same depth rules — so a shaderpack should treat it as one, and the
             * honest way to say that is to point at the vanilla pipeline it clones rather than to name a
             * program family. Naming one is what gave forms the ENTITIES_ALPHA program, whose alpha test
             * reads vertex alpha as a discard threshold and deleted every fully opaque form under a pack.
             * Mirroring also brings the shadow-pass assignment (forms cast shadows again) and Iris's own
             * per-phase choice (the hand renderer gets a hand program). See BBSRendering#mirrorIrisPipeline. */
            BBSRendering.mirrorIrisPipeline(pipeline, modelPrototype(variant));
        }

        modelPipelines.put(key, pipeline);

        return pipeline;
    }

    /**
     * The vanilla pipeline a model variant is the BBS-shaded twin of — what a shaderpack should mirror
     * (see {@link BBSRendering#mirrorIrisPipeline}).
     *
     * <p>Cutout, not solid, is the right twin for the ordinary passes: BBS models sample textures with
     * see-through texels and rely on them being dropped, which is exactly what vanilla's cutout entity
     * pipelines mean. Cull follows the variant, since vanilla keeps a culled and an unculled cutout
     * pipeline for the same reason BBS does.
     */
    private static RenderPipeline modelPrototype(ModelVariant variant)
    {
        if (variant.pass() == FormTranslucentQueue.PASS_TRANSLUCENT)
        {
            return RenderPipelines.ENTITY_TRANSLUCENT;
        }

        return variant.cull() ? RenderPipelines.ENTITY_CUTOUT : RenderPipelines.ENTITY_CUTOUT_NO_CULL;
    }

    /**
     * Build and register the unlit billboard pipeline on vanilla's own {@code position_tex_color}
     * shader (see the BILLBOARD field note). Cull ON, like the 1.21.1 draw: the billboard emits the
     * quad as an explicit front/back face pair with opposite winding, so culling keeps exactly one
     * of them — the one facing the viewer. Negative form scales flip both windings together, which
     * only swaps which of the two survives, never drops both. Without culling the two coplanar
     * faces stack, and a semi-transparent texture is blended over itself.
     */
    private static RenderPipeline registerBillboard()
    {
        Identifier shader = Identifier.of("minecraft", "core/position_tex_color");

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/billboard"))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(true)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0");

        RenderPipeline pipeline = RenderPipelines.register(builder.build());


        return pipeline;
    }

    /**
     * Build and register the particles pipeline. Like {@link #modelPipeline} it declares the builtin
     * std140 UBOs the migrated {@code bbs:core/particles} GLSL imports (fog / dynamictransforms /
     * projection), but no Lighting block (particles are not directionally lit) and the
     * POSITION_TEXTURE_COLOR_LIGHT format the emitter builds. Sampler0 = albedo, Sampler2 = lightmap.
     */
    private static RenderPipeline registerParticles(boolean world)
    {
        Identifier shader = Identifier.of(BBSMod.MOD_ID, "core/particles");

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/particles" + (world ? "_world" : "")))
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

        RenderPipeline pipeline = RenderPipelines.register(builder.build());

        if (world)
        {
            BBSRendering.assignIrisPipeline(pipeline, BBSRendering.IrisProgramKind.PARTICLE);
        }

        return pipeline;
    }

    /**
     * Build and register the multilink pipeline (texture-picker pixelate/erase preview). Declares the
     * builtin std140 UBOs the migrated {@code bbs:core/multilink} GLSL imports (DynamicTransforms for
     * ModelViewMat + ColorModulator, Projection for ProjMat) plus the custom std140 {@code MultilinkInfo}
     * block carrying the BBS-specific Filters(vec4)/Size(vec2) that the 1.21.1 shader set as loose
     * uniforms. No Fog/Lighting — it is a 2D UI shader.
     *
     * TODO(1.21.11 render): the per-draw MultilinkInfo UBO (Filters/Size) still needs to be uploaded by
     * the caller when this pipeline is actually dispatched — the multilink editor currently routes
     * through the deprecated Batcher2D.texturedBox bridge which ignores the pipeline, so the values are
     * not yet supplied. Wire the UBO when the multilink preview is re-routed onto this layer.
     */
    private static RenderPipeline registerMultilink()
    {
        Identifier shader = Identifier.of(BBSMod.MOD_ID, "core/multilink");

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/multilink"))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("MultilinkInfo", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler3");

        return RenderPipelines.register(builder.build());
    }

    /**
     * Build and register the subtitles pipeline (blurred subtitle text). Declares the builtin std140
     * UBOs the migrated {@code bbs:core/subtitles} GLSL imports (DynamicTransforms for ModelViewMat,
     * Projection for ProjMat) plus the custom std140 {@code SubtitlesInfo} block carrying the
     * BBS-specific Blur(vec2)/TextureSize(vec2) that the 1.21.1 shader set as loose uniforms. No
     * ColorModulator/Fog/Lighting — it is a 2D UI shader that modulates by vertexColor only.
     *
     * TODO(1.21.11 render): the per-draw SubtitlesInfo UBO (Blur/TextureSize) still needs to be uploaded
     * by the caller when this pipeline is actually dispatched — the subtitle renderer currently routes
     * through the deprecated Batcher2D.texturedBox bridge which ignores the pipeline, so the values are
     * not yet supplied. Wire the UBO when the subtitle blur is re-routed onto this layer.
     */
    private static RenderPipeline registerSubtitles()
    {
        Identifier shader = Identifier.of(BBSMod.MOD_ID, "core/subtitles");

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/subtitles"))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("SubtitlesInfo", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0");

        return RenderPipelines.register(builder.build());
    }

    /**
     * Build and register a picker RenderPipeline. The migrated {@code bbs:core/picker_*} GLSL imports
     * the builtin DynamicTransforms (ModelViewMat/ColorModulator) and Projection (ProjMat) std140 UBOs
     * and declares one custom std140 block, {@link #PICKER_UNIFORM} (HighlightColor vec4, Target int),
     * uploaded per draw. Only Sampler0 is used (picking never samples the lightmap). The original
     * 1.21.1 picker programs declared a Sampler2 they never read; it is dropped here.
     */
    private static RenderPipeline registerPicker(String name, VertexFormat format)
    {
        Identifier shader = Identifier.of(BBSMod.MOD_ID, "core/" + name);

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/" + name))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexFormat(format, VertexFormat.DrawMode.QUADS)
            /* Blend MUST be off for every picker pipeline. The geometry pickers encode an object index in
             * the exact vertex colour, and a blended pixel is a corrupt id. picker_preview writes the
             * highlight colour into an off-screen target that is later composited by the caller's blit:
             * blending it against the transparent-black clear premultiplied it, so the blit multiplied by
             * alpha a SECOND time and any highlight below full opacity came out dark (it looked right only
             * at alpha 1, where the square is a no-op). */
            .withoutBlend()
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform(PICKER_UNIFORM, UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0");

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
