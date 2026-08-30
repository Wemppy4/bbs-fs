package mchorse.bbs_mod.cubic;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.cubic.render.CubicMatrixRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.model.ModelSetupQueue;
import mchorse.bbs_mod.cubic.render.CubicVAOBuilderRenderer;
import mchorse.bbs_mod.cubic.render.CubicVAORenderer;
import mchorse.bbs_mod.cubic.render.WeldGeometryCache;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.cubic.weld.ModelWeld;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormOverlay;
import mchorse.bbs_mod.forms.renderers.utils.FormPbr;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModelInstance implements IModelInstance
{
    public final String id;
    public IModel model;
    public Animations animations;

    /* The channels token: which (form, entity, transition, frame, pose version) the asset's
     * pose currently holds. The instance is one globally cached asset per model id, so the
     * token must live HERE — two forms sharing a model overwrite each other's pose, and each
     * write re-stamps it. See ModelFormRenderer#evaluateChannels. */
    private Object channelsForm;
    private Object channelsEntity;
    private float channelsTransition;
    private long channelsEpoch;
    private int channelsPoseVersion;
    private boolean channelsValid;

    public boolean matchesChannels(Object form, Object entity, float transition, long epoch, int poseVersion)
    {
        return this.channelsValid
            && this.channelsForm == form
            && this.channelsEntity == entity
            && Float.compare(this.channelsTransition, transition) == 0
            && this.channelsEpoch == epoch
            && this.channelsPoseVersion == poseVersion;
    }

    public void stampChannels(Object form, Object entity, float transition, long epoch, int poseVersion)
    {
        this.channelsForm = form;
        this.channelsEntity = entity;
        this.channelsTransition = transition;
        this.channelsEpoch = epoch;
        this.channelsPoseVersion = poseVersion;
        this.channelsValid = true;
    }

    public void clearChannels()
    {
        this.channelsValid = false;
        this.channelsForm = null;
        this.channelsEntity = null;
    }

    /** The model's intrinsic texture from its loader; {@link ModelConfig#texture} overrides it when set. */
    public Link baseTexture;

    /**
     * Per-material default textures, loaded from the model's {@code textures/<material>/}
     * folders (or synthesized as a 1x1 swatch for flat-color materials). Keyed by material
     * name; the empty key is the model's default texture. Used as the static fallback for a
     * material when no animation track overrides it - see {@link #getMaterialTexture}.
     */
    public Map<String, Link> materialTextures = new HashMap<>();

    /** Ordered, distinct list of material names present on the model (for the editor and resolution). */
    public List<String> materials = new ArrayList<>();

    /** The model's {@code config.json} as an editable value tree; the instance reads every setting from here. */
    public final ModelConfig config;

    /** Welds resolved against the model (groups/cubes/corners). Built lazily on first render, kept across frames. */
    private List<WeldBinding> weldBindings;

    /** Every group that takes part in a weld, derived from the bindings once. */
    private Set<ModelGroup> weldedGroups;

    /** The baked CPU half of a welded model, keyed by pose — see {@link WeldGeometryCache}. */
    private final WeldGeometryCache weldCache = new WeldGeometryCache();

    /** The frame the seams and the CPU bake are computed in: the model's root, camera-independent. */
    private static final MatrixStack ROOT = new MatrixStack();

    /** Whether the VAO bake skipped some groups (shape-keyed meshes) — those render immediate via the hybrid path. */
    private boolean partialVaos;

    /** Per group, the geometry split into one VAO per material name (empty key = default texture). */
    private Map<ModelGroup, Map<String, ModelVAO>> vaos = new HashMap<>();

    public transient Matrix4f lastBaseTransform;
    public transient Form form;

    public ModelInstance(String id, IModel model, Animations animations, Link texture)
    {
        this.id = id;
        this.model = model;
        this.animations = animations;
        this.baseTexture = texture;
        this.config = new ModelConfig(id);
    }

    @Override
    public IModel getModel()
    {
        return this.model;
    }

    @Override
    public Pose getSneakingPose()
    {
        return this.config.getSneakingPose();
    }

    @Override
    public Animations getAnimations()
    {
        return this.animations;
    }

    public Map<ModelGroup, Map<String, ModelVAO>> getVaos()
    {
        return this.vaos;
    }

    /** Welds resolved against this model, built once. Empty when the model declares none or isn't cubic. */
    public List<WeldBinding> getWeldBindings()
    {
        if (this.weldBindings == null)
        {
            this.weldBindings = new ArrayList<>();
            this.weldedGroups = new HashSet<>();

            if (this.model instanceof Model model)
            {
                for (ModelWeld weld : this.config.getWelds())
                {
                    WeldBinding binding = WeldBinding.resolve(model, weld);

                    if (binding != null)
                    {
                        this.weldBindings.add(binding);
                        this.weldedGroups.add(binding.sourceGroup);
                        this.weldedGroups.add(binding.targetGroup);
                    }
                }
            }
        }

        return this.weldBindings;
    }

    /**
     * Re-resolve welds after the config's weld list was edited: drop the cached bindings (rebuilt on the
     * next render) and refresh the config's derived caches so the new welds take effect.
     */
    public void invalidateWelds()
    {
        this.weldBindings = null;
        this.weldedGroups = null;
        this.weldCache.invalidate();
        this.config.rebuild();
    }

    /**
     * Resolve a material's static default texture: the per-material texture loaded
     * from {@code textures/<material>/} if present, otherwise the supplied fallback
     * (the form/model default texture). Animation tracks layer on top of this at
     * render time (handled by the caller), so this only covers the non-animated default.
     */
    public Link getMaterialTexture(String material, Link fallback)
    {
        Link link = this.materialTextures.get(material);

        return link != null ? link : fallback;
    }

    public String getAnchor()
    {
        String anchor = this.model.getAnchor();
        String anchorGroup = this.config.anchor.get();

        if (anchorGroup.isEmpty() && !anchor.isEmpty())
        {
            return anchor;
        }

        return anchorGroup;
    }

    public void applyConfig(MapType data)
    {
        if (data == null)
        {
            return;
        }

        this.config.fromData(data);
    }

    /* Config accessors — the instance reads all of these from {@link #config}. */

    public Link getTexture()
    {
        Link texture = this.config.getTexture();

        return texture != null ? texture : this.baseTexture;
    }

    public Vector3f getScale()
    {
        return this.config.scale.get();
    }

    public float getUiScale()
    {
        return this.config.uiScale.get();
    }

    public boolean isProcedural()
    {
        return this.config.procedural.get();
    }

    public boolean isCulling()
    {
        return this.config.culling.get();
    }

    public String getPoseGroup()
    {
        String group = this.config.poseGroup.get();

        return group.isEmpty() ? this.id : group;
    }

    public View getView()
    {
        return this.config.getView();
    }

    public Set<String> getDisabledBones()
    {
        return this.config.disabledBones.get();
    }

    public Map<String, String> getFlippedParts()
    {
        return this.config.getFlippedParts();
    }

    public Map<ArmorType, ArmorSlot> getArmorSlots()
    {
        return this.config.getArmorSlots();
    }

    public List<ArmorSlot> getItemsMain()
    {
        return this.config.getItemsMain();
    }

    public List<ArmorSlot> getItemsOff()
    {
        return this.config.getItemsOff();
    }

    public ArmorSlot getFpMain()
    {
        return this.config.getFpMain();
    }

    public ArmorSlot getFpOffhand()
    {
        return this.config.getFpOffhand();
    }

    public void setup()
    {
        if (this.model instanceof BOBJModel bobjModel)
        {
            ModelSetupQueue.add(bobjModel::setup);
        }

        /* A welded or shape-keyed model still builds VAOs: only its welded bones and shape-keyed groups render
         * on the immediate (CPU) path, the rest ride their VAOs on the GPU (see {@link #renderHybrid}). */
        if (this.model instanceof Model cubicModel)
        {
            boolean bake = !this.config.onCpu.get();

            this.partialVaos = bake && this.hasShapeKeyedGroups(cubicModel);

            if (bake)
            {
                ModelSetupQueue.add(() -> CubicRenderer.processRenderModel(new CubicVAOBuilderRenderer(this.vaos), null, new MatrixStack(), cubicModel));
            }
        }
    }

    /** Whether some group carries shape-keyed meshes — the VAO builder skips those, so the render is hybrid. */
    private boolean hasShapeKeyedGroups(Model model)
    {
        for (ModelGroup group : model.getAllGroups())
        {
            for (ModelMesh mesh : group.meshes)
            {
                if (!mesh.data.isEmpty())
                {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isVAORendered()
    {
        /* A welded or shape-keyed model builds VAOs too, but renders through the hybrid path — external
         * callers (shader choice, etc.) must still treat it as non-VAO, so report false for those. */
        if (!this.getWeldBindings().isEmpty() || this.partialVaos)
        {
            return false;
        }

        return !this.vaos.isEmpty() || this.model instanceof BOBJModel;
    }

    public void delete()
    {
        for (Map<String, ModelVAO> groupVaos : this.vaos.values())
        {
            for (ModelVAO value : groupVaos.values())
            {
                value.delete();
            }
        }

        this.vaos.clear();
        this.weldCache.delete();
    }

    /* Rendering */

    public void fillStencilMap(StencilMap stencilMap, ModelForm form)
    {
        for (RigBone bone : this.model.getRigBones())
        {
            stencilMap.addPicking(form, this.getPickingBone(bone.getBoneName()));
        }
    }

    /**
     * Resolve a bone's name for stencil picking, redirecting it to another bone when the model's
     * config declares an override (e.g. clicking "torso" selecting "low_body" instead). The bone's
     * own geometry still owns the stencil index — only the bone the click resolves to changes.
     */
    private String getPickingBone(String bone)
    {
        return this.config.getPickingOverrides().getOrDefault(bone, bone);
    }

    public void captureMatrices(MatrixCache bones)
    {
        BBSProfiler.count(BBSProfiler.Section.CAPTURE_MATRICES);

        if (this.model instanceof Model model)
        {
            MatrixStack stack = new MatrixStack();
            CubicMatrixRenderer renderer = new CubicMatrixRenderer(model);

            CubicRenderer.processRenderModel(renderer, null, stack, model);

            for (ModelGroup group : model.getAllGroups())
            {
                Matrix4f matrix = new Matrix4f(renderer.matrices.get(group.index));
                Matrix4f origin = new Matrix4f(renderer.origins.get(group.index));

                matrix.translate(
                    group.initial.translate.x / 16,
                    group.initial.translate.y / 16,
                    group.initial.translate.z / 16
                );
                matrix.rotateY(MathUtils.PI);
                origin.translate(
                    group.initial.translate.x / 16,
                    group.initial.translate.y / 16,
                    group.initial.translate.z / 16
                );
                origin.rotateY(MathUtils.PI);

                bones.put(group.id, matrix, origin, evaluatedChannelRotation(group.current, group.orient, true));
            }
        }
        else if (this.model instanceof BOBJModel model)
        {
            model.getArmature().setupMatrices();

            for (BOBJBone orderedBone : model.getArmature().orderedBones)
            {
                Matrix4f matrix = new Matrix4f();
                Matrix4f origin = new Matrix4f();

                matrix.rotateY(MathUtils.PI).mul(orderedBone.mat);
                origin.rotateY(MathUtils.PI).mul(orderedBone.originMat);

                bones.put(orderedBone.name, matrix, origin, evaluatedChannelRotation(orderedBone.transform, orderedBone.orient, false));
            }
        }
    }

    /**
     * The bone's EVALUATED channel rotation (ZYX euler radians) for the gizmo's
     * additive overlay-editing base, or {@code null} when the render doesn't
     * follow the channels additively: quaternion mode composes multiplicatively,
     * and a composed {@code orient} counts only while it still EQUALS the
     * channel rotation — the first composed layer seeds it FROM the channels
     * (identical by construction), but stacked layers multiply and diverge,
     * and then the additive base model doesn't apply.
     */
    private static Vector3f evaluatedChannelRotation(Transform current, Quaternionf orient, boolean degrees)
    {
        if (current.rotationMode == Transform.RotationMode.QUATERNION)
        {
            return null;
        }

        Vector3f radians = degrees
            ? new Vector3f(
                MathUtils.toRad(current.rotate.x),
                MathUtils.toRad(current.rotate.y),
                MathUtils.toRad(current.rotate.z)
            )
            : new Vector3f(current.rotate);

        if (orient != null)
        {
            Quaternionf channels = Matrices.toQuaternionZYXRadians(radians.x, radians.y, radians.z);

            /* |dot| = cos(θ/2) between the two rotations (double cover); anything
             * under ~1.6° apart means a genuinely multiplicative stack. */
            if (Math.abs(channels.dot(orient)) < 0.9999F)
            {
                return null;
            }
        }

        return radians;
    }

    /**
     * First weld pass: capture the rigid world corners of every welded face with no drawing, then build the seams.
     * Runs a dedicated capture-only renderer that only touches welded cubes (and only their welded face's corners),
     * so it's a light matrix walk over the tree rather than a full per-vertex pass.
     */
    private void captureWelds(List<WeldBinding> bindings, MatrixStack stack, Model model, int light, int overlay, StencilMap stencilMap, ShapeKeys keys)
    {
        for (WeldBinding binding : bindings)
        {
            for (WeldBinding.Layer layer : binding.layers)
            {
                layer.resetCapture();
            }
        }

        CubicCubeRenderer capture = new CubicCubeRenderer(light, overlay, stencilMap, keys);

        capture.setWelds(bindings);
        capture.setCaptureOnly(true);
        CubicRenderer.processRenderModel(capture, null, stack, model);

        for (WeldBinding binding : bindings)
        {
            for (WeldBinding.Layer layer : binding.layers)
            {
                layer.computeSeam();
            }
        }
    }

    /**
     * Hybrid render: bones with baked VAOs ride the GPU; only actively-bending welded bones and groups
     * with no VAO (shape-keyed meshes, or none baked yet) go through the immediate CPU path, where their
     * cubes deform against the seam or morph. A light capture pass fills the seams first — for picking
     * too, so the stencil matches the deformed geometry.
     */
    private void renderHybrid(MatrixStack stack, ShaderProgram shader, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Link> textureResolver, Model model, List<WeldBinding> bindings)
    {
        Set<ModelGroup> weldedGroups = this.weldedGroups;

        /* The welded cubes draw from a CPU bake, so — outside picking and the Iris pipeline, which run their own
         * shader state — they go through the BBS model shader; the VAO bones use the same shader so both halves of
         * the model match. */
        boolean explicitWeld = stencilMap == null && !(BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld());
        ShaderProgram drawShader = explicitWeld ? BBSShaders.getModel() : shader;
        Color cpuOverlay = this.getCpuOverlay(stencilMap, overlay);

        /* The bake is a function of the pose and the draw's own inputs, computed in the ROOT frame: the same
         * buffer serves every pass of the frame and every frame nothing moved in. A miss redoes what used to
         * happen every pass — capture the seams, decide which bones bend, tessellate them. */
        boolean cacheable = RenderFrame.isEnabled();
        long key = cacheable ? this.weldKey(model, keys, light, overlay, color, cpuOverlay != null, stencilMap) : 0L;
        WeldGeometryCache.Entry entry = cacheable ? this.weldCache.find(key) : null;
        Set<ModelGroup> cpuGroups;
        BufferBuilder builder = null;

        if (entry == null)
        {
            BBSProfiler.begin(BBSProfiler.Timer.WELDS);

            /* Capture the seams for the visible draw AND for picking: the stencil must match the deformed
             * geometry, or hovering a bent welded bone highlights its un-sealed rest silhouette at the joint. */
            if (!bindings.isEmpty())
            {
                this.captureWelds(bindings, ROOT, model, light, overlay, stencilMap, keys);
            }

            cpuGroups = this.collectCpuGroups(model, bindings, weldedGroups);

            if (!cpuGroups.isEmpty())
            {
                CubicVAORenderer bake = new CubicVAORenderer(drawShader, this, light, overlay, stencilMap, keys, textureResolver);

                bake.setCpuOverlayActive(cpuOverlay != null);
                bake.setColor(color.r, color.g, color.b, color.a);
                bake.setWelds(bindings);
                bake.setWeldedGroups(weldedGroups);
                bake.setCpuGroups(cpuGroups);
                bake.setHybridPasses(false, true);

                builder = Tessellator.getInstance().getBuffer();
                builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                CubicRenderer.processRenderModel(bake, builder, ROOT, model);
            }

            if (cacheable)
            {
                entry = this.weldCache.acquire(RenderFrame.getEpoch());

                if (entry != null)
                {
                    entry.key = key;
                    entry.valid = true;
                    entry.cpuGroups = cpuGroups;
                    entry.hasGeometry = builder != null;

                    if (builder != null)
                    {
                        entry.vbo.bind();
                        entry.vbo.upload(builder.end());
                        VertexBuffer.unbind();
                        builder = null;
                    }
                }
            }

            BBSProfiler.end(BBSProfiler.Timer.WELDS);
        }
        else
        {
            cpuGroups = entry.cpuGroups;
        }

        /* The VAO bones ride the GPU every pass, in the caller's frame; the CPU set is the bake's, so the two
         * halves always agree on which bones are which. */
        CubicVAORenderer renderProcessor = new CubicVAORenderer(drawShader, this, light, overlay, stencilMap, keys, textureResolver);

        renderProcessor.setCpuOverlayActive(cpuOverlay != null);
        renderProcessor.setColor(color.r, color.g, color.b, color.a);
        renderProcessor.setWelds(bindings);
        renderProcessor.setWeldedGroups(weldedGroups);
        renderProcessor.setCpuGroups(cpuGroups);
        renderProcessor.setHybridPasses(true, false);

        RenderSystem.setShader(() -> drawShader);

        /* The CPU path doesn't switch textures per material — it draws with whatever's bound. The VAO bones rebind
         * per material as they draw, so remember the caller's default texture and restore it for the CPU draw
         * (matches the old all-CPU path, which drew the welded cubes with that same default). */
        int defaultTexture = RenderSystem.getShaderTexture(0);
        Texture defaultTextureObject = BBSModClient.getTextures().getLastBound();

        CubicRenderer.processRenderModel(renderProcessor, null, stack, model);

        boolean hasGeometry = entry != null ? entry.hasGeometry : builder != null;

        if (!hasGeometry)
        {
            return;
        }

        RenderSystem.setShaderTexture(0, defaultTexture);

        /* Root-frame geometry drawn in the caller's frame: the caller's stack goes into the model-view, and its
         * normal matrix takes the baked normals into the same space the VAO path's NormalMat*Normal lands in. */
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.peek().getPositionMatrix());
        Matrix3f normalMat = new Matrix3f(stack.peek().getNormalMatrix());
        GlUniform normalUniform = drawShader.getUniform("NormalMat");

        if (normalUniform != null)
        {
            normalUniform.set(normalMat);
        }

        int previousOverlay = cpuOverlay != null ? FormOverlay.bind(cpuOverlay) : 0;

        this.drawImmediate(builder, entry, drawShader, modelView, normalMat, stencilMap, defaultTextureObject, color.a);

        if (cpuOverlay != null)
        {
            FormOverlay.unbind(previousOverlay);
        }
    }

    /**
     * What the CPU bake depends on: every bone's evaluated transform, colour and lighting (the pose the seams
     * and the deformation come from), the draw's light/overlay/colour, the shape keys, and the picking mode
     * (which bakes stencil ids into the light attribute).
     */
    private long weldKey(Model model, ShapeKeys keys, int light, int overlay, Color color, boolean cpuOverlayActive, StencilMap stencilMap)
    {
        long hash = 1125899906842597L;

        for (ModelGroup group : model.getAllGroups())
        {
            hash = hash * 31 + (group.visible ? 1 : 0);
            hash = hash * 31 + group.current.contentHash();
            hash = hash * 31 + (group.orient == null ? 0 : group.orient.hashCode());
            hash = hash * 31 + (group.offset == null ? 0 : group.offset.hashCode());
            hash = hash * 31 + group.color.getARGBColor();
            hash = hash * 31 + group.overlay.getARGBColor();
            hash = hash * 31 + Float.floatToIntBits(group.lighting);
        }

        hash = hash * 31 + light;
        hash = hash * 31 + overlay;
        hash = hash * 31 + Float.floatToIntBits(color.r);
        hash = hash * 31 + Float.floatToIntBits(color.g);
        hash = hash * 31 + Float.floatToIntBits(color.b);
        hash = hash * 31 + Float.floatToIntBits(color.a);
        hash = hash * 31 + (cpuOverlayActive ? 1 : 0);
        hash = hash * 31 + (stencilMap == null ? 0 : (stencilMap.increment ? 2 : 1));
        hash = hash * 31 + (keys == null ? 0 : keys.shapeKeys.hashCode());

        return hash;
    }

    /**
     * Draw immediate-path geometry (its vertices carry the full camera-space transform baked in)
     * with two-pass translucency when needed: the opaque texels draw now and write depth, the
     * semi-transparent ones replay from a retained vertex buffer when the frame's translucent
     * queue flushes. Single-pass draws keep the old direct path.
     */
    /**
     * Draw the CPU bake — the cached entry's buffer, or a throwaway one built from {@code builder} when the
     * cache is off or full. Two-pass translucency when needed: the opaque texels draw now and write depth,
     * the semi-transparent ones replay from the buffer when the frame's translucent queue flushes; a cached
     * buffer is only lent to the queue (it must not be rebuilt until the flush — see the cache), a throwaway
     * one is handed over and freed by it.
     */
    private void drawImmediate(BufferBuilder builder, WeldGeometryCache.Entry cached, ShaderProgram shader, Matrix4f modelView, Matrix3f normalMat, StencilMap stencilMap, Texture texture, float alpha)
    {
        boolean split = FormTranslucentQueue.needsSplit(shader, stencilMap, texture, alpha);
        boolean whole = !split && FormTranslucentQueue.needsWholeDefer(shader, stencilMap, alpha);
        boolean owned = cached == null;
        VertexBuffer buffer;

        if (owned)
        {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(builder.end());
        }
        else
        {
            buffer = cached.vbo;
            buffer.bind();
        }

        if (!split && !whole)
        {
            buffer.draw(modelView, RenderSystem.getProjectionMatrix(), shader);
            VertexBuffer.unbind();

            if (owned)
            {
                buffer.close();
            }

            return;
        }

        if (split)
        {
            /* Immediate opaque pass: the solid texels draw now and write depth. (The whole-defer
             * case skips this — it replays the entire mesh with depth at flush instead.) */
            FormTranslucentQueue.setPassMode(shader, FormTranslucentQueue.PASS_OPAQUE);
            buffer.draw(modelView, RenderSystem.getProjectionMatrix(), shader);
            FormTranslucentQueue.setPassMode(shader, FormTranslucentQueue.PASS_SINGLE);
        }

        VertexBuffer.unbind();

        if (!owned)
        {
            cached.lentEpoch = RenderFrame.getEpoch();
        }

        Vector3f origin = modelView.transformPosition(new Vector3f());

        if (split)
        {
            /* Depth stays on: this is solid geometry, so its semi-transparent texels must occlude
             * the ones behind them inside the same model — see the split constructors' note. */
            FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(buffer, owned, () -> shader, FormTranslucentQueue.PASS_TRANSLUCENT, true, texture, modelView, normalMat, origin, null, this.isCulling(), null, null));
        }
        else
        {
            /* Uniform colour fade: defer the whole mesh with depth on so it self-occludes. */
            FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(buffer, owned, () -> shader, FormTranslucentQueue.PASS_SINGLE, true, texture, modelView, normalMat, origin, null, this.isCulling(), null, null));
        }
    }

    /**
     * The form-level color overlay for the immediate (CPU) draw, or null when neutral, picking,
     * or during a hurt flash (the flash wins). The CPU buffer batches all bones and materials in
     * one draw, so only the FORM level applies there — per-material and per-bone overlays are a
     * VAO-path feature.
     */
    private Color getCpuOverlay(StencilMap stencilMap, int overlay)
    {
        if (stencilMap != null || overlay != OverlayTexture.DEFAULT_UV)
        {
            return null;
        }

        Color combined = FormOverlay.combine(this.form instanceof ModelForm form ? form : null, null, null);

        return combined == null ? null : combined.copy();
    }

    /** The groups the immediate path tessellates: a visible bending welded bone, or a visible bone with geometry but no VAO. */
    private Set<ModelGroup> collectCpuGroups(Model model, List<WeldBinding> bindings, Set<ModelGroup> weldedGroups)
    {
        Set<ModelGroup> groups = null;

        for (ModelGroup group : model.getAllGroups())
        {
            if (!group.visible || (group.cubes.isEmpty() && group.meshes.isEmpty()))
            {
                continue;
            }

            Map<String, ModelVAO> groupVaos = this.vaos.get(group);

            if ((weldedGroups.contains(group) && WeldBinding.hasActiveSeam(bindings, group)) || groupVaos == null || groupVaos.isEmpty())
            {
                if (groups == null)
                {
                    groups = new HashSet<>();
                }

                groups.add(group);
            }
        }

        return groups == null ? Set.of() : groups;
    }

    public void render(MatrixStack stack, Supplier<ShaderProgram> program, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Link> textureResolver)
    {
        ShaderProgram shader = program.get();

        if (this.model instanceof Model model)
        {
            List<WeldBinding> bindings = this.getWeldBindings();

            /* Welds and partially-baked (shape-keyed) models mix VAO and immediate rendering; a partial
             * model whose VAOs aren't baked yet falls through to the plain CPU path below. */
            if (!bindings.isEmpty() || (this.partialVaos && !this.vaos.isEmpty()))
            {
                this.renderHybrid(stack, shader, color, light, overlay, stencilMap, keys, textureResolver, model, bindings);
            }
            else if (this.isVAORendered())
            {
                CubicVAORenderer renderProcessor = new CubicVAORenderer(shader, this, light, overlay, stencilMap, keys, textureResolver);

                renderProcessor.setColor(color.r, color.g, color.b, color.a);
                CubicRenderer.processRenderModel(renderProcessor, null, stack, model);
            }
            else
            {
                CubicCubeRenderer renderProcessor = new CubicCubeRenderer(light, overlay, stencilMap, keys);
                Color cpuOverlay = this.getCpuOverlay(stencilMap, overlay);

                renderProcessor.setCpuOverlayActive(cpuOverlay != null);
                renderProcessor.setColor(color.r, color.g, color.b, color.a);
                RenderSystem.setShader(() -> shader);

                BufferBuilder builder = Tessellator.getInstance().getBuffer();

                builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                CubicRenderer.processRenderModel(renderProcessor, builder, stack, model);

                int previousOverlay = cpuOverlay != null ? FormOverlay.bind(cpuOverlay) : 0;

                /* Plain CPU path bakes in the caller's frame, so the global model-view applies as is. */
                this.drawImmediate(builder, null, shader, new Matrix4f(RenderSystem.getModelViewMatrix()), null, stencilMap, BBSModClient.getTextures().getLastBound(), color.a);

                if (cpuOverlay != null)
                {
                    FormOverlay.unbind(previousOverlay);
                }
            }
        }
        else if (this.model instanceof BOBJModel model)
        {
            List<BOBJModelVAO> vaos = model.getVaos();

            if (!vaos.isEmpty())
            {
                stack.push();
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180F));

                model.getArmature().setupMatrices();

                /* One key for the whole armature; each mesh compares it against what its VBO holds. */
                long armatureKey = RenderFrame.isEnabled() ? BOBJModelVAO.armatureKey(model.getArmature()) : Long.MIN_VALUE;

                /* One draw per mesh; bind that mesh's resolved texture (mesh name = material). */
                ModelForm modelForm = this.form instanceof ModelForm form ? form : null;
                boolean hurtFlash = overlay != OverlayTexture.DEFAULT_UV;

                for (BOBJModelVAO vao : vaos)
                {
                    Texture texture = null;

                    if (textureResolver != null)
                    {
                        Link link = textureResolver.apply(vao.data.mesh.name);

                        if (link != null)
                        {
                            texture = BBSModClient.getTextures().getTexture(link);
                            texture = FormPbr.resolveAlbedo(modelForm, vao.data.mesh.name, link, texture);
                            BBSModClient.getTextures().bindTexture(texture);
                        }
                    }

                    if (texture == null)
                    {
                        /* No per-mesh override — the draw uses the form's base texture bound earlier. */
                        texture = BBSModClient.getTextures().getLastBound();
                    }

                    vao.updateMesh(stencilMap, armatureKey);

                    /* Form + material overlay per mesh; no bone level here — BOBJ vertices are
                     * skinned across bones, so a per-bone overlay has no per-draw home. */
                    Color overlayColor = stencilMap == null && !hurtFlash
                        ? FormOverlay.combine(modelForm, vao.data.mesh.name, null)
                        : null;
                    int meshOverlay = overlayColor != null ? 0 : overlay;
                    int previousOverlay = overlayColor != null ? FormOverlay.bind(overlayColor) : 0;

                    if (FormTranslucentQueue.needsSplit(shader, stencilMap, texture, color.a))
                    {
                        Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
                        Matrix3f normalMat = new Matrix3f(stack.peek().getNormalMatrix());

                        FormTranslucentQueue.setPassMode(shader, FormTranslucentQueue.PASS_OPAQUE);
                        vao.render(shader, modelView, normalMat, color.r, color.g, color.b, color.a, stencilMap, light, meshOverlay);
                        FormTranslucentQueue.setPassMode(shader, FormTranslucentQueue.PASS_SINGLE);

                        FormTranslucentQueue.add(new FormTranslucentQueue.BOBJCommand(vao, vao.snapshotArmature(), vao.getUploadCount(), texture, modelView, normalMat, color.r, color.g, color.b, color.a, light, meshOverlay, this.isCulling())
                            .overlayColor(overlayColor));
                    }
                    else if (FormTranslucentQueue.needsWholeDefer(shader, stencilMap, color.a))
                    {
                        /* A uniform colour fade defers the whole draw into the sorted end-of-frame
                         * pass with depth kept on, so the faded model still self-occludes. */
                        Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
                        Matrix3f normalMat = new Matrix3f(stack.peek().getNormalMatrix());

                        FormTranslucentQueue.add(new FormTranslucentQueue.BOBJCommand(vao, () -> shader, FormTranslucentQueue.PASS_SINGLE, true, vao.snapshotArmature(), vao.getUploadCount(), texture, modelView, normalMat, color.r, color.g, color.b, color.a, light, meshOverlay, this.isCulling())
                            .overlayColor(overlayColor));
                    }
                    else
                    {
                        vao.render(shader, stack, color.r, color.g, color.b, color.a, stencilMap, light, meshOverlay);
                    }

                    if (overlayColor != null)
                    {
                        FormOverlay.unbind(previousOverlay);
                    }
                }

                stack.pop();
            }
        }
    }
}
