package mchorse.bbs_mod.forms.renderers;

import com.mojang.authlib.GameProfile;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MobFormRenderer extends FormRenderer<MobForm> implements ITickable
{
    /** Per entity class, its model's ModelParts by field name — resolved reflectively once in {@link #getBones}. */
    private static final Map<Class, Map<String, ModelPart>> parts = new HashMap<>();

    public static final GameProfile WIDE = new GameProfile(UUID.fromString("b99a2400-28a8-4288-92dc-924beafbf756"), "McHorseYT");
    public static final GameProfile SLIM = new GameProfile(UUID.fromString("5477bd28-e672-4f87-a209-c03cf75f3606"), "osmiq");

    private Entity entity;

    private String lastId = "";
    private String lastNBT = "";
    private boolean lastSlim;

    public float prevHandSwing;
    private float prevYawHead;
    private float prevPitch;

    public MobFormRenderer(MobForm form)
    {
        super(form);
    }

    @Override
    public List<String> getBones()
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            Map<String, ModelPart> stringModelPartMap = parts.get(this.entity.getClass());

            if (stringModelPartMap == null)
            {
                stringModelPartMap = new HashMap<>();

                if (MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.entity) instanceof LivingEntityRenderer renderer)
                {
                    EntityModel model = renderer.getModel();
                    Set<Field> fields = new HashSet<>();
                    Class aClass = model.getClass();

                    while (aClass != Object.class)
                    {
                        for (Field field : aClass.getDeclaredFields())
                        {
                            fields.add(field);
                        }

                        aClass = aClass.getSuperclass();
                    }

                    for (Field declaredField : fields)
                    {
                        if (declaredField.getType().equals(ModelPart.class))
                        {
                            try
                            {
                                declaredField.setAccessible(true);

                                ModelPart part = (ModelPart) declaredField.get(model);

                                stringModelPartMap.put(declaredField.getName(), part);
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                parts.put(this.entity.getClass(), stringModelPartMap);
            }

            return new ArrayList<>(stringModelPartMap.keySet());
        }

        return super.getBones();
    }

    private void bindTexture()
    {
        Link link = this.form.texture.get();

        if (link != null)
        {
            BBSModClient.getTextures().bindTexture(link);
        }
    }

    private void ensureEntity()
    {
        String id = this.form.mobID.get();
        String nbt = this.form.mobNBT.get();
        boolean slim = this.form.slim.get();

        if (!this.lastId.equals(id) || !this.lastNBT.equals(nbt) || slim != this.lastSlim)
        {
            this.lastId = id;
            this.lastNBT = nbt;
            this.lastSlim = slim;
            this.entity = null;
        }

        if (this.entity != null)
        {
            return;
        }

        NbtCompound compound = new NbtCompound();

        try
        {
            /* 1.21.5: new StringNbtReader(StringReader).parseCompound() -> StringNbtReader.readCompound(String). */
            compound = StringNbtReader.readCompound(nbt);
        }
        catch (Exception e)
        {}

        /* 1.21.2: EntityType.create(World) -> create(World, SpawnReason). */
        this.entity = Registries.ENTITY_TYPE.get(Identifier.of(id)).create(MinecraftClient.getInstance().world, SpawnReason.COMMAND);

        if (this.entity == null && this.form.isPlayer())
        {
            this.entity = new OtherClientPlayerEntity(MinecraftClient.getInstance().world, slim ? SLIM : WIDE);
            /* TODO(1.21.11 render): PlayerEntity.PLAYER_MODEL_PARTS tracked-data was removed, so the
             * model-parts (cape/jacket/sleeves) byte can no longer be set via the data tracker. The
             * cosmetic model layers must be enabled through the new player-model/skin config when that
             * path is ported. */
        }

        if (this.entity != null)
        {
            compound.putString("id", id);
            /* TODO(1.21.11 render): Entity.readNbt(NbtCompound) was removed by the 1.21.6 persistence
             * rewrite (NBT read now goes through ReadView). The mob's custom NBT (the user-provided
             * mobNBT) is no longer applied. Wire this through the ReadView API once it is ported. */
            this.entity.noClip = true;
        }
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureEntity();

        /* TODO(1.21.11 render): vanilla-entity rendering is stubbed.
         * The 1.21.2 EntityRenderState rewrite changed
         * EntityRenderManager.render(Entity, x, y, z, yaw, tickDelta, MatrixStack,
         * VertexConsumerProvider, light) to
         * render(EntityRenderState, CameraRenderState, x, y, z, MatrixStack,
         * OrderedRenderCommandQueue) and the per-entity texture/shader hijack used here goes through
         * CustomVertexConsumerProvider, which the old immediate VertexConsumerProvider no longer
         * matches. Also context.batcher.getContext().getMatrices() now returns a 2D Matrix3x2fStack.
         * Re-implement against the render-state + command-queue API once the entity-render foundation
         * is ported. The entity is still constructed/ticked so non-render uses (bones, etc.) work. */
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.ensureEntity();

        /* TODO(1.21.11 render): vanilla-entity rendering is stubbed (see renderInUI).
         * This 3D path additionally relied on:
         *  - RenderSystem.setShader(getPickerModelsProgram) + setupTarget(ShaderProgram) for picking
         *    (ShaderProgram/GlUniform removed; the BBS picker pipeline + Target UBO uniform must
         *    replace it);
         *  - the per-layer CustomVertexConsumerProvider hijack to bind the entity texture and force
         *    the picker shader on held-item sub-layers;
         *  - EntityRenderManager.render(Entity, ..., MatrixStack, VertexConsumerProvider, light),
         *    replaced by the render-state + OrderedRenderCommandQueue API;
         *  - RenderSystem.getModelViewMatrix() save/restore + enableDepthTest (state is per-pipeline).
         * Re-implement against the new entity render-state pipeline once the foundation is ported.
         * The entity is still constructed/ticked so bone collection and tick logic keep working. */
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            this.entity.tick();

            /* 1.21.9: Entity prevPitch/prevYaw -> lastPitch/lastYaw; LivingEntity prevHeadYaw/
             * prevBodyYaw -> lastHeadYaw/lastBodyYaw. */
            this.entity.lastPitch = this.prevPitch;
            this.entity.lastYaw = 0F;

            if (this.entity instanceof LivingEntity livingEntity)
            {
                livingEntity.lastHeadYaw = this.prevYawHead;
                livingEntity.lastBodyYaw = 0F;

                /* Limb swing is so ugly */
                if (livingEntity.limbAnimator instanceof LimbAnimatorAccessor a && entity.getLimbAnimator() instanceof LimbAnimatorAccessor b)
                {
                    a.setPrevSpeed(b.getPrevSpeed());
                    a.setSpeed(b.getSpeed());
                    a.setPos(b.getPos());
                }

                /* Arm swing */
                float handSwingProgress = entity.getHandSwingProgress(0F);

                if (handSwingProgress < this.prevHandSwing)
                {
                    this.prevHandSwing = 0;
                }

                if (handSwingProgress > 0 && this.prevHandSwing == 0)
                {
                    livingEntity.swingHand(Hand.MAIN_HAND);
                }

                this.prevHandSwing = handSwingProgress;
            }

            this.entity.setYaw(0F);
            this.entity.setHeadYaw(entity.getHeadYaw() - entity.getBodyYaw());
            this.entity.setPitch(entity.getPitch());
            this.entity.setBodyYaw(0F);

            this.entity.setPos(entity.getX(), entity.getY(), entity.getZ());
            this.entity.setOnGround(entity.isOnGround());
            this.entity.setSneaking(entity.isSneaking());
            this.entity.setSprinting(entity.isSprinting());
            this.entity.setPose(entity.isSneaking() ? EntityPose.CROUCHING : EntityPose.STANDING);
            if (this.entity instanceof LivingEntity living)
            {
                living.equipStack(EquipmentSlot.MAINHAND, entity.getEquipmentStack(EquipmentSlot.MAINHAND));
                living.equipStack(EquipmentSlot.OFFHAND, entity.getEquipmentStack(EquipmentSlot.OFFHAND));
                living.equipStack(EquipmentSlot.HEAD, entity.getEquipmentStack(EquipmentSlot.HEAD));
                living.equipStack(EquipmentSlot.CHEST, entity.getEquipmentStack(EquipmentSlot.CHEST));
                living.equipStack(EquipmentSlot.LEGS, entity.getEquipmentStack(EquipmentSlot.LEGS));
                living.equipStack(EquipmentSlot.FEET, entity.getEquipmentStack(EquipmentSlot.FEET));
            }
            this.entity.age = entity.getAge();
            this.entity.noClip = true;

            this.prevYawHead = entity.getHeadYaw() - entity.getBodyYaw();
            this.prevPitch = entity.getPitch();
        }
    }

}
