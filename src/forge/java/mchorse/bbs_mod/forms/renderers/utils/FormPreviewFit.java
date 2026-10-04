package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;

/**
 * Frames form geometry and its attached parts at the preview's mouse-controlled turn.
 * Models supply cached rest-pose bounds and bone attachments; other renderers supply
 * their local geometry bounds. Container forms contribute transforms and descendants.
 */
public final class FormPreviewFit
{
    /** ModelFormRenderer.getUIMatrix's tilt towards the viewer. */
    private static final float TILT_SIN = (float) Math.sin(MathUtils.PI / 8);
    private static final float TILT_COS = (float) Math.cos(MathUtils.PI / 8);

    /** Share of the box kept free on each side; a player-sized model then matches stock size. */
    private static final float MARGIN = 0.06F;
    private static final float EPSILON = 1.0E-4F;

    /** Bounds are re-read this often, so geometry edited in place catches up. */
    private static final long REFRESH_MS = 1000L;

    private static final Map<IModel, Bounds> CACHE = new WeakHashMap<>();

    private FormPreviewFit()
    {}

    /**
     * The fitted replacement for {@code stock} (getUIMatrix's result), or null to keep stock.
     */
    public static Matrix4f frame(FormRenderer<?> renderer, Matrix4f stock, int x1, int y1, int x2, int y2, float transition)
    {
        ModelInstance model = renderer instanceof ModelFormRenderer models ? models.getModel() : null;
        Bounds bounds = new Bounds();
        collectForm(renderer, model, new Matrix4f(), bounds, transition, true);

        if (bounds.empty)
        {
            return null;
        }

        float minY = bounds.min.y;
        float maxY = bounds.max.y;
        float radius = bounds.radius(1F, 1F);

        /* Whatever way the model is turned, depth up to the radius tilts into the picture's height. */
        float top = maxY * TILT_COS + radius * TILT_SIN;
        float bottom = minY * TILT_COS - radius * TILT_SIN;
        float w = (x2 - x1) * (1F - MARGIN * 2F);
        float h = (y2 - y1) * (1F - MARGIN * 2F);
        float k = Float.MAX_VALUE;

        if (radius > EPSILON)
        {
            k = w / (radius * 2F);
        }

        if (top - bottom > EPSILON)
        {
            k = Math.min(k, h / (top - bottom));
        }

        Matrix4f turn = new Matrix4f(stock).setTranslation(0F, 0F, 0F);
        float stockScale = turn.getScale(new Vector3f()).x;

        if (k == Float.MAX_VALUE || !Float.isFinite(k) || k <= 0F || stockScale < EPSILON)
        {
            return null;
        }

        float y = (y1 + y2) / 2F + k * (top + bottom) / 2F;

        /* Stock's own turn (mouse angle, frozen models) and centre column, only resized and lifted. */
        return new Matrix4f()
            .translation(stock.m30(), y, stock.m32())
            .scale(k / stockScale)
            .mul(turn);
    }

    /** Follow the same attachment order as renderBodyParts, without posing or mutating shared models. */
    private static void collectForm(FormRenderer<?> renderer, ModelInstance model, Matrix4f parent,
                                    Bounds result, float transition, boolean root)
    {
        Form form = renderer.getForm();

        if (!root && !form.visible.get()) return;

        Matrix4f matrix = new Matrix4f(parent).mul(renderer.createEvaluatedTransform(transition).createMatrix());
        Bounds geometry = null;
        Bounds destination = result;
        Vector3f facingOrigin = null;
        if (renderer.isPreviewCameraFacing())
        {
            /* Rendering replaces this branch's rotation with the camera axes, including
             * the attachments drawn afterwards. Enclose the entire branch about its pivot. */
            facingOrigin = matrix.getTranslation(new Vector3f());
            matrix = new Matrix4f().scaling(matrix.getScale(new Vector3f()));
            result = new Bounds();
        }

        if (model != null && model.model != null)
        {
            matrix.scale(model.getScale());
            /* Nested models use render3D, whose model frame starts with this turn. */
            if (!root) matrix.rotateY(MathUtils.PI);
            geometry = bounds(model.model);
            result.add(geometry, matrix);
        }
        else
        {
            AABB local = renderer.getPreviewBounds();
            if (local != null)
            {
                Bounds box = new Bounds();
                box.add((float) local.x, (float) local.y, (float) local.z);
                box.add((float) local.maxX(), (float) local.maxY(), (float) local.maxZ());
                result.add(box, matrix);
            }
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            Form child = part.getForm();
            if (child == null || !child.visible.get()) continue;
            FormRenderer<?> childRenderer = FormUtilsClient.getRenderer(child);
            if (childRenderer == null) continue;

            Matrix4f attachment = new Matrix4f(matrix);
            if (renderer instanceof ModelFormRenderer)
            {
                Matrix4f bone = part.filterBoneMatrix(geometry == null ? null : geometry.bones.get(part.bone.get()));
                if (bone == null) attachment.rotateY(MathUtils.PI);
                else attachment.mul(bone);
            }
            attachment.mul(part.transform.get().createMatrix());

            ModelInstance childModel = childRenderer instanceof ModelFormRenderer models ? models.getModel() : null;
            collectForm(childRenderer, childModel, attachment, result, transition, false);
        }
        if (facingOrigin != null && !result.empty)
        {
            float x = Math.max(Math.abs(result.min.x), Math.abs(result.max.x));
            float y = Math.max(Math.abs(result.min.y), Math.abs(result.max.y));
            float z = Math.max(Math.abs(result.min.z), Math.abs(result.max.z));
            float radius = (float) Math.sqrt(x * x + y * y + z * z);
            destination.add(facingOrigin.x - radius, facingOrigin.y - radius, facingOrigin.z - radius);
            destination.add(facingOrigin.x + radius, facingOrigin.y + radius, facingOrigin.z + radius);
        }
    }

    private static Bounds bounds(IModel model)
    {
        long now = System.currentTimeMillis();
        Bounds bounds = CACHE.get(model);

        if (bounds == null || now - bounds.time > REFRESH_MS)
        {
            bounds = new Bounds();

            if (model instanceof Model cubic)
            {
                Matrix4f root = new Matrix4f();

                for (ModelGroup group : cubic.topGroups)
                {
                    collect(group, root, bounds);
                }

                bounds.pixelsToBlocks();
            }
            else if (model instanceof BOBJModel bobj)
            {
                if (bobj.getArmature() != null)
                {
                    for (var bone : bobj.getArmature().orderedBones)
                    {
                        bounds.bones.put(bone.name, new Matrix4f().rotateY(MathUtils.PI).mul(bone.boneMat));
                    }
                }
                for (BOBJLoader.CompiledData mesh : bobj.getMeshes())
                {
                    float[] positions = mesh.posData;

                    for (int i = 0; i + 2 < positions.length; i += 3)
                    {
                        bounds.add(positions[i], positions[i + 1], positions[i + 2]);
                    }
                }
            }

            bounds.time = now;
            CACHE.put(model, bounds);
        }

        return bounds;
    }

    /** One group's rest-pose geometry in the model's pixel space, the way ICubicRenderer places it. */
    private static void collect(ModelGroup group, Matrix4f parent, Bounds bounds)
    {
        Transform rest = group.initial;
        Vector3f pivot = rest.translate;
        Matrix4f matrix = new Matrix4f(parent).translate(pivot);

        if (rest.rotationMode == Transform.RotationMode.QUATERNION)
        {
            matrix.rotate(rest.quat);
        }
        else if (rest.rotate.x != 0F || rest.rotate.y != 0F || rest.rotate.z != 0F)
        {
            matrix.rotate(Matrices.toLocalRotationZYXDegrees(rest.rotate));
        }

        matrix.scale(rest.scale).translate(-pivot.x, -pivot.y, -pivot.z);

        Matrix4f attachment = new Matrix4f(matrix).translate(pivot);
        attachment.setTranslation(attachment.m30() / 16F, attachment.m31() / 16F, attachment.m32() / 16F);
        bounds.bones.put(group.id, attachment.rotateY(MathUtils.PI));

        if (group.visible)
        {
            Vector3f point = new Vector3f();

            for (ModelCube cube : group.cubes)
            {
                Quaternionf rotation = rotation(cube.rotate);

                for (int i = 0; i < 8; i++)
                {
                    point.set(
                        (i & 1) == 0 ? cube.origin.x - cube.inflate : cube.origin.x + cube.size.x + cube.inflate,
                        (i & 2) == 0 ? cube.origin.y - cube.inflate : cube.origin.y + cube.size.y + cube.inflate,
                        (i & 4) == 0 ? cube.origin.z - cube.inflate : cube.origin.z + cube.size.z + cube.inflate
                    );

                    bounds.add(matrix, point, rotation, cube.pivot);
                }
            }

            for (ModelMesh mesh : group.meshes)
            {
                Quaternionf rotation = rotation(mesh.rotate);

                for (Vector3f vertex : mesh.baseData.vertices)
                {
                    /* Loaded mesh vertices are already in pixels with the origin added (ModelVertex
                     * divides by 16 only when drawing), despite ModelGroup's bounds saying blocks. */
                    bounds.add(matrix, point.set(vertex), rotation, mesh.origin);
                }
            }
        }

        for (ModelGroup child : group.children)
        {
            collect(child, matrix, bounds);
        }
    }

    private static Quaternionf rotation(Vector3f rotate)
    {
        return rotate.x == 0F && rotate.y == 0F && rotate.z == 0F ? null : Matrices.toLocalRotationZYXDegrees(rotate);
    }

    private static final class Bounds
    {
        private final Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        private final Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        private final Map<String, Matrix4f> bones = new HashMap<>();
        private boolean empty = true;
        private long time;

        private void add(Bounds bounds, Matrix4f matrix)
        {
            if (bounds.empty) return;
            Vector3f point = new Vector3f();
            for (int i = 0; i < 8; i++)
            {
                point.set((i & 1) == 0 ? bounds.min.x : bounds.max.x,
                    (i & 2) == 0 ? bounds.min.y : bounds.max.y,
                    (i & 4) == 0 ? bounds.min.z : bounds.max.z);
                matrix.transformPosition(point);
                this.add(point.x, point.y, point.z);
            }
        }

        private void add(Matrix4f matrix, Vector3f point, Quaternionf rotation, Vector3f pivot)
        {
            if (rotation != null)
            {
                rotation.transform(point.sub(pivot)).add(pivot);
            }

            matrix.transformPosition(point);
            this.add(point.x, point.y, point.z);
        }

        private void add(float x, float y, float z)
        {
            this.min.set(Math.min(this.min.x, x), Math.min(this.min.y, y), Math.min(this.min.z, z));
            this.max.set(Math.max(this.max.x, x), Math.max(this.max.y, y), Math.max(this.max.z, z));
            this.empty = false;
        }

        private void pixelsToBlocks()
        {
            this.min.div(16F);
            this.max.div(16F);
        }

        /** Furthest reach from the turning axis; corners of the box, so it holds at every angle. */
        private float radius(float scaleX, float scaleZ)
        {
            float x = Math.max(Math.abs(this.min.x), Math.abs(this.max.x)) * scaleX;
            float z = Math.max(Math.abs(this.min.z), Math.abs(this.max.z)) * scaleZ;

            return (float) Math.sqrt(x * x + z * z);
        }
    }
}
