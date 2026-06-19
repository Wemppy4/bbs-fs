package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.PhysicsControls;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.mixin.client.ClientPlayerEntityAccessor;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.TransformSpace;
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

        if (UIBaseMenu.shouldRenderAxes())
        {
            if (context.bone != null) renderAxes(context.bone, context.space, context.map, form, entity, transition, stack);
            if (context.bone2 != null && context.map == null) renderPreviewAxes(context.bone2, context.space2, form, entity, transition, stack);
        }

        stack.pop();

        if (UIBaseMenu.shouldRenderAxes() && context.anchorGizmo)
        {
            renderAnchorGizmo(entities, entity, target, defaultMatrix, cx, cy, cz, transition, context.anchorSpace, context.map, stack);
        }

        if (!relative && context.map == null && opacity > 0F && context.shadowRadius > 0F)
        {
            /* Place the shadow under the replay's perceived position: shift the actual shadow position
             * by how far the model (form transform + anchor-bone root motion) has moved from rest,
             * mapped from form-local into world axes via the render target. Moving the position itself
             * (not just translating the quad) makes the shadow's ground projection and shading match. */
            double shadowX = position.x;
            double shadowY = position.y;
            double shadowZ = position.z;

            FormRenderer renderer = FormUtilsClient.getRenderer(FormUtils.getRoot(form));

            if (renderer != null && !BBSRendering.isIrisShadowPass() && context.replay != null && context.replay.shadowFollow.get())
            {
                Vector3f displacement = renderer.getShadowDisplacement(entity, transition);

                if (displacement != null)
                {
                    target.transformDirection(displacement);

                    shadowX += displacement.x;
                    shadowY += displacement.y;
                    shadowZ += displacement.z;
                }

                /* Extra world-space nudge to seat the shadow on the model's real floor (added after the
                 * form-local displacement is mapped to world, so it stays vertical regardless of facing). */
                Point offset = context.replay.shadowOffset.get();

                shadowX += offset.x;
                shadowY += offset.y;
                shadowZ += offset.z;
            }

            stack.push();
            stack.translate(shadowX - cx, shadowY - cy, shadowZ - cz);

            ModelBlockEntityRenderer.renderShadow(context.consumers, stack, transition, shadowX, shadowY, shadowZ, 0F, 0F, 0F, context.shadowRadius, opacity);

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

    private static void renderAxes(String bone, TransformSpace space, StencilMap stencilMap, Form form, IEntity entity, float transition, MatrixStack stack)
    {
        String mapKey = bone != null && bone.contains(PerLimbService.POSE_BONES) ? bone.replace(PerLimbService.POSE_BONES, "") : bone;
        Form root = FormUtils.getRoot(form);
        MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, transition);
        Matrix4f matrix = space == TransformSpace.LOCAL ? map.get(mapKey).matrix() : map.get(mapKey).origin();

        if (matrix != null)
        {
            /* LOCAL strips the bone's scale (the gizmo must not inherit it, the
             * form editor does the same); WORLD keeps only the position. */
            if (space == TransformSpace.LOCAL) matrix = MatrixStackUtils.stripScale(matrix);
            else if (space == TransformSpace.WORLD) matrix = new Matrix4f().translation(matrix.getTranslation(new Vector3f()));

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

    /**
     * The replay's "axes preview" (a secondary bone): plain, non-interactive
     * cool axes via {@link Draw#coolerAxes} — not the editing gizmo. Resolves the
     * bone matrix exactly like {@link #renderAxes} and applies the same
     * distance scaling the gizmo uses, so the preview keeps a constant on-screen
     * size and matches the gizmo's axes.
     */
    private static void renderPreviewAxes(String bone, TransformSpace space, Form form, IEntity entity, float transition, MatrixStack stack)
    {
        String mapKey = bone != null && bone.contains(PerLimbService.POSE_BONES) ? bone.replace(PerLimbService.POSE_BONES, "") : bone;
        Form root = FormUtils.getRoot(form);
        MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, transition);
        MatrixCacheEntry entry = map.get(mapKey);

        if (entry == null)
        {
            return;
        }

        Matrix4f matrix = space == TransformSpace.LOCAL ? entry.matrix() : entry.origin();

        if (matrix == null)
        {
            return;
        }

        if (space == TransformSpace.LOCAL) matrix = MatrixStackUtils.stripScale(matrix);
        else if (space == TransformSpace.WORLD) matrix = new Matrix4f().translation(matrix.getTranslation(new Vector3f()));

        stack.push();
        MatrixStackUtils.multiply(stack, matrix);

        Vector3f cameraRelative = stack.peek().getPositionMatrix().getTranslation(new Vector3f());
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        float fov = proj.m33() == 0 ? (float) (2.0 * Math.atan(1.0 / proj.m11())) : BBSSettings.getFov();
        float distanceScale = BBSSettings.getAxesDistanceScale(cameraRelative.length(), fov);

        stack.scale(distanceScale, distanceScale, distanceScale);
        Draw.coolerAxes(stack, 0.25F, 0.008F);

        RenderSystem.enableDepthTest();
        stack.pop();
    }

    /**
     * Draw the editing gizmo for the form's anchor offset. The anchor is applied
     * as {@code parent.mul(transform)} in {@link #getTotalMatrix}, so the gizmo
     * sits at that resolved matrix {@code full} (already computed as the entity's
     * render target) and edits {@code form.anchor.transform}. The space toggle
     * mirrors {@link #renderAxes}: LOCAL keeps the anchor's own orientation,
     * WORLD axis-aligns it, PARENT uses the attachment's orientation at the
     * anchor's position.
     */
    private static void renderAnchorGizmo(IntObjectMap<IEntity> entities, IEntity entity, Matrix4f full, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, TransformSpace space, StencilMap stencilMap, MatrixStack stack)
    {
        Form form = entity.getForm();

        if (form == null || full == null)
        {
            return;
        }

        Matrix4f matrix;

        if (space == TransformSpace.WORLD)
        {
            matrix = new Matrix4f().translation(full.getTranslation(new Vector3f()));
        }
        else if (space == TransformSpace.PARENT)
        {
            Matrix4f parent = getEntityMatrix(entities, cx, cy, cz, form.anchor.get(), defaultMatrix, transition, 0, true);

            matrix = MatrixStackUtils.stripScale(parent);
            matrix.setTranslation(full.getTranslation(new Vector3f()));
        }
        else
        {
            matrix = MatrixStackUtils.stripScale(full);
        }

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
        Matrix4f matrix = getBoneCompositeMatrix(entities, entity, replay, cameraX, cameraY, cameraZ, transition, bonePath, useBoneMatrix);

        return matrix == null ? null : MatrixStackUtils.stripScale(matrix);
    }

    /**
     * The same composite as {@link #getGizmoBoneCompositeMatrix} but with the bone's scale kept.
     * The gizmo drops scale on purpose (a gizmo must not inherit it); world-space transform capture
     * needs the full matrix, so it goes through this variant instead.
     */
    public static Matrix4f getBoneCompositeMatrix(
        IntObjectMap<IEntity> entities,
        IEntity entity,
        Replay replay,
        double cameraX,
        double cameraY,
        double cameraZ,
        float transition,
        String bonePath,
        boolean useBoneMatrix
    ) {
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

        return new Matrix4f(target).mul(bone);
    }

    /**
     * The anchor's resolved world matrix as composed for the film viewport — the
     * same {@code target} {@link #renderEntity} renders the form with, i.e.
     * {@code getTotalMatrix(form.anchor)}. Used by the gizmo drag to numerically
     * sample how {@code form.anchor.transform} maps to world position/rotation
     * (the counterpart of {@link #getGizmoBoneCompositeMatrix} for the anchor,
     * with no bone multiply since the anchor moves the whole form).
     */
    public static Matrix4f getGizmoAnchorCompositeMatrix(
        IntObjectMap<IEntity> entities,
        IEntity entity,
        Replay replay,
        double cameraX,
        double cameraY,
        double cameraZ,
        float transition
    ) {
        if (entity == null || entity.getForm() == null)
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
        Matrix4f full = defaultMatrix;

        if (!relative)
        {
            Pair<Matrix4f, Float> pair = getTotalMatrix(entities, form.anchor.get(), defaultMatrix, cx, cy, cz, transition, 0);

            full = pair.a != null ? pair.a : defaultMatrix;
        }

        return MatrixStackUtils.stripScale(full);
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
                int ticks = replay.getTick(this.getTick());

                entity.setForm(FormUtils.copy(replay.form.get()));
                replay.keyframes.apply(ticks, entity);
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
                            boolean grounded = replay.keyframes.grounded.interpolate(ticks) > 0;

                            Vec3d pos = player.getPos();

                            player.move(MovementType.SELF, new Vec3d(x - pos.x, y - pos.y, z - pos.z));
                            player.setPosition(x, y, z);

                            player.setSneaking(sneaking);
                            player.setOnGround(grounded);

                            /* First person teleports the player from keyframes instead of walking it, so vanilla's
                             * stride distance (the view-bobbing amplitude) is computed from a zero velocity and stays
                             * flat. Re-derive it from the actual per-tick displacement (the same source as the limb
                             * animation) with vanilla's own easing. prevStrideDistance already holds last tick's value
                             * (snapshotted by the player tick), so only the current one is advanced — keeping the bob
                             * smooth between frames. */
                            float dx = (float) (player.getX() - player.prevX);
                            float dz = (float) (player.getZ() - player.prevZ);
                            float stride = grounded ? Math.min(0.1F, (float) Math.sqrt(dx * dx + dz * dz)) : 0F;

                            player.strideDistance = player.prevStrideDistance + (stride - player.prevStrideDistance) * 0.4F;

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

            if (PerLimbService.isPhysicsControlChannel(id))
            {
                this.applyPhysicsControls(root, PerLimbService.parsePhysicsControlFormPath(id), channel, tick);
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
                this.applyPhysicsTarget(root, physicsPath.formPath(), physicsPath.rootBone(), channel, tick, transition);
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

    private void applyPhysicsControls(Form root, String formPath, KeyframeChannel<?> channel, float tick)
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

        if (!(value instanceof PhysicsControls controls))
        {
            return;
        }

        for (Map.Entry<String, PhysicsControl> entry : controls.controls.entrySet())
        {
            modelForm.physicsControlOverrides.computeIfAbsent(entry.getKey(), (k) -> new PhysicsControl()).copy(entry.getValue());
        }
    }

    private enum TargetKind
    {
        IK, POLE
    }

    private void applyOverride(Form root, String formPath, String targetId, KeyframeChannel<?> channel, float tick, float transition, TargetKind kind)
    {
        Form form = formPath.isEmpty() ? root : FormUtils.getForm(root, formPath);

        if (!(form instanceof ModelForm modelForm))
        {
            return;
        }

        KeyframeSegment<?> segment = channel.find(tick);

        if (segment == null || !(segment.createInterpolated() instanceof Anchor anchor))
        {
            return;
        }

        Map<String, Vector3f> overrides = switch (kind)
        {
            case IK -> modelForm.ikTargetOverrides;
            case POLE -> modelForm.poleTargetOverrides;
        };
        Map<String, Float> weights = switch (kind)
        {
            case IK -> modelForm.ikTargetWeights;
            case POLE -> modelForm.poleTargetWeights;
        };

        /* Resolve the BOUND side at its full position with a 0..1 fade weight, mirroring
         * applyPhysicsTarget: feeding the fading anchor straight to getTotalMatrix would
         * lerp the position from world origin across a "None" key, yanking the pole/target
         * to (0,0,0). The applier eases the override in/out from the config position by the
         * weight instead, so a fade glides from where the bone already is. */
        Anchor resolve;
        float weight;

        if (anchor.previous != null && anchor.isFadeIn())
        {
            resolve = anchor.copy();
            weight = anchor.x;
        }
        else if (anchor.previous != null && anchor.isFadeOut())
        {
            resolve = anchor.previous;
            weight = 1F - anchor.x;
        }
        else
        {
            resolve = anchor;
            weight = 1F;
        }

        if (weight <= 0F || resolve.replay == Anchor.NO_ATTACHMENT || this.entities.get(resolve.replay) == null)
        {
            return;
        }

        Pair<Matrix4f, Float> matrix = getTotalMatrix(this.entities, resolve, IDENTITY, 0D, 0D, 0D, transition, 0, true);
        Matrix4f resolved = matrix.a != null ? matrix.a : IDENTITY;
        Vector3f position = resolved.getTranslation(TEMP_VECTOR);

        overrides.computeIfAbsent(targetId, (k) -> new Vector3f()).set(position);
        weights.put(targetId, weight);
    }

    /**
     * Physics target override with fade support. Unlike the IK/pole targets this also resolves a fade
     * <em>weight</em>: when the binding crosses a no-target keyframe the shared anchor interpolation lerps the
     * resolved matrix from world origin, which yanks the chain to (0,0,0). Instead we resolve the bound side at
     * its full position and hand the physics solver a 0..1 weight so it can ease the chain in/out from its own
     * tip (see {@link ModelPhysicsRuntime}).
     */
    private void applyPhysicsTarget(Form root, String formPath, String rootBone, KeyframeChannel<?> channel, float tick, float transition)
    {
        Form form = formPath.isEmpty() ? root : FormUtils.getForm(root, formPath);

        if (!(form instanceof ModelForm modelForm))
        {
            return;
        }

        KeyframeSegment<?> segment = channel.find(tick);

        if (segment == null || !(segment.createInterpolated() instanceof Anchor anchor))
        {
            return;
        }

        /* Pick the bound side and how present it is. Fade in/out blends to/from "no target"; a straight switch
         * between two real targets keeps the anchor's own lerp at full weight. */
        Anchor resolve;
        float weight;

        if (anchor.previous != null && anchor.isFadeIn())
        {
            resolve = anchor.copy();
            weight = anchor.x;
        }
        else if (anchor.previous != null && anchor.isFadeOut())
        {
            resolve = anchor.previous;
            weight = 1F - anchor.x;
        }
        else
        {
            resolve = anchor;
            weight = 1F;
        }

        if (weight <= 0F || resolve.replay == Anchor.NO_ATTACHMENT || this.entities.get(resolve.replay) == null)
        {
            return;
        }

        Pair<Matrix4f, Float> matrix = getTotalMatrix(this.entities, resolve, IDENTITY, 0D, 0D, 0D, transition, 0, true);
        Matrix4f resolved = matrix.a != null ? matrix.a : IDENTITY;
        Vector3f position = resolved.getTranslation(TEMP_VECTOR);

        modelForm.physicsTargetOverrides.computeIfAbsent(rootBone, (k) -> new Vector3f()).set(position);
        modelForm.physicsTargetWeights.put(rootBone, weight);
    }

    private void clearTargetOverrides(Form form)
    {
        if (form instanceof ModelForm modelForm)
        {
            modelForm.ikTargetOverrides.clear();
            modelForm.poleTargetOverrides.clear();
            modelForm.ikTargetWeights.clear();
            modelForm.poleTargetWeights.clear();
            modelForm.ikControlOverrides.clear();
            modelForm.physicsTargetOverrides.clear();
            modelForm.physicsTargetWeights.clear();
            modelForm.physicsControlOverrides.clear();
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
