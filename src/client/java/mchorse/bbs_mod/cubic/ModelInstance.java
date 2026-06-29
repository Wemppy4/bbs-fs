package mchorse.bbs_mod.cubic;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.cubic.render.CubicMatrixRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.CubicVAOBuilderRenderer;
import mchorse.bbs_mod.cubic.render.CubicVAORenderer;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.weld.ModelWeld;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
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
import mchorse.bbs_mod.utils.resources.LinkUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModelInstance implements IModelInstance
{
    public final String id;
    public IModel model;
    public Animations animations;
    public Link texture;

    /**
     * Per-material default textures, loaded from the model's {@code textures/<material>/}
     * folders (or synthesized as a 1x1 swatch for flat-color materials). Keyed by material
     * name; the empty key is the model's default texture. Used as the static fallback for a
     * material when no animation track overrides it - see {@link #getMaterialTexture}.
     */
    public Map<String, Link> materialTextures = new HashMap<>();

    /** Ordered, distinct list of material names present on the model (for the editor and resolution). */
    public List<String> materials = new ArrayList<>();

    /* Model's additional properties */
    public String poseGroup;
    public boolean procedural;
    public boolean culling = true;
    public boolean onCpu;
    public String anchorGroup = "";

    public View view;

    public Vector3f scale = new Vector3f(1F);
    public float uiScale = 1F;
    public Pose sneakingPose = new Pose();

    public List<ArmorSlot> itemsMain = new ArrayList<>();
    public List<ArmorSlot> itemsOff = new ArrayList<>();
    public List<String> disabledBones = new ArrayList<>();
    public Map<String, String> flippedParts = new HashMap<>();
    public Map<String, String> pickingOverrides = new HashMap<>();
    public Map<ArmorType, ArmorSlot> armorSlots = new HashMap<>();

    /** Welds declared in the model's config — glue a bone's face onto another's to seal a bending joint. */
    public List<ModelWeld> welds = new ArrayList<>();

    /** Welds resolved against the model (groups/cubes/corners). Built lazily on first render, kept across frames. */
    private List<WeldBinding> weldBindings;

    public ArmorSlot fpMain;
    public ArmorSlot fpOffhand;

    /** Per group, the geometry split into one VAO per material name (empty key = default texture). */
    private Map<ModelGroup, Map<String, ModelVAO>> vaos = new HashMap<>();

    public transient Matrix4f lastBaseTransform;
    public transient Form form;

    public ModelInstance(String id, IModel model, Animations animations, Link texture)
    {
        this.id = id;
        this.model = model;
        this.animations = animations;
        this.texture = texture;

        this.poseGroup = id;
    }

    @Override
    public IModel getModel()
    {
        return this.model;
    }

    @Override
    public Pose getSneakingPose()
    {
        return this.sneakingPose;
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
                for (ModelWeld weld : this.welds)
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

        if (this.anchorGroup.isEmpty() && !anchor.isEmpty())
        {
            return anchor;
        }

        return this.anchorGroup;
    }

    public void applyConfig(MapType config)
    {
        if (config == null)
        {
            return;
        }

        this.procedural = config.getBool("procedural", this.procedural);
        this.culling = config.getBool("culling", this.culling);
        this.onCpu = config.getBool("on_cpu", this.onCpu);
        this.poseGroup = config.getString("pose_group", this.poseGroup);

        if (config.has("texture"))
        {
            this.texture = LinkUtils.create(config.get("texture"));
        }
        if (config.has("items_main"))
        {
            ListType list = config.get("items_main").asList();

            for (BaseType type : list)
            {
                ArmorSlot slot = new ArmorSlot();

                slot.fromData(type);
                this.itemsMain.add(slot);
            }
        }
        if (config.has("items_off"))
        {
            ListType list = config.get("items_off").asList();

            for (BaseType type : list)
            {
                ArmorSlot slot = new ArmorSlot();

                slot.fromData(type);
                this.itemsOff.add(slot);
            }
        }
        if (config.has("ui_scale")) this.uiScale = config.getFloat("ui_scale");
        if (config.has("scale")) this.scale = DataStorageUtils.vector3fFromData(config.getList("scale"), new Vector3f(1F));
        if (config.has("sneaking_pose", BaseType.TYPE_MAP))
        {
            this.sneakingPose = new Pose();
            this.sneakingPose.fromData(config.getMap("sneaking_pose"));
        }
        if (config.has("anchor")) this.anchorGroup = config.getString("anchor");
        if (config.has("disabledBones"))
        {
            ListType list = config.getList("disabledBones");

            for (BaseType type : list)
            {
                this.disabledBones.add(type.asString());
            }
        }
        if (config.has("flipped_parts"))
        {
            MapType map = config.getMap("flipped_parts");

            for (String key : map.keys())
            {
                String string = map.getString(key);

                if (!string.trim().isEmpty())
                {
                    this.flippedParts.put(key, string);
                }
            }
        }
        if (config.has("picking_overrides"))
        {
            MapType map = config.getMap("picking_overrides");

            for (String key : map.keys())
            {
                String string = map.getString(key);

                if (!string.trim().isEmpty())
                {
                    this.pickingOverrides.put(key, string);
                }
            }
        }
        if (config.has("armor_slots"))
        {
            MapType map = config.getMap("armor_slots");

            for (String key : map.keys())
            {
                try
                {
                    ArmorType type = ArmorType.valueOf(key.toUpperCase());
                    ArmorSlot slot = new ArmorSlot();

                    slot.fromData(map.getMap(key));
                    this.armorSlots.put(type, slot);
                }
                catch (Exception e)
                {}
            }
        }
        if (config.has("fp_main"))
        {
            this.fpMain = new ArmorSlot();
            this.fpMain.fromData(config.get("fp_main"));
        }
        if (config.has("fp_offhand"))
        {
            this.fpOffhand = new ArmorSlot();
            this.fpOffhand.fromData(config.get("fp_offhand"));
        }

        /* Optional look-at configuration */
        if (config.has("look_at", BaseType.TYPE_MAP))
        {
            this.view = new View();

            this.view.fromData(config.getMap("look_at"));
        }

        if (config.has("welds"))
        {
            for (BaseType type : config.getList("welds"))
            {
                MapType weld = (MapType) type;

                this.welds.add(new ModelWeld(
                    weld.getString("source_bone"),
                    weld.getString("source_face"),
                    weld.getString("target_bone"),
                    weld.getString("target_face"),
                    weld.has("max_angle") ? weld.getFloat("max_angle") : 120F
                ));
            }
        }
    }

    public void setup()
    {
        if (this.model instanceof BOBJModel model)
        {
            MinecraftClient.getInstance().execute(model::setup);
        }

        /* VAOs should be only generated if there are no shape keys */
        if (!this.model.getShapeKeys().isEmpty())
        {
            return;
        }

        /* Welded cubes deform per-vertex against another bone's live transform, which baked VAOs can't do,
         * so a welded model stays on the immediate (CPU) render path. */
        if (!this.welds.isEmpty())
        {
            return;
        }

        if (this.model instanceof Model model && !this.onCpu)
        {
            MinecraftClient.getInstance().execute(() ->
            {
                CubicRenderer.processRenderModel(new CubicVAOBuilderRenderer(this.vaos), null, new MatrixStack(), model);
            });
        }
    }

    public boolean isVAORendered()
    {
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
        return this.pickingOverrides.getOrDefault(bone, bone);
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

    /** First weld pass: capture the rigid world corners of every welded face with no drawing, then build the seams. */
    private void captureWelds(CubicCubeRenderer renderProcessor, MatrixStack stack, Model model)
    {
        List<WeldBinding> bindings = this.getWeldBindings();

        for (WeldBinding binding : bindings)
        {
            for (WeldBinding.Layer layer : binding.layers)
            {
                layer.resetCapture();
            }
        }

        renderProcessor.setCaptureOnly(true);
        CubicRenderer.processRenderModel(renderProcessor, null, stack, model);
        renderProcessor.setCaptureOnly(false);

        for (WeldBinding binding : bindings)
        {
            for (WeldBinding.Layer layer : binding.layers)
            {
                layer.computeSeam();
            }
        }
    }

    public void render(MatrixStack stack, Supplier<ShaderProgram> program, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Link> textureResolver)
    {
        ShaderProgram shader = program.get();

        if (this.model instanceof Model model)
        {
            boolean isVao = this.isVAORendered();
            CubicCubeRenderer renderProcessor = isVao
                ? new CubicVAORenderer(shader, this, light, overlay, stencilMap, keys, textureResolver)
                : new CubicCubeRenderer(light, overlay, stencilMap, keys);

            renderProcessor.setColor(color.r, color.g, color.b, color.a);

            boolean welded = !isVao && !this.welds.isEmpty();

            if (welded)
            {
                renderProcessor.setWelds(this.getWeldBindings());
            }

            if (isVao)
            {
                CubicRenderer.processRenderModel(renderProcessor, null, stack, model);
            }
            else
            {
                RenderSystem.setShader(() -> shader);

                if (welded)
                {
                    this.captureWelds(renderProcessor, stack, model);
                }

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
