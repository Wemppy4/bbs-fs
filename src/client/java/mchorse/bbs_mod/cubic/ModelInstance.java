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
import mchorse.bbs_mod.cubic.render.CubicVAOBuilderRenderer;
import mchorse.bbs_mod.cubic.render.CubicVAORenderer;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.weld.ModelWeld;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Pose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
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
    /** Identity NormalMat for the welded immediate draw — its normals are already CPU-transformed to world space. */
    private static final Matrix3f WELD_NORMAL_MAT = new Matrix3f();

    public final String id;
    public IModel model;
    public Animations animations;

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

            if (this.model instanceof Model model)
            {
                for (ModelWeld weld : this.config.getWelds())
                {
                    WeldBinding binding = WeldBinding.resolve(model, weld);

                    if (binding != null)
                    {
                        this.weldBindings.add(binding);
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
        if (this.model instanceof BOBJModel model)
        {
            MinecraftClient.getInstance().execute(model::setup);
        }

        /* A welded or shape-keyed model still builds VAOs: only its welded bones and shape-keyed groups render
         * on the immediate (CPU) path, the rest ride their VAOs on the GPU (see {@link #renderHybrid}). */
        if (this.model instanceof Model model)
        {
            boolean bake = !this.config.onCpu.get();

            this.partialVaos = bake && this.hasShapeKeyedGroups(model);

            if (bake)
            {
                MinecraftClient.getInstance().execute(() ->
                {
                    CubicRenderer.processRenderModel(new CubicVAOBuilderRenderer(this.vaos), null, new MatrixStack(), model);
                });
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
    }

    /* Rendering */

    public void fillStencilMap(StencilMap stencilMap, ModelForm form)
    {
        if (this.model instanceof Model model)
        {
            for (ModelGroup group : model.getOrderedGroups())
            {
                stencilMap.addPicking(form, this.getPickingBone(group.id));
            }
        }
        else if (this.model instanceof BOBJModel model)
        {
            for (BOBJBone orderedBone : model.getArmature().orderedBones)
            {
                stencilMap.addPicking(form, this.getPickingBone(orderedBone.name));
            }
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
                bones.put(group.id, matrix, origin);
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
                bones.put(orderedBone.name, matrix, origin);
            }
        }
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
        Set<ModelGroup> weldedGroups = new HashSet<>();

        for (WeldBinding binding : bindings)
        {
            weldedGroups.add(binding.sourceGroup);
            weldedGroups.add(binding.targetGroup);
        }

        /* Capture the seams for the visible draw AND for picking: the stencil must match the deformed geometry, or
         * hovering a bent welded bone highlights its wrong, un-sealed rest silhouette at the joint. */
        if (!bindings.isEmpty())
        {
            this.captureWelds(bindings, stack, model, light, overlay, stencilMap, keys);
        }

        /* The welded cubes draw immediate with world-space corners, so — outside picking and the Iris pipeline, which
         * run their own shader state — they go through the BBS model shader with NormalMat pinned to identity (the
         * normals are already in world space, the same space the VAO path's NormalMat*Normal resolves to; else the
         * first-person hand during video export inherits a foreign NormalMat and darkens). The VAO bones use the same
         * shader so both halves of the model match. */
        boolean explicitWeld = stencilMap == null && !(BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld());
        ShaderProgram drawShader = explicitWeld ? BBSShaders.getModel() : shader;

        CubicVAORenderer renderProcessor = new CubicVAORenderer(drawShader, this, light, overlay, stencilMap, keys, textureResolver);

        renderProcessor.setColor(color.r, color.g, color.b, color.a);
        renderProcessor.setWelds(bindings);
        renderProcessor.setWeldedGroups(weldedGroups);

        RenderSystem.setShader(() -> drawShader);

        /* The CPU path doesn't switch textures per material — it draws with whatever's bound. The VAO bones rebind
         * per material as they draw, so remember the caller's default texture and restore it for the CPU draw
         * (matches the old all-CPU path, which drew the welded cubes with that same default). */
        int defaultTexture = RenderSystem.getShaderTexture(0);

        /* Open the shared CPU buffer only if some group actually renders on the CPU (a visible bending welded
         * bone, or a visible bone with geometry but no VAO) — drawing an empty buffer would fail. */
        boolean cpuGeometry = this.hasCpuGeometry(model, bindings, weldedGroups);
        BufferBuilder builder = null;

        if (cpuGeometry)
        {
            builder = Tessellator.getInstance().getBuffer();
            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        }

        CubicRenderer.processRenderModel(renderProcessor, builder, stack, model);

        if (cpuGeometry)
        {
            RenderSystem.setShaderTexture(0, defaultTexture);

            if (explicitWeld)
            {
                GlUniform normalMat = drawShader.getUniform("NormalMat");

                if (normalMat != null)
                {
                    normalMat.set(WELD_NORMAL_MAT);
                }
            }

            BufferRenderer.drawWithGlobalProgram(builder.end());
        }
    }

    /** Whether the immediate path will emit anything: a visible bending welded bone, or a visible bone with geometry but no VAO. */
    private boolean hasCpuGeometry(Model model, List<WeldBinding> bindings, Set<ModelGroup> weldedGroups)
    {
        for (ModelGroup group : model.getAllGroups())
        {
            if (!group.visible || (group.cubes.isEmpty() && group.meshes.isEmpty()))
            {
                continue;
            }

            Map<String, ModelVAO> groupVaos = this.vaos.get(group);

            if ((weldedGroups.contains(group) && WeldBinding.hasActiveSeam(bindings, group)) || groupVaos == null || groupVaos.isEmpty())
            {
                return true;
            }
        }

        return false;
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

                renderProcessor.setColor(color.r, color.g, color.b, color.a);
                RenderSystem.setShader(() -> shader);

                BufferBuilder builder = Tessellator.getInstance().getBuffer();

                builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                CubicRenderer.processRenderModel(renderProcessor, builder, stack, model);
                BufferRenderer.drawWithGlobalProgram(builder.end());
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

                /* One draw per mesh; bind that mesh's resolved texture (mesh name = material). */
                for (BOBJModelVAO vao : vaos)
                {
                    if (textureResolver != null)
                    {
                        Link link = textureResolver.apply(vao.data.mesh.name);

                        if (link != null)
                        {
                            BBSModClient.getTextures().bindTexture(link);
                        }
                    }

                    vao.updateMesh(stencilMap);
                    vao.render(shader, stack, color.r, color.g, color.b, color.a, stencilMap, light, overlay);
                }

                stack.pop();
            }
        }
    }
}
