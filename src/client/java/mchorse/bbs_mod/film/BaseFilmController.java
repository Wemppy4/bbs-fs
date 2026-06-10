package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.mixin.client.ClientPlayerEntityAccessor;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class BaseFilmController
{
    public final Film film;

    public final IntObjectMap<IEntity> entities = new IntObjectHashMap<>();

    public boolean paused;
    public int exception = -1;

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Vector3f TEMP_VECTOR = new Vector3f();

    /* Rendering helpers */

    public static void renderEntity(FilmControllerContext context)
    {
        IntObjectMap<IEntity> entities = context.entities;
        IEntity entity = context.entity;
        Camera camera = context.camera;
        MatrixStack stack = context.stack;
        float transition = context.transition;

        Form form = entity.getForm();

        if (form == null)
        {
            return;
        }

        Vector3d position = Vectors.TEMP_3D.set(
            Lerps.lerp(entity.getPrevX(), entity.getX(), transition),
            Lerps.lerp(entity.getPrevY(), entity.getY(), transition),
            Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition)
        );

        double cx = camera.getPos().x;
        double cy = camera.getPos().y;
        double cz = camera.getPos().z;

        boolean relative = context.replay != null && context.relative;

        if (relative)
        {
            cx = context.replay.keyframes.x.interpolate(0F) + context.replay.relativeOffset.get().x;
            cy = context.replay.keyframes.y.interpolate(0F) + context.replay.relativeOffset.get().y;
            cz = context.replay.keyframes.z.interpolate(0F) + context.replay.relativeOffset.get().z;
        }

        Matrix4f target = null;
        Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, cx, cy, cz, transition);
        float opacity = 1F;

        if (!relative)
        {
            Pair<Matrix4f, Float> pair = getTotalMatrix(entities, form.anchor.get(), defaultMatrix, cx, cy, cz, transition, 0);

            target = pair.a;
            opacity = pair.b;
        }

        if (target != null)
        {
            Vector3f v = target.getTranslation(new Vector3f());
            Vector3f v2 = defaultMatrix.getTranslation(new Vector3f());

            position.x += v.x - v2.x;
            position.y += v.y - v2.y;
            position.z += v.z - v2.z;
        }
        else
        {
            target = defaultMatrix;
        }

        Matrix4f targetWorld;

        if (relative)
        {
            targetWorld = new Matrix4f(target);
        }
        else
        {
            Matrix4f defaultWorldMatrix = getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, transition);
            Pair<Matrix4f, Float> pairWorld = getTotalMatrix(entities, form.anchor.get(), defaultWorldMatrix, 0D, 0D, 0D, transition, 0);

            targetWorld = pairWorld.a != null ? pairWorld.a : defaultWorldMatrix;
        }

        BlockPos pos = BlockPos.ofFloored(position.x, position.y + 0.5D, position.z);
        int sky = entity.getWorld().getLightLevel(LightType.SKY, pos);
        int torch = entity.getWorld().getLightLevel(LightType.BLOCK, pos);
        int light = LightmapTextureManager.pack(torch, sky);
        int overlay = OverlayTexture.packUv(OverlayTexture.getU(0F), OverlayTexture.getV(entity.getHurtTimer() > 0));

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.ENTITY, entity, stack, light, overlay, transition)
            .camera(camera)
            .stencilMap(context.map)
            .color(context.color);

        stack.push();

        if (relative)
        {
            stack.peek().getPositionMatrix().identity();
            stack.peek().getNormalMatrix().identity();
        }

        formContext.world.peek().getPositionMatrix().identity();
        formContext.world.peek().getNormalMatrix().identity();
        MatrixStackUtils.multiply(formContext.world, targetWorld);

        MatrixStackUtils.multiply(stack, target);
        FormUtilsClient.render(form, formContext);

        if (UIBaseMenu.renderAxes)
        {
            if (context.bone != null) renderAxes(context.bone, context.local, context.map, form, entity, transition, stack);
            if (context.bone2 != null && context.map == null) renderAxes(context.bone2, context.local2, context.map, form, entity, transition, stack);
        }

        stack.pop();

        if (!relative && context.map == null && opacity > 0F && context.shadowRadius > 0F)
        {
            stack.push();
            stack.translate(position.x - cx, position.y - cy, position.z - cz);

            ModelBlockEntityRenderer.renderShadow(context.consumers, stack, transition, position.x, position.y, position.z, 0F, 0F, 0F, context.shadowRadius, opacity);

            stack.pop();
        }

        if (!relative && !context.nameTag.isEmpty() && context.map == null)
        {
            stack.push();
            stack.translate(position.x - cx, position.y - cy, position.z - cz);

            renderNameTag(entity, Text.literal(StringUtils.processColoredText(context.nameTag)), stack, context.consumers, light);

            stack.pop();
        }

        RenderSystem.enableDepthTest();
    }

    private static void renderAxes(String bone, boolean local, StencilMap stencilMap, Form form, IEntity entity, float transition, MatrixStack stack)
    {
        String mapKey = bone != null && bone.contains(PerLimbService.POSE_BONES) ? bone.replace(PerLimbService.POSE_BONES, "") : bone;
        Form root = FormUtils.getRoot(form);
        MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, transition);
        Matrix4f matrix = local ? map.get(mapKey).matrix() : map.get(mapKey).origin();

        if (matrix != null)
        {
            stack.push();
            MatrixStackUtils.multiply(stack, matrix);

            if (stencilMap == null)
            {
                Gizmo.INSTANCE.render(stack);
            }
            else
            {
                Gizmo.INSTANCE.renderStencil(stack, stencilMap);
            }

            RenderSystem.enableDepthTest();
            stack.pop();
        }
    }

    public static Pair<Matrix4f, Float> getTotalMatrix(IntObjectMap<IEntity> entities, Anchor value, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, int i)
    {
        return getTotalMatrix(entities, value, defaultMatrix, cx, cy, cz, transition, i, false);
    }

    public static Pair<Matrix4f, Float> getTotalMatrix(IntObjectMap<IEntity> entities, Anchor value, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, int i, boolean fullMatrix)
    {
        /* Stupid recursion stop, I don't think anyone would need more than that */
        if (i > 5)
        {
            return new Pair<>(defaultMatrix, 1F);
        }

        boolean same = value.previous == null || Objects.equals(value, value.previous);
        boolean only = value.x <= 0F && value.previous != null;
        Pair<Matrix4f, Float> result = new Pair<>(null, 1F);

        if (same || only)
        {
            Anchor anchor = same ? value : value.previous;
            Matrix4f matrix = getEntityMatrix(entities, cx, cy, cz, anchor, defaultMatrix, transition, i, fullMatrix);

            matrix = applyAnchorTransform(matrix, anchor);

            if (matrix != defaultMatrix)
            {
                result.a = matrix;
                result.b = 0F;
            }
        }
        else
        {
            Matrix4f matrix = getEntityMatrix(entities, cx, cy, cz, value, defaultMatrix, transition, i, fullMatrix);
            Matrix4f lastMatrix = getEntityMatrix(entities, cx, cy, cz, value.previous, defaultMatrix, transition, i, fullMatrix);

            matrix = applyAnchorTransform(matrix, value);
            lastMatrix = applyAnchorTransform(lastMatrix, value.previous);

            result.a = value.x >= 1F ? matrix : Matrices.lerp(lastMatrix, matrix, value.x);

            if (value.isFadeOut()) result.b = value.x;
            else if (value.isFadeIn()) result.b = 1F - value.x;
            else result.b = 0F;
        }

        return result;
    }

    private static Matrix4f applyAnchorTransform(Matrix4f matrix, Anchor anchor)
    {
        if (matrix == null || anchor == null || anchor.transform.isDefault())
        {
            return matrix;
        }

        return matrix.mul(anchor.transform.createMatrix());
    }

    public static Matrix4f getEntityMatrix(IntObjectMap<IEntity> entities, double cameraX, double cameraY, double cameraZ, Anchor anchor, Matrix4f defaultMatrix, float transition, int i)
    {
        return getEntityMatrix(entities, cameraX, cameraY, cameraZ, anchor, defaultMatrix, transition, i, false);
    }

    public static Matrix4f getEntityMatrix(IntObjectMap<IEntity> entities, double cameraX, double cameraY, double cameraZ, Anchor anchor, Matrix4f defaultMatrix, float transition, int i, boolean fullMatrix)
    {
        IEntity entity = entities.get(anchor.replay);

        if (entity != null)
        {
            Matrix4f basic = getMatrixForRenderWithRotation(entity, cameraX, cameraY, cameraZ, transition);

            Form form = entity.getForm();

            if (form != null)
            {
                Pair<Matrix4f, Float> totalMatrix = getTotalMatrix(entities, form.anchor.get(), basic, cameraX, cameraY, cameraZ, transition, i + 1, fullMatrix);

                if (totalMatrix.a != null)
                {
                    basic = totalMatrix.a;
                }

                MatrixCache map = FormUtilsClient.getRenderer(form).collectMatrices(entity, transition);
                Matrix4f matrix = map.get(anchor.attachment).matrix();

                if (matrix != null)
                {
                    basic.mul(matrix);

                    if (!fullMatrix && anchor.scale)
                    {
                        Matrix3f mat = new Matrix3f();
                        Vector3f v = new Vector3f();
                        basic.get3x3(mat);

                        mat.getColumn(0, v); v.normalize(); mat.setColumn(0, v);
                        mat.getColumn(1, v); v.normalize(); mat.setColumn(1, v);
                        mat.getColumn(2, v); v.normalize(); mat.setColumn(2, v);

                        basic.set3x3(mat);
                    }

                    if (!fullMatrix && anchor.translate)
                    {
                        Vector3f t = new Vector3f();
                        basic.getTranslation(t);
                        basic.set(defaultMatrix);
                        basic.setTranslation(t);
                    }
                }

            }

            return basic;
        }

        return defaultMatrix;
    }

    public static Matrix4f getMatrixForRenderWithRotation(IEntity entity, double cameraX, double cameraY, double cameraZ, float tickDelta)
    {
        double x = Lerps.lerp(entity.getPrevX(), entity.getX(), tickDelta) - cameraX;
        double y = Lerps.lerp(entity.getPrevY(), entity.getY(), tickDelta) - cameraY;
        double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), tickDelta) - cameraZ;

        Matrix4f matrix = new Matrix4f();

        float bodyYaw = Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), tickDelta);

        matrix.translate((float) x, (float) y, (float) z);
        matrix.rotateY(MathUtils.toRad(-bodyYaw));

        return matrix;
    }

    /**
     * Bone transform as composed for the film viewport: the same {@code target}
     * that {@link #renderEntity} multiplies onto the stack before the bone
     * matrix from {@link FormUtilsClient#getRenderer(Form)#collectMatrices},
     * i.e. {@code target.mul(bone)}. This includes replay position, whole-entity
     * {@code bodyYaw} from {@link #getMatrixForRenderWithRotation}, anchor
     * chains, etc. — everything that is <em>outside</em> the form's internal
     * {@code collectMatrices} tree but affects where the gizmo is drawn.
     *
     * @param cameraX camera position X (same convention as {@link #renderEntity})
     * @param cameraY camera position Y
     * @param cameraZ camera position Z
     * @param bonePath path key matching {@link #renderAxes} (see pose.bones. stripping)
     * @param useBoneMatrix if {@code true}, use the rotation-bearing bone matrix;
     *                      if {@code false}, use the origin-only matrix (matches
     *                      GLOBAL gizmo mode in {@link #renderAxes})
     */
    public static Matrix4f getGizmoBoneCompositeMatrix(
        IntObjectMap<IEntity> entities,
        IEntity entity,
        Replay replay,
        double cameraX,
        double cameraY,
        double cameraZ,
        float transition,
        String bonePath,
        boolean useBoneMatrix
    )
    {
        if (entity == null || entity.getForm() == null || bonePath == null)
        {
            return null;
        }

        Form form = entity.getForm();
        boolean relative = replay != null && replay.relative.get();

        double cx = cameraX;
        double cy = cameraY;
        double cz = cameraZ;

        if (relative && replay != null)
        {
            cx = replay.keyframes.x.interpolate(0F) + replay.relativeOffset.get().x;
            cy = replay.keyframes.y.interpolate(0F) + replay.relativeOffset.get().y;
            cz = replay.keyframes.z.interpolate(0F) + replay.relativeOffset.get().z;
        }

        Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, cx, cy, cz, transition);
        Matrix4f target;

        if (!relative)
        {
            Pair<Matrix4f, Float> pair = getTotalMatrix(entities, form.anchor.get(), defaultMatrix, cx, cy, cz, transition, 0);

            target = pair.a != null ? pair.a : defaultMatrix;
        }
        else
        {
            target = defaultMatrix;
        }

        String mapKey = bonePath.contains(PerLimbService.POSE_BONES)
            ? bonePath.replace(PerLimbService.POSE_BONES, "")
            : bonePath;

        Form root = FormUtils.getRoot(form);
        MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, transition);
        MatrixCacheEntry entry = map.get(mapKey);
        Matrix4f bone = useBoneMatrix ? entry.matrix() : entry.origin();

        if (bone == null)
        {
            return null;
        }

        return MatrixStackUtils.stripScale(new Matrix4f(target).mul(bone));
    }

    private static void renderNameTag(IEntity entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        boolean sneaking = !entity.isSneaking();
        float hitboxH = (float) entity.getPickingHitbox().h + 0.5F;

        matrices.push();
        matrices.translate(0F, hitboxH, 0F);
        matrices.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
        matrices.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        float opacity = MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25F);
        int background = (int) (opacity * 255F) << 24;
        float h = (float) (-textRenderer.getWidth(text) / 2);

        textRenderer.draw(text, h, 0, 0x20ffffff, false, matrix4f, vertexConsumers, sneaking ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL, background, light);

        if (sneaking)
        {
            textRenderer.draw(text, h, 0, -1, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        }

        matrices.pop();
    }

    /* Film controller */

    public BaseFilmController(Film film)
    {
        this.film = film;
    }

    public IntObjectMap<IEntity> getEntities()
    {
        return this.entities;
    }

    public void togglePause()
    {
        this.paused = !this.paused;
    }

    public void createEntities()
    {
        this.entities.clear();

        if (this.film == null)
        {
            return;
        }

        int i = 0;

        for (Replay replay : this.film.replays.getList())
        {
            if (replay.enabled.get())
            {
                World world = MinecraftClient.getInstance().world;
                IEntity entity = new StubEntity(world);

                entity.setForm(FormUtils.copy(replay.form.get()));
                replay.keyframes.apply(0, entity);
                entity.setPrevX(entity.getX());
                entity.setPrevY(entity.getY());
                entity.setPrevZ(entity.getZ());

                entity.setPrevYaw(entity.getYaw());
                entity.setPrevHeadYaw(entity.getHeadYaw());
                entity.setPrevPitch(entity.getPitch());
                entity.setPrevBodyYaw(entity.getBodyYaw());

                this.entities.put(i, entity);
            }

            i += 1;
        }
    }

    public abstract Map<String, Integer> getActors();

    public abstract int getTick();

    public boolean hasFinished()
    {
        return false;
    }

    public void update()
    {
        this.updateEntities(this.getTick());
    }

    protected void updateEntities(int ticks)
    {
        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            List<Replay> replays = this.film.replays.getList();
            Replay replay = CollectionUtils.getSafe(replays, i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.UPDATE))
            {
                continue;
            }

            if (replay != null)
            {
                ticks = replay.getTick(ticks);

                this.updateEntityAndForm(entity, ticks);
                this.applyReplay(replay, ticks, entity);

                Map<String, Integer> actors = this.getActors();

                if (actors != null)
                {
                    Integer entityId = actors.get(replay.getId());

                    if (entityId != null)
                    {
                        Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                        if (anEntity instanceof ActorEntity actor)
                        {
                            /* Force synchronize entity angles */
                            actor.setYaw(replay.keyframes.yaw.interpolate(ticks).floatValue());
                            actor.setHeadYaw(replay.keyframes.headYaw.interpolate(ticks).floatValue());
                            actor.setBodyYaw(replay.keyframes.bodyYaw.interpolate(ticks).floatValue());
                            actor.setPitch(replay.keyframes.pitch.interpolate(ticks).floatValue());
                            replay.applyClientActions(ticks, new MCEntity(anEntity), this.film);
                        }
                        else if (anEntity instanceof PlayerEntity player)
                        {
                            double x = replay.keyframes.x.interpolate(ticks);
                            double y = replay.keyframes.y.interpolate(ticks);
                            double z = replay.keyframes.z.interpolate(ticks);
                            double prevX = replay.keyframes.x.interpolate(ticks - 1);
                            double prevY = replay.keyframes.y.interpolate(ticks - 1);
                            double prevZ = replay.keyframes.z.interpolate(ticks - 1);

                            player.setVelocity(x - prevX, y - prevY, z - prevZ);
                        }
                    }
                }
            }
        }
    }

    public void updateEndWorld()
    {
        int ticks = this.getTick();

        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            List<Replay> replays = this.film.replays.getList();
            Replay replay = CollectionUtils.getSafe(replays, i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.UPDATE))
            {
                continue;
            }

            if (replay != null)
            {
                ticks = replay.getTick(ticks);

                Map<String, Integer> actors = this.getActors();

                if (actors != null)
                {
                    Integer entityId = actors.get(replay.getId());

                    if (entityId != null)
                    {
                        Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                        if (anEntity instanceof PlayerEntity player)
                        {
                            double x = replay.keyframes.x.interpolate(ticks);
                            double y = replay.keyframes.y.interpolate(ticks);
                            double z = replay.keyframes.z.interpolate(ticks);
                            boolean sneaking = replay.keyframes.sneaking.interpolate(ticks) > 0;

                            Vec3d pos = player.getPos();

                            player.move(MovementType.SELF, new Vec3d(x - pos.x, y - pos.y, z - pos.z));
                            player.setPosition(x, y, z);

                            player.setSneaking(sneaking);
                            player.setOnGround(replay.keyframes.grounded.interpolate(ticks) > 0);

                            if (player instanceof ClientPlayerEntityAccessor accessor)
                            {
                                accessor.bbs$setIsSneakingPose(sneaking);
                            }

                            if (player instanceof ClientPlayerEntity playerEntity)
                            {
                                playerEntity.input.sneaking = sneaking;
                            }

                            player.fallDistance = replay.keyframes.fall.interpolate(ticks).floatValue();
                        }
                    }
                }
            }
        }
    }

    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        entity.update();

        if (entity.getForm() != null)
        {
            entity.getForm().update(entity);
        }
    }

    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        replay.keyframes.apply(ticks, entity);
        replay.applyClientActions(ticks, entity, this.film);
    }

    public void startRenderFrame(float transition)
    {
        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            Replay replay = this.film.replays.getList().get(i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.PROPERTIES))
            {
                continue;
            }

            float delta = this.getTransition(entity, transition);
            int tick = replay.getTick(this.getTick());

            /* Apply property */
            Form form1 = entity.getForm();
            replay.properties.applyProperties(form1, tick + delta);
            this.applyTargetOverrides(replay, form1, tick + delta, delta);

            Map<String, Integer> actors = this.getActors();

            if (actors != null)
            {
                Integer entityId = actors.get(replay.getId());

                if (entityId != null)
                {
                    Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                    if (anEntity instanceof ActorEntity actor)
                    {
                        Form form = actor.getForm();
                        replay.properties.applyProperties(form, tick + delta);
                        this.applyTargetOverrides(replay, form, tick + delta, delta);
                    }
                    else if (anEntity instanceof PlayerEntity player)
                    {
                        Morph morph = Morph.getMorph(player);

                        if (morph != null)
                        {
                            Form form = morph.getForm();
                            replay.properties.applyProperties(form, tick + delta);
                            this.applyTargetOverrides(replay, form, tick + delta, delta);
                        }

                        float yawHead = replay.keyframes.headYaw.interpolate(tick + delta).floatValue();
                        float yawBody = replay.keyframes.bodyYaw.interpolate(tick + delta).floatValue();
                        float pitch = replay.keyframes.pitch.interpolate(tick + delta).floatValue();

                        player.setYaw(yawHead);
                        player.setHeadYaw(yawHead);
                        player.setPitch(pitch);
                        player.setBodyYaw(yawBody);
                        player.prevYaw = yawHead;
                        player.prevHeadYaw = yawHead;
                        player.prevPitch = pitch;
                        player.prevBodyYaw = yawBody;
                    }
                }
            }
        }
    }

    public void update(Replay replay, Form root, float tick, float transition)
    {
        this.applyTargetOverrides(replay, root, tick, transition);
    }

    private void applyTargetOverrides(Replay replay, Form root, float tick, float transition)
    {
        if (replay == null || root == null)
        {
            return;
        }

        this.clearTargetOverrides(root);

        if (replay.properties == null || replay.properties.properties == null || replay.properties.properties.isEmpty())
        {
            return;
        }

        for (KeyframeChannel<?> channel : replay.properties.properties.values())
        {
            if (channel == null)
            {
                continue;
            }

            String id = channel.getId();

            if (id == null || id.isEmpty())
            {
                continue;
            }

            if (PerLimbService.isIKControlChannel(id))
            {
                this.applyIKControls(root, PerLimbService.parseIKControlFormPath(id), channel, tick);
                continue;
            }

            PerLimbService.IKTargetPath ikPath = PerLimbService.parseIKTargetPath(id);

            if (ikPath != null)
            {
                this.applyOverride(root, ikPath.formPath(), ikPath.controller(), channel, tick, transition, TargetKind.IK);
                continue;
            }

            PerLimbService.PoleTargetPath polePath = PerLimbService.parsePoleTargetPath(id);

            if (polePath != null)
            {
                this.applyOverride(root, polePath.formPath(), polePath.controller(), channel, tick, transition, TargetKind.POLE);
                continue;
            }

            PerLimbService.PhysicsTargetPath physicsPath = PerLimbService.parsePhysicsTargetPath(id);

            if (physicsPath != null)
            {
                this.applyOverride(root, physicsPath.formPath(), physicsPath.rootBone(), channel, tick, transition, TargetKind.PHYSICS);
            }
        }
    }

    private void applyIKControls(Form root, String formPath, KeyframeChannel<?> channel, float tick)
    {
        Form form = formPath == null || formPath.isEmpty() ? root : FormUtils.getForm(root, formPath);

        if (!(form instanceof ModelForm modelForm))
        {
            return;
        }

        KeyframeSegment<?> segment = channel.find(tick);

        if (segment == null)
        {
            return;
        }

        Object value = segment.createInterpolated();

        if (!(value instanceof IKControls controls))
        {
            return;
        }

        for (Map.Entry<String, IKControl> entry : controls.controls.entrySet())
        {
            modelForm.ikControlOverrides.computeIfAbsent(entry.getKey(), (k) -> new IKControl()).copy(entry.getValue());
        }
    }

    private enum TargetKind
    {
        IK, POLE, PHYSICS
    }

    private void applyOverride(Form root, String formPath, String targetId, KeyframeChannel<?> channel, float tick, float transition, TargetKind kind)
    {
        Form form = formPath.isEmpty() ? root : FormUtils.getForm(root, formPath);

        if (form instanceof ModelForm modelForm)
        {
            Vector3f position = resolveTargetPosition(channel, tick, transition);

            if (position != null)
            {
                Map<String, Vector3f> overrides = switch (kind)
                {
                    case IK -> modelForm.ikTargetOverrides;
                    case POLE -> modelForm.poleTargetOverrides;
                    case PHYSICS -> modelForm.physicsTargetOverrides;
                };

                overrides.computeIfAbsent(targetId, (k) -> new Vector3f()).set(position);
            }
        }
    }

    private Vector3f resolveTargetPosition(KeyframeChannel<?> channel, float tick, float transition)
    {
        KeyframeSegment<?> segment = channel.find(tick);

        if (segment == null)
        {
            return null;
        }

        Object v = segment.createInterpolated();

        if (!(v instanceof Anchor anchor))
        {
            return null;
        }

        IEntity targetEntity = this.entities.get(anchor.replay);

        if (targetEntity == null)
        {
            return null;
        }

        Pair<Matrix4f, Float> matrix = getTotalMatrix(this.entities, anchor, IDENTITY, 0D, 0D, 0D, transition, 0, true);
        Matrix4f resolved = matrix.a != null ? matrix.a : IDENTITY;

        return resolved.getTranslation(TEMP_VECTOR);
    }

    private void clearTargetOverrides(Form form)
    {
        if (form instanceof ModelForm modelForm)
        {
            modelForm.ikTargetOverrides.clear();
            modelForm.poleTargetOverrides.clear();
            modelForm.ikControlOverrides.clear();
            modelForm.physicsTargetOverrides.clear();
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            Form child = part.getForm();

            if (child != null)
            {
                this.clearTargetOverrides(child);
            }
        }
    }

    protected float getTransition(IEntity entity, float transition)
    {
        return this.paused ? 0F : transition;
    }

    protected boolean canUpdate(int i, Replay replay, IEntity entity, UpdateMode updateMode)
    {
        if (this.paused && (updateMode == UpdateMode.UPDATE))
        {
            return false;
        }

        return i != this.exception;
    }

    public void render(WorldRenderContext context)
    {
        RenderSystem.enableDepthTest();

        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            Replay replay = this.film.replays.getList().get(i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.RENDER))
            {
                continue;
            }

            this.renderEntity(context, replay, entity);
        }
    }

    protected void renderEntity(WorldRenderContext context, Replay replay, IEntity entity)
    {
        if (!replay.actor.get())
        {
            FilmControllerContext filmContext = getFilmControllerContext(context, replay, entity);

            filmContext.transition = getTransition(entity, context.tickDelta());

            renderEntity(filmContext);
        }
    }

    protected FilmControllerContext getFilmControllerContext(WorldRenderContext context, Replay replay, IEntity entity)
    {
        return FilmControllerContext.instance
            .setup(this.entities, entity, replay, context)
            .shadow(replay.shadow.get(), replay.shadowSize.get())
            .nameTag(replay.nameTag.get())
            .relative(replay.relative.get());
    }

    public void shutdown()
    {}

    public static enum UpdateMode
    {
        UPDATE, RENDER, PROPERTIES;
    }
}
