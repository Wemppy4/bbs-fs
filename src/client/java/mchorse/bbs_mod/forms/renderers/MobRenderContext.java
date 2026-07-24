package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pose state scoped to one MobForm entity render.
 *
 * <p>Contexts restore the previous value when closed so nested entity renders cannot leak pose
 * state into their caller. The part map is identity based and lives only for the duration of the
 * render, matching the renderer/model instances discovered for that call.</p>
 */
public final class MobRenderContext implements AutoCloseable
{
    private static final ThreadLocal<MobRenderContext> CURRENT = new ThreadLocal<>();

    private final MobRenderContext previous;
    private final VanillaRendererBones.Discovery discovery;
    private final List<VanillaBoneHierarchy.Hierarchy> hierarchies;
    private final IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> bones;
    private final IdentityHashMap<ModelPart, PoseTransform> transforms;
    private final IdentityHashMap<ModelPart, Vector3f> rotationOffsets = new IdentityHashMap<>();
    private final IdentityHashMap<ModelPart, Matrix4f> origins = new IdentityHashMap<>();
    private final IdentityHashMap<ModelPart, Integer> pickingOffsets = new IdentityHashMap<>();
    private final Map<String, Integer> pickingIds = new HashMap<>();
    private final MatrixCache matrices = new MatrixCache();
    private final List<String> pickedBoneIds = new ArrayList<>();
    private final Color color;
    private final Matrix4f base;
    private final Matrix4f inverseBase;
    private final boolean picking;
    private final boolean incrementPicking;
    private boolean closed;

    private MobRenderContext(Object renderer, Pose pose, Pose overlay, Color color, Matrix4f base, boolean picking, boolean incrementPicking)
    {
        this.previous = CURRENT.get();
        VanillaRendererBones.Discovery discovery = VanillaRendererBones.discover(renderer);
        BoneHierarchy hierarchy = discovery.getBoneHierarchy();
        Pose mergedPose = pose.copy();
        Pose mergedOverlay = overlay.copy();

        hierarchy.migratePose(mergedPose);
        hierarchy.migratePose(mergedOverlay);
        mergedPose = mergePose(mergedPose, mergedOverlay);

        this.discovery = discovery;
        this.hierarchies = discovery.getRuntimeHierarchies();
        this.bones = resolveBones(discovery, this.hierarchies);
        this.transforms = resolveTransforms(discovery, mergedPose);
        this.color = color == null ? Color.white() : color.copy();
        this.base = base == null ? null : new Matrix4f(base);
        this.inverseBase = base == null || Math.abs(base.determinant()) < 1.0E-8F
            ? null
            : new Matrix4f(base).invert();
        this.picking = picking;
        this.incrementPicking = incrementPicking;

        CURRENT.set(this);
    }

    public static MobRenderContext push(Object renderer, Pose pose, Pose overlay, Color color)
    {
        return new MobRenderContext(renderer, pose, overlay, color, null, false, false);
    }

    public static MobRenderContext push(Object renderer, Pose pose, Pose overlay, Color color, Matrix4f base, boolean picking, boolean incrementPicking)
    {
        return new MobRenderContext(renderer, pose, overlay, color, base, picking, incrementPicking);
    }

    public static PoseTransform getTransform(ModelPart part)
    {
        MobRenderContext context = CURRENT.get();

        return context == null ? null : context.transforms.get(part);
    }

    public static boolean isActive()
    {
        return CURRENT.get() != null;
    }

    public static Color getColor(ModelPart part)
    {
        MobRenderContext context = CURRENT.get();

        if (context == null)
        {
            return null;
        }

        if (context.bones.containsKey(part))
        {
            return context.color;
        }

        VanillaBoneHierarchy.Hierarchy hierarchy = VanillaBoneHierarchy.getHierarchy(part).orElse(null);

        return hierarchy == null || !context.hierarchies.contains(hierarchy) ? null : context.color;
    }

    public static boolean isTracked(ModelPart part)
    {
        MobRenderContext context = CURRENT.get();

        return context != null && context.bones.containsKey(part);
    }

    public static void captureOrigin(ModelPart part, Matrix4f matrix)
    {
        MobRenderContext context = CURRENT.get();

        if (context != null && context.inverseBase != null && context.bones.containsKey(part))
        {
            context.origins.put(part, context.toLocal(matrix));
        }
    }

    public static void captureRotationOffset(ModelPart part, float pitch, float yaw, float roll)
    {
        MobRenderContext context = CURRENT.get();

        if (context != null && context.bones.containsKey(part))
        {
            context.rotationOffsets.put(part, new Vector3f(pitch, yaw, roll));
        }
    }

    public static void captureMatrix(ModelPart part, Matrix4f matrix)
    {
        MobRenderContext context = CURRENT.get();

        if (context == null || context.inverseBase == null)
        {
            return;
        }

        VanillaBoneHierarchy.Bone bone = context.bones.get(part);
        Matrix4f origin = context.origins.get(part);

        if (bone != null && origin != null)
        {
            if (!context.matrices.has(bone.getId()))
            {
                context.matrices.put(bone.getId(), context.toLocal(matrix), origin, context.rotationOffsets.get(part));
            }
        }
    }

    /**
     * Returns the lightmap-U offset consumed by the model picker shader, or {@code -1} outside a
     * picking pass. Offset zero is reserved for non-bone entity geometry.
     */
    public static int getPickingOffset(ModelPart part)
    {
        MobRenderContext context = CURRENT.get();

        if (context == null || !context.picking)
        {
            return -1;
        }

        if (!context.incrementPicking)
        {
            return 0;
        }

        VanillaBoneHierarchy.Bone bone = context.bones.get(part);

        if (bone == null || part.isEmpty())
        {
            return 0;
        }

        Integer offset = context.pickingOffsets.get(part);

        if (offset == null)
        {
            offset = context.pickingIds.get(bone.getId());

            if (offset == null)
            {
                offset = context.pickedBoneIds.size() + 1;
                context.pickingIds.put(bone.getId(), offset);
                context.pickedBoneIds.add(bone.getId());
            }

            context.pickingOffsets.put(part, offset);
        }

        return offset;
    }

    public MatrixCache getMatrices()
    {
        return this.matrices;
    }

    public List<String> getPickedBoneIds()
    {
        return List.copyOf(this.pickedBoneIds);
    }

    /**
     * Completes matrices for models which vanilla skipped because their variant, equipment or
     * render condition was absent.
     *
     * <p>Inactive equipment-style feature layers (armour overlays, …) reuse the same relative part
     * paths as their owning context model. When the feature's path set is a subset of a context
     * capture, each part inherits that context pose so gizmos track the body. Independent feature
     * models that only share an attachment point (shoulder pets, …) keep their own local tree and
     * are rooted under the context model instead — path-name collisions such as {@code body} are
     * deliberately not inherited. Renderer variant containers still share captures only within
     * their group. MobForm's transform is applied through the regular ModelPart hook.</p>
     */
    public void completeMatrices()
    {
        if (this.base == null)
        {
            return;
        }

        IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Map<String, CapturedBone>> capturedByHierarchy = new IdentityHashMap<>();
        Map<List<String>, Map<String, CapturedBone>> capturedByStructure = new HashMap<>();
        IdentityHashMap<Object, Map<String, CapturedBone>> capturedByVariant = new IdentityHashMap<>();
        Matrix4f modelRoot = null;

        for (VanillaBoneHierarchy.Hierarchy hierarchy : this.hierarchies)
        {
            for (VanillaBoneHierarchy.Bone bone : hierarchy.getBones())
            {
                ModelPart part = bone.getPart();
                VanillaBoneHierarchy.Bone canonicalBone = this.discovery.getCanonicalBone(bone);

                if (part != null && canonicalBone != null && this.origins.containsKey(part) && this.matrices.has(canonicalBone.getId()))
                {
                    CapturedBone captured = new CapturedBone(part, this.matrices.get(canonicalBone.getId()));

                    this.putCaptured(hierarchy, bone, captured, capturedByHierarchy, capturedByStructure, capturedByVariant);

                    if (modelRoot == null && bone.getParentId() == null)
                    {
                        modelRoot = this.getCapturedParent(captured);
                    }
                }
            }
        }

        for (VanillaBoneHierarchy.Hierarchy hierarchy : this.hierarchies)
        {
            for (VanillaBoneHierarchy.Bone bone : hierarchy.getBones())
            {
                VanillaBoneHierarchy.Bone canonicalBone = this.discovery.getCanonicalBone(bone);

                if (canonicalBone == null || this.matrices.has(canonicalBone.getId()))
                {
                    continue;
                }

                ModelPart part = bone.getPart();
                CapturedBone reference = this.getCapturedReference(
                    hierarchy, bone, capturedByHierarchy, capturedByStructure, capturedByVariant
                );
                Matrix4f parent = this.getFallbackParent(hierarchy, bone, reference, modelRoot);

                if (part == null || parent == null)
                {
                    continue;
                }

                PartTransform original = PartTransform.capture(part);

                if (reference != null && reference.part() != part)
                {
                    part.copyTransform(reference.part());
                }

                MatrixStack stack = new MatrixStack();

                stack.peek().getPositionMatrix().set(this.base).mul(parent);

                try
                {
                    part.rotate(stack);
                }
                finally
                {
                    original.restore(part);
                }

                if (this.matrices.has(canonicalBone.getId()))
                {
                    CapturedBone captured = new CapturedBone(part, this.matrices.get(canonicalBone.getId()));

                    this.putCaptured(hierarchy, bone, captured, capturedByHierarchy, capturedByStructure, capturedByVariant);
                }
            }
        }
    }

    private void putCaptured(
        VanillaBoneHierarchy.Hierarchy hierarchy,
        VanillaBoneHierarchy.Bone bone,
        CapturedBone captured,
        IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Map<String, CapturedBone>> capturedByHierarchy,
        Map<List<String>, Map<String, CapturedBone>> capturedByStructure,
        IdentityHashMap<Object, Map<String, CapturedBone>> capturedByVariant
    )
    {
        capturedByHierarchy.computeIfAbsent(hierarchy, (key) -> new HashMap<>())
            .putIfAbsent(bone.getPath(), captured);

        Object variantGroup = this.discovery.getVariantGroup(hierarchy);

        if (variantGroup != null)
        {
            capturedByVariant.computeIfAbsent(variantGroup, (key) -> new HashMap<>())
                .putIfAbsent(bone.getPath(), captured);
        }

        /* Structure-keyed maps isolate independent primary models that share common leaf names
         * without being related feature layers, and also expose primary / equipment captures for
         * subset-based feature inheritance. */
        capturedByStructure.computeIfAbsent(hierarchy.getStructureKey(), (key) -> new HashMap<>())
            .putIfAbsent(bone.getPath(), captured);
    }

    private CapturedBone getCapturedReference(
        VanillaBoneHierarchy.Hierarchy hierarchy,
        VanillaBoneHierarchy.Bone bone,
        IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Map<String, CapturedBone>> capturedByHierarchy,
        Map<List<String>, Map<String, CapturedBone>> capturedByStructure,
        IdentityHashMap<Object, Map<String, CapturedBone>> capturedByVariant
    )
    {
        Object variantGroup = this.discovery.getVariantGroup(hierarchy);

        if (variantGroup != null)
        {
            Map<String, CapturedBone> variants = capturedByVariant.get(variantGroup);
            CapturedBone fromVariant = variants == null ? null : variants.get(bone.getPath());

            if (fromVariant != null)
            {
                return fromVariant;
            }
        }

        Map<String, CapturedBone> own = capturedByHierarchy.get(hierarchy);
        CapturedBone exact = own == null ? null : own.get(bone.getPath());

        if (exact != null)
        {
            return exact;
        }

        Map<String, CapturedBone> sameStructure = capturedByStructure.get(hierarchy.getStructureKey());
        CapturedBone structureExact = sameStructure == null ? null : sameStructure.get(bone.getPath());

        if (structureExact != null)
        {
            return structureExact;
        }

        CapturedBone fromFeatureContext = this.findCapturedInFeatureContexts(hierarchy, bone.getPath(), capturedByHierarchy);

        if (fromFeatureContext != null)
        {
            return fromFeatureContext;
        }

        /* Inactive equipment layers that are not registered with a feature context still inherit
         * from any rendered structure that fully contains their part paths. */
        return this.findCompatibleCaptured(hierarchy, bone.getPath(), capturedByStructure);
    }

    /**
     * Prefer the feature's declared context model(s). Equipment layers attach to that context and
     * intentionally reuse the same relative part paths. Accidental name collisions with unrelated
     * feature models (for example parrot {@code body} vs player {@code body}) are rejected because
     * the feature path set is not a subset of the context tree.
     */
    private CapturedBone findCapturedInFeatureContexts(
        VanillaBoneHierarchy.Hierarchy hierarchy,
        String path,
        IdentityHashMap<VanillaBoneHierarchy.Hierarchy, Map<String, CapturedBone>> capturedByHierarchy
    )
    {
        CapturedBone matched = null;
        String matchedId = null;

        for (VanillaBoneHierarchy.Hierarchy contextHierarchy : this.discovery.getFeatureContexts(hierarchy))
        {
            if (!isEquipmentStyleFeature(hierarchy, contextHierarchy))
            {
                continue;
            }

            Map<String, CapturedBone> captured = capturedByHierarchy.get(contextHierarchy);
            CapturedBone candidate = captured == null ? null : captured.get(path);

            if (candidate == null)
            {
                continue;
            }

            VanillaBoneHierarchy.Bone canonicalBone = this.discovery.getCanonicalBone(candidate.part());
            String candidateId = canonicalBone == null ? null : canonicalBone.getId();

            if (candidateId == null)
            {
                continue;
            }

            if (matchedId != null && !matchedId.equals(candidateId))
            {
                return null;
            }

            matchedId = candidateId;
            matched = candidate;
        }

        return matched;
    }

    /**
     * Fall back to any captured structure whose path set fully contains this hierarchy's paths.
     * Score by shared path count so a humanoid armour layer prefers the densest matching body tree.
     */
    private CapturedBone findCompatibleCaptured(
        VanillaBoneHierarchy.Hierarchy hierarchy,
        String path,
        Map<List<String>, Map<String, CapturedBone>> capturedByStructure
    )
    {
        List<String> structure = hierarchy.getStructureKey();
        CapturedBone best = null;
        int bestScore = 0;
        String bestId = null;

        for (Map.Entry<List<String>, Map<String, CapturedBone>> entry : capturedByStructure.entrySet())
        {
            if (entry.getKey().equals(structure) || !isPathSubset(structure, entry.getKey()))
            {
                continue;
            }

            CapturedBone candidate = entry.getValue().get(path);

            if (candidate == null)
            {
                continue;
            }

            VanillaBoneHierarchy.Bone canonicalBone = this.discovery.getCanonicalBone(candidate.part());
            String candidateId = canonicalBone == null ? null : canonicalBone.getId();

            if (candidateId == null)
            {
                continue;
            }

            /* Prefer the densest containing tree so a full body model wins over a thinner overlay. */
            int score = entry.getKey().size();

            if (score > bestScore)
            {
                bestScore = score;
                best = candidate;
                bestId = candidateId;
            }
            else if (score == bestScore && bestId != null && !bestId.equals(candidateId))
            {
                /* Ambiguous same-score donors: refuse rather than pick arbitrarily. */
                best = null;
                bestId = null;
            }
        }

        return best;
    }

    /**
     * Equipment / armour layers re-use the context model's relative paths (often as a strict
     * subset). Independent feature models (parrots, held items, …) introduce their own parts and
     * must not inherit coincidentally shared names.
     */
    private static boolean isEquipmentStyleFeature(
        VanillaBoneHierarchy.Hierarchy feature,
        VanillaBoneHierarchy.Hierarchy context
    )
    {
        return isPathSubset(feature.getStructureKey(), context.getStructureKey());
    }

    /**
     * True when every path in {@code feature} also exists in {@code context}. Empty trees never
     * match so accidental root-only models cannot inherit arbitrary poses.
     */
    private static boolean isPathSubset(List<String> feature, List<String> context)
    {
        if (feature.isEmpty() || context.isEmpty())
        {
            return false;
        }

        Set<String> contextPaths = new HashSet<>(context);

        for (String path : feature)
        {
            if (!contextPaths.contains(path))
            {
                return false;
            }
        }

        return true;
    }

    private Matrix4f getFallbackParent(
        VanillaBoneHierarchy.Hierarchy hierarchy,
        VanillaBoneHierarchy.Bone bone,
        CapturedBone reference,
        Matrix4f modelRoot
    )
    {
        if (bone.getParentId() != null)
        {
            VanillaBoneHierarchy.Bone parent = hierarchy.resolve(bone.getParentId()).orElse(null);
            VanillaBoneHierarchy.Bone canonicalParent = this.discovery.getCanonicalBone(parent);

            if (canonicalParent == null)
            {
                return null;
            }

            MatrixCacheEntry entry = this.matrices.get(canonicalParent.getId());

            return entry.matrix() == null ? null : new Matrix4f(entry.matrix());
        }

        if (reference != null)
        {
            return this.getCapturedParent(reference);
        }

        Matrix4f featureParent = this.getFeatureParent(hierarchy, bone);

        if (featureParent != null)
        {
            return featureParent;
        }

        Matrix4f contextRoot = this.getFeatureContextRoot(hierarchy);

        if (contextRoot != null)
        {
            return contextRoot;
        }

        /* A pure renderer variant (boat types, …) can have an external attachment transform which
         * is not represented by its ModelPart tree. Without a rendered sibling or a feature context,
         * the primary model root is not a valid substitute. Feature layers still fall back to the
         * model root so inactive equipment / attachment bones remain gizmo-editable. */
        if (this.discovery.getVariantGroup(hierarchy) != null && this.discovery.getFeatureContexts(hierarchy).isEmpty())
        {
            return null;
        }

        return modelRoot == null ? null : new Matrix4f(modelRoot);
    }

    private Matrix4f getFeatureParent(VanillaBoneHierarchy.Hierarchy hierarchy, VanillaBoneHierarchy.Bone bone)
    {
        String matchedId = null;
        Matrix4f matched = null;
        Class<?> modelType = this.discovery.getModelType(hierarchy);
        boolean skull = modelType != null && SkullBlockEntityModel.class.isAssignableFrom(modelType);

        for (VanillaBoneHierarchy.Hierarchy contextHierarchy : this.discovery.getFeatureContexts(hierarchy))
        {
            /* Path attachment only for equipment-style layers (or skulls, which attach through a
             * known external correction). Independent feature trees fall through to the context root. */
            if (!skull && !isEquipmentStyleFeature(hierarchy, contextHierarchy))
            {
                continue;
            }

            VanillaBoneHierarchy.Bone contextBone = contextHierarchy.resolve(bone.getPath()).orElse(null);
            VanillaBoneHierarchy.Bone canonicalBone = this.discovery.getCanonicalBone(contextBone);

            if (canonicalBone == null)
            {
                continue;
            }

            Matrix4f matrix = this.matrices.get(canonicalBone.getId()).matrix();

            if (matrix != null)
            {
                if (matchedId != null && !matchedId.equals(canonicalBone.getId()))
                {
                    return null;
                }

                matchedId = canonicalBone.getId();
                matched = matrix;
            }
        }

        if (matched == null)
        {
            return null;
        }

        Matrix4f parent = new Matrix4f(matched);

        /* SkullBlockEntityRenderer applies this model-space correction after the feature has
         * attached the skull to its context model. Scale is intentionally omitted: gizmo matrices
         * strip it, while retaining the rotation is required for correct X/Z directions. */
        if (skull)
        {
            parent.rotateY(MathUtils.PI);
        }

        return parent;
    }

    /**
     * When a feature model has no path-compatible capture, place its root under a rendered context
     * root so gizmos remain interactive. Attachment-specific offsets applied by the feature renderer
     * itself are still unavailable without that render pass.
     */
    private Matrix4f getFeatureContextRoot(VanillaBoneHierarchy.Hierarchy hierarchy)
    {
        if (this.discovery.getFeatureContexts(hierarchy).isEmpty())
        {
            return null;
        }

        Matrix4f matched = null;
        String matchedId = null;

        for (VanillaBoneHierarchy.Hierarchy contextHierarchy : this.discovery.getFeatureContexts(hierarchy))
        {
            for (VanillaBoneHierarchy.Bone contextBone : contextHierarchy.getBones())
            {
                if (contextBone.getParentId() != null)
                {
                    continue;
                }

                VanillaBoneHierarchy.Bone canonicalBone = this.discovery.getCanonicalBone(contextBone);
                ModelPart part = contextBone.getPart();

                if (canonicalBone == null || part == null || !this.matrices.has(canonicalBone.getId()))
                {
                    continue;
                }

                MatrixCacheEntry entry = this.matrices.get(canonicalBone.getId());

                if (entry.origin() == null)
                {
                    continue;
                }

                Matrix4f root = this.getCapturedParent(new CapturedBone(part, entry));

                if (root == null)
                {
                    continue;
                }

                if (matchedId != null && !matchedId.equals(canonicalBone.getId()))
                {
                    return null;
                }

                matchedId = canonicalBone.getId();
                matched = root;
            }
        }

        return matched;
    }

    private Matrix4f getCapturedParent(CapturedBone reference)
    {
        if (reference.entry().origin() == null)
        {
            return null;
        }

        ModelPart part = reference.part();
        PoseTransform transform = this.transforms.get(part);
        float pivotX = part.pivotX;
        float pivotY = part.pivotY;
        float pivotZ = part.pivotZ;

        if (transform != null && transform.fix > 0F)
        {
            ModelTransform initial = part.getDefaultTransform();

            pivotX = Lerps.lerp(pivotX, initial.pivotX, transform.fix);
            pivotY = Lerps.lerp(pivotY, initial.pivotY, transform.fix);
            pivotZ = Lerps.lerp(pivotZ, initial.pivotZ, transform.fix);
        }

        if (transform != null)
        {
            pivotX += transform.translate.x;
            pivotY += transform.translate.y;
            pivotZ += transform.translate.z;
        }

        return new Matrix4f(reference.entry().origin()).translate(-pivotX / 16F, -pivotY / 16F, -pivotZ / 16F);
    }

    private Matrix4f toLocal(Matrix4f matrix)
    {
        return new Matrix4f(this.inverseBase).mul(matrix);
    }

    private static Pose mergePose(Pose pose, Pose overlay)
    {
        Pose result = pose.copy();

        for (Map.Entry<String, PoseTransform> entry : overlay.transforms.entrySet())
        {
            PoseTransform transform = result.get(entry.getKey());
            PoseTransform value = entry.getValue();

            if (value.fix != 0F)
            {
                transform.translate.lerp(value.translate, value.fix);
                transform.scale.lerp(value.scale, value.fix);
                transform.rotate.lerp(value.rotate, value.fix);
                transform.rotate2.lerp(value.rotate2, value.fix);
            }
            else
            {
                transform.translate.add(value.translate);
                transform.scale.add(value.scale).sub(1F, 1F, 1F);
                transform.rotate.add(value.rotate);
                transform.rotate2.add(value.rotate2);
            }
        }

        return result;
    }

    private static IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> resolveBones(
        VanillaRendererBones.Discovery discovery,
        List<VanillaBoneHierarchy.Hierarchy> hierarchies
    )
    {
        IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> bones = new IdentityHashMap<>();

        for (VanillaBoneHierarchy.Hierarchy hierarchy : hierarchies)
        {
            for (VanillaBoneHierarchy.Bone bone : hierarchy.getBones())
            {
                ModelPart part = bone.getPart();

                if (part != null)
                {
                    VanillaBoneHierarchy.Bone canonicalBone = discovery.getCanonicalBone(bone);

                    if (canonicalBone != null)
                    {
                        bones.put(part, canonicalBone);
                    }
                }
            }
        }

        return bones;
    }

    private static IdentityHashMap<ModelPart, PoseTransform> resolveTransforms(VanillaRendererBones.Discovery discovery, Pose pose)
    {
        IdentityHashMap<ModelPart, PoseTransform> transforms = new IdentityHashMap<>();

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            for (VanillaBoneHierarchy.Bone bone : discovery.resolveAll(entry.getKey()))
            {
                ModelPart part = bone.getPart();

                if (part != null)
                {
                    transforms.put(part, entry.getValue());
                }
            }
        }

        return transforms;
    }

    @Override
    public void close()
    {
        if (this.closed)
        {
            return;
        }

        this.closed = true;

        if (CURRENT.get() == this)
        {
            if (this.previous == null)
            {
                CURRENT.remove();
            }
            else
            {
                CURRENT.set(this.previous);
            }
        }
    }

    private record CapturedBone(ModelPart part, MatrixCacheEntry entry)
    {}

    private record PartTransform(
        float pivotX, float pivotY, float pivotZ,
        float pitch, float yaw, float roll,
        float scaleX, float scaleY, float scaleZ
    )
    {
        private static PartTransform capture(ModelPart part)
        {
            return new PartTransform(
                part.pivotX, part.pivotY, part.pivotZ,
                part.pitch, part.yaw, part.roll,
                part.xScale, part.yScale, part.zScale
            );
        }

        private void restore(ModelPart part)
        {
            part.pivotX = this.pivotX;
            part.pivotY = this.pivotY;
            part.pivotZ = this.pivotZ;
            part.pitch = this.pitch;
            part.yaw = this.yaw;
            part.roll = this.roll;
            part.xScale = this.scaleX;
            part.yScale = this.scaleY;
            part.zScale = this.scaleZ;
        }
    }
}
