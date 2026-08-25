package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.cubic.physics.PhysicsControls;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormMaterial;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.core.ValueBoneConstraint;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.PoseBones;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every track a form tree offers, in the order a timeline shows them.
 *
 * <p>What tracks a form has is a question about the form. It used to be answered by whoever was
 * drawing a timeline: the replay editor walked the form tree one way, the animation state editor
 * repeated the walk with a slightly different subset, and the per-form track filter built a throwaway
 * {@link FormProperties} to ask a third time. Each knew a different set of kinds — which is why the
 * state editor never showed bones or materials, and why the filter listed tracks the timeline didn't.</p>
 *
 * <p>The catalog answers once, as descriptors. A timeline turns those into rows; it no longer decides
 * what exists.</p>
 */
public class TrackCatalog
{
    /** Hues bone tracks are coloured from, one per parent bone, so siblings share a colour. */
    private static final int BONE_TRACK_HUE_COUNT = 12;

    /** Body part anchors are not worth animating, and the timeline has always hidden them. */
    private static final String ANCHOR = "anchor";

    /**
     * The catalog of a form nothing is animating yet — every track it <em>could</em> have, with no
     * channels behind them. Answers "what tracks does this form have" for the per-form track filter,
     * which used to build a throwaway {@link FormProperties} to ask.
     */
    public static List<TrackDescriptor> of(Form root)
    {
        return of(root, null);
    }

    public static List<TrackDescriptor> of(Form root, FormProperties properties)
    {
        List<TrackDescriptor> tracks = new ArrayList<>();

        collect(root, root, "", properties, tracks);

        return tracks;
    }

    /**
     * The catalog laid out the way a timeline draws it: every track that folds under another one
     * follows it directly, so unfolding a parent reveals rows that are already in the right place.
     * A track whose parent is not in the list (a form that has no texture track of its own, say)
     * keeps its position rather than disappearing.
     */
    public static List<TrackDescriptor> ordered(List<TrackDescriptor> tracks)
    {
        Map<TrackId, List<TrackDescriptor>> children = new HashMap<>();
        Set<TrackId> present = new HashSet<>();

        for (TrackDescriptor track : tracks)
        {
            present.add(track.id());

            if (track.parent() != null)
            {
                children.computeIfAbsent(track.parent(), (k) -> new ArrayList<>()).add(track);
            }
        }

        List<TrackDescriptor> out = new ArrayList<>();

        for (TrackDescriptor track : tracks)
        {
            /* Roots only — everything else is reached from its parent, however deep it sits. A bone
             * hangs off a bone that hangs off a bone, so anything that only took direct children
             * would drop the whole skeleton below the first joint. */
            if (track.parent() == null || !present.contains(track.parent()))
            {
                append(track, children, out);
            }
        }

        return out;
    }

    private static void append(TrackDescriptor track, Map<TrackId, List<TrackDescriptor>> children, List<TrackDescriptor> out)
    {
        out.add(track);

        for (TrackDescriptor child : children.getOrDefault(track.id(), List.of()))
        {
            append(child, children, out);
        }
    }

    private static void collect(Form root, Form form, String path, FormProperties properties, List<TrackDescriptor> out)
    {
        if (form == null)
        {
            return;
        }

        properties(root, form, path, properties, out);

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            materials(modelForm, model, path, properties, out);
            bones(modelForm, model, path, properties, out);
            ik(modelForm, model, path, properties, out);
            physics(modelForm, path, properties, out);
        }

        /* By the part's stable id, never by its position: the address a track is stored under is
         * built the same way (see FormUtils.getPath), so listing by index would offer tracks whose
         * paths resolve to nothing. */
        for (BodyPart part : form.parts.getAllTyped())
        {
            collect(root, part.getForm(), StringUtils.combinePaths(path, part.getId()), properties, out);
        }
    }

    /**
     * A track's title: what the form is called, then what the track is. Deliberately NOT built from
     * the form path — that is an address of stable ids, unreadable by design (and before them, a
     * list position that moved when parts were reordered).
     */
    private static String titled(Form form, String rest)
    {
        String label = form == null ? "" : form.getTrackLabel();

        return label.isEmpty() ? rest : label + FormUtils.PATH_SEPARATOR + rest;
    }

    /** The replay's channel for this track, made if absent; null when asked without a replay. */
    private static KeyframeChannel channel(FormProperties properties, TrackId id)
    {
        return properties == null ? null : properties.getOrCreate(id);
    }

    /* The form's own properties */

    private static void properties(Form root, Form form, String path, FormProperties properties, List<TrackDescriptor> out)
    {
        for (BaseValue value : form.getAll())
        {
            if (!value.isVisible())
            {
                continue;
            }

            String name = value.getId();

            /* The root form's own anchor is animatable (it is what parents a replay to another one);
             * a body part's anchor is what glues it to its parent, and animating that is meaningless. */
            if (ANCHOR.equals(name) && !path.isEmpty())
            {
                continue;
            }

            /* Only a property that can hold keyframes is a track — the rest of a form's values are
             * static settings. Asked of the value itself, so it holds with or without a replay. */
            if (!(value instanceof BaseKeyframeFactoryValue))
            {
                continue;
            }

            TrackId id = TrackId.property(path, name);
            KeyframeChannel channel = properties == null ? null : properties.getOrCreate(root, id);

            if (channel == null && properties != null)
            {
                continue;
            }

            BaseValueBasic property = FormUtils.getProperty(root, id.toKey());
            TrackDescriptor track = new TrackDescriptor(id, channel, form, IKey.constant(form.getTrackName(id.toKey())),
                TrackStyle.icon(name), TrackStyle.color(name), property);

            if (TrackId.MATERIAL_PROP_OVERLAY.equals(name))
            {
                /* A fresh overlay keyframe must visibly tint — the neutral value has zero strength,
                 * and a keyframe that changes nothing reads as broken. */
                track = track.seed(() -> opaqueOverlaySeed(property.get() instanceof Color color ? color : null));
            }

            out.add(track);
        }
    }

    /* Bones */

    private static void bones(ModelForm modelForm, ModelInstance model, String path, FormProperties properties, List<TrackDescriptor> out)
    {
        if (!modelForm.boneTracks.get() || model == null)
        {
            return;
        }

        IModel iModel = model.model;
        TrackId pose = TrackId.property(path, FormProperties.POSE_PROPERTY);
        Map<String, Integer> parentToColor = new HashMap<>();
        Set<String> shown = new HashSet<>();
        int[] hue = {0};

        for (String bone : iModel.getGroupKeysInHierarchyOrder())
        {
            if (PoseBones.isHidden(model.getDisabledBones(), bone))
            {
                continue;
            }

            shown.add(bone);

            String parent = iModel.getParentGroupKey(bone);
            int color = parentToColor.computeIfAbsent(parent, (p) ->
                Colors.HSVtoRGB((hue[0]++ % BONE_TRACK_HUE_COUNT) / (float) BONE_TRACK_HUE_COUNT, 0.7F, 0.7F).getRGBColor());

            TrackId id = TrackId.bone(path, bone);
            String title = titled(modelForm, bone);

            /* A bone hangs off the bone it hangs off in the model, so folding an arm folds the whole
             * arm. The root of the skeleton hangs off the form's pose track, which is the thing all
             * of them are contributions to. */
            String ancestor = nearestShown(iModel, shown, parent);

            /* No icon: a skeleton is most of the rows in the timeline, and the same limb icon on every
             * one of them is a column of noise. Their colour already groups them by parent. */
            out.add(new TrackDescriptor(id, channel(properties, id), modelForm, IKey.constant(title),
                null, color, new ValueTransform(id.toKey(), new PoseTransform()))
                .under(ancestor == null ? pose : TrackId.bone(path, ancestor)));

            boneConstraint(modelForm, path, bone, id, color, properties, out);
        }
    }

    /**
     * The bone's rotation-limits track, folding under the bone's own row. Listed like the solver
     * tracks are — only where it is set up: a bone whose constraint is enabled statically, or whose
     * replay already holds the track. Every bone CAN be limited, but a row per bone would double
     * the skeleton, which is already most of the timeline.
     */
    private static void boneConstraint(ModelForm modelForm, String path, String bone, TrackId boneTrack, int color, FormProperties properties, List<TrackDescriptor> out)
    {
        FormBone formBone = modelForm.bones.getBone(bone);
        boolean enabled = formBone != null && formBone.constraints.get().enabled;
        TrackId id = TrackId.boneConstraint(path, bone);

        if (!enabled && (properties == null || !properties.has(id)))
        {
            return;
        }

        String title = titled(modelForm, bone + FormUtils.PATH_SEPARATOR + "constraints");
        BaseValueBasic property = formBone != null
            ? formBone.constraints
            : new ValueBoneConstraint(id.toKey(), new BoneConstraint());

        out.add(new TrackDescriptor(id, channel(properties, id), modelForm, IKey.constant(title),
            Icons.LOCKED, color, property)
            .seed(() -> formBone != null ? formBone.constraints.get().copy() : new BoneConstraint())
            .under(boneTrack));
    }

    /**
     * The closest bone above this one that the timeline is actually showing. A hidden bone is not a
     * row, so its children would have nothing to hang off; they attach to its nearest shown ancestor
     * instead of falling out of the tree.
     */
    private static String nearestShown(IModel model, Set<String> shown, String bone)
    {
        String current = bone;

        while (current != null && !current.isEmpty())
        {
            if (shown.contains(current))
            {
                return current;
            }

            current = model.getParentGroupKey(current);
        }

        return null;
    }

    /* Materials */

    /**
     * The whole-form PBR sliders (which every model has, even a single-material one), plus — for a
     * real material set — one texture track and the appearance tracks per material. All of them fold
     * under the form's own texture track, which is why their titles carry no {@code material/}
     * segment: the track they fold under already says it.
     */
    private static void materials(ModelForm modelForm, ModelInstance model, String path, FormProperties properties, List<TrackDescriptor> out)
    {
        if (model == null)
        {
            return;
        }

        TrackId texture = TrackId.property(path, "texture");

        /* The whole-form material level (the "" key the material tab writes to when no material is
         * selected) owns the PBR sliders of every model — including single-material ones, which have
         * no per-material tracks at all. */
        pbr(modelForm, path, "", modelForm.materials.getMaterial(""), texture, properties, out);

        /* A model with at most one material ignores the material system entirely (its single texture
         * is driven by form.texture), so it exposes no per-material tracks — see the renderer. */
        if (model.materials.size() <= 1)
        {
            return;
        }

        for (String material : model.materials)
        {
            if (material == null || material.isEmpty())
            {
                continue;
            }

            TrackId id = TrackId.materialTexture(path, material);

            /* Seed the track's value with the material's current default texture (editor pick, else
             * folder/Kd, else the form/model default) so a new keyframe starts there instead of null —
             * the texture picker then opens at that texture rather than at the root. */
            Link fallback = modelForm.materialTextures.getLink(material);

            if (fallback == null)
            {
                fallback = model.getMaterialTexture(material, model.getTexture());
            }

            out.add(new TrackDescriptor(id, channel(properties, id), modelForm, IKey.constant(materialTitle(modelForm, material)),
                Icons.MATERIAL, Colors.BLUE, new ValueLink(id.toKey(), fallback))
                .under(texture));

            /* A material's own properties fold under that material, not under the form's texture:
             * one material's sliders have nothing to do with the next material's. */
            materialProps(modelForm, path, material, id, properties, out);
        }
    }

    /**
     * A material's appearance tracks. Each is layered over the static material value at playback, and
     * seeded from it so a fresh keyframe starts at what the material currently shows — except the
     * colour overlay, which seeds at FULL strength: the neutral value has zero strength, and a
     * keyframe that changes nothing reads as broken.
     */
    private static void materialProps(ModelForm modelForm, String path, String material, TrackId parent, FormProperties properties, List<TrackDescriptor> out)
    {
        FormMaterial staticMaterial = modelForm.materials.getMaterial(material);
        String prefix = material + FormUtils.PATH_SEPARATOR;

        TrackId color = TrackId.materialProp(path, material, TrackId.MATERIAL_PROP_COLOR);

        out.add(prop(modelForm, color, parent, materialTitle(modelForm, prefix + TrackId.MATERIAL_PROP_COLOR), Icons.BUCKET,
            new ValueColor(color.toKey(), staticMaterial == null ? Color.white() : staticMaterial.color.get().copy()), properties));

        TrackId overlay = TrackId.materialProp(path, material, TrackId.MATERIAL_PROP_OVERLAY);
        ValueColor overlayValue = new ValueColor(overlay.toKey(), staticMaterial == null ? new Color(1F, 1F, 1F, 0F) : staticMaterial.overlayColor.get().copy());

        out.add(prop(modelForm, overlay, parent, materialTitle(modelForm, prefix + TrackId.MATERIAL_PROP_OVERLAY), Icons.COLOR, overlayValue, properties)
            .seed(() -> opaqueOverlaySeed(overlayValue.get())));

        TrackId lighting = TrackId.materialProp(path, material, TrackId.MATERIAL_PROP_LIGHTING);

        out.add(prop(modelForm, lighting, parent, materialTitle(modelForm, prefix + TrackId.MATERIAL_PROP_LIGHTING), Icons.LIGHT,
            new ValueFloat(lighting.toKey(), staticMaterial == null ? 1F : staticMaterial.lighting.get()), properties));

        TrackId culling = TrackId.materialProp(path, material, TrackId.MATERIAL_PROP_CULLING);

        out.add(prop(modelForm, culling, parent, materialTitle(modelForm, prefix + TrackId.MATERIAL_PROP_CULLING), Icons.CONVERT,
            new ValueInt(culling.toKey(), staticMaterial == null ? 0 : staticMaterial.culling.get()), properties));

        pbr(modelForm, path, material, staticMaterial, parent, properties, out);
    }

    /**
     * The five PBR slider tracks of a material level — used both per material and for the whole-form
     * level (empty material name), which is where a single-material model's sliders live.
     */
    private static void pbr(ModelForm modelForm, String path, String material, FormMaterial staticMaterial, TrackId parent, FormProperties properties, List<TrackDescriptor> out)
    {
        /* The whole-form level names nothing: its sliders read as the form's own, which is what they
         * are. A material's sliders keep the material name in front of them. */
        String prefix = material.isEmpty() ? "" : material + FormUtils.PATH_SEPARATOR;

        for (String slider : new String[] {
            TrackId.MATERIAL_PROP_SMOOTHNESS, TrackId.MATERIAL_PROP_METALLIC, TrackId.MATERIAL_PROP_SSS,
            TrackId.MATERIAL_PROP_PIXEL_EMISSION, TrackId.MATERIAL_PROP_RELIEF })
        {
            TrackId id = TrackId.materialProp(path, material, slider);
            float value = staticMaterial == null ? 0F : pbrSlider(staticMaterial, slider);

            out.add(prop(modelForm, id, parent, materialTitle(modelForm, prefix + slider), Icons.MATERIAL,
                new ValueFloat(id.toKey(), value), properties));
        }
    }

    private static TrackDescriptor prop(ModelForm modelForm, TrackId id, TrackId parent, String title, mchorse.bbs_mod.ui.utils.icons.Icon icon, BaseValueBasic value, FormProperties properties)
    {
        return new TrackDescriptor(id, channel(properties, id), modelForm, IKey.constant(title),
            icon, TrackStyle.color(id.property()), value).under(parent);
    }

    /**
     * Title of a material track. The form path stays (a body part's tracks must say whose they are,
     * exactly like the bone tracks), everything above the material itself goes — the texture track
     * these fold under already carries it.
     */
    private static String materialTitle(Form form, String name)
    {
        return titled(form, name);
    }

    private static float pbrSlider(FormMaterial material, String property)
    {
        return switch (property)
        {
            case TrackId.MATERIAL_PROP_SMOOTHNESS -> material.smoothness.get();
            case TrackId.MATERIAL_PROP_METALLIC -> material.metallic.get();
            case TrackId.MATERIAL_PROP_SSS -> material.sss.get();
            case TrackId.MATERIAL_PROP_PIXEL_EMISSION -> material.pixelEmission.get();
            case TrackId.MATERIAL_PROP_RELIEF -> material.relief.get();
            default -> 0F;
        };
    }

    /**
     * The colour overlay's new-keyframe value: the current one when it is already visible, at full
     * strength otherwise — a fresh keyframe must tint, not silently do nothing.
     */
    public static Color opaqueOverlaySeed(Color current)
    {
        Color seed = current == null ? new Color(1F, 1F, 1F, 1F) : current.copy();

        if (seed.a <= 0F)
        {
            seed.a = 1F;
        }

        return seed;
    }

    /* IK */

    private static void ik(ModelForm modelForm, ModelInstance model, String path, FormProperties properties, List<TrackDescriptor> out)
    {
        if (model == null)
        {
            return;
        }

        model.form = modelForm;

        List<String> controllers = ModelIKRuntime.getControllers(model);

        if (!controllers.isEmpty())
        {
            /* One controls track per form: a single track whose value holds the per-chain scalars
             * (weight, softness, pole, enabled), driving the bones' own `ik` properties at playback.
             * It has no form property behind it, so the chains are listed from the form itself. */
            TrackId id = TrackId.ikControls(path);

            out.add(new TrackDescriptor(id, channel(properties, id), modelForm,
                IKey.constant(titled(modelForm, "ik")), Icons.IK, Colors.YELLOW, null)
                .seed(() -> ikControls(modelForm)));
        }

        for (String controller : controllers)
        {
            if (controller != null && !controller.isEmpty())
            {
                TrackId id = TrackId.ikTarget(path, controller);

                out.add(target(modelForm, id, properties, titled(modelForm, "ik/" + controller), Colors.CYAN));
            }
        }

        for (String controller : ModelIKRuntime.getPoleControllers(model))
        {
            if (controller != null && !controller.isEmpty())
            {
                TrackId id = TrackId.poleTarget(path, controller);

                out.add(target(modelForm, id, properties, titled(modelForm, "pole/" + controller), Colors.ORANGE));
            }
        }
    }

    /* Physics */

    private static void physics(ModelForm modelForm, String path, FormProperties properties, List<TrackDescriptor> out)
    {
        boolean hasChains = false;

        for (BaseValue value : modelForm.bones.getAll())
        {
            if (value instanceof FormBone bone && bone.hasPhysicsChain())
            {
                hasChains = true;

                break;
            }
        }

        if (!hasChains)
        {
            return;
        }

        TrackId controls = TrackId.physicsControls(path);

        out.add(new TrackDescriptor(controls, channel(properties, controls), modelForm,
            IKey.constant(titled(modelForm, "physics")), Icons.PHYSICS, Colors.GREEN, null)
            .seed(() -> physicsControls(modelForm)));

        /* The wind is global to the form, so — unlike the physics controls — it is not keyed by chain. */
        TrackId wind = TrackId.windControls(path);

        out.add(new TrackDescriptor(wind, channel(properties, wind), modelForm,
            IKey.constant(titled(modelForm, "wind")), Icons.ARROW_RIGHT, Colors.CYAN, null)
            .seed(() -> modelForm.wind.get().copy()));

        for (BaseValue value : modelForm.bones.getAll())
        {
            if (!(value instanceof FormBone bone) || !bone.hasPhysicsChain())
            {
                continue;
            }

            TrackId id = TrackId.physicsTarget(path, bone.getId());

            out.add(target(modelForm, id, properties, titled(modelForm, "physics/" + bone.getId()), Colors.MAGENTA));
        }
    }

    /** A fully populated IK-controls value seeded from the bones' own `ik` properties, so a fresh keyframe matches what the editor shows instead of an empty container that drifts to defaults. */
    private static IKControls ikControls(ModelForm modelForm)
    {
        IKControls controls = new IKControls();

        for (BaseValue value : modelForm.bones.getAll())
        {
            if (value instanceof FormBone bone && bone.hasChain() && bone.ik.getOriginalValue().enabled)
            {
                controls.get(bone.getId()).copy(bone.ik.getOriginalValue());
            }
        }

        return controls;
    }

    /** A physics-controls value seeded from the bones' own `physics` properties, one entry per chain root. */
    private static PhysicsControls physicsControls(ModelForm modelForm)
    {
        PhysicsControls controls = new PhysicsControls();

        for (BaseValue value : modelForm.bones.getAll())
        {
            if (value instanceof FormBone bone && bone.hasPhysicsChain())
            {
                controls.get(bone.getId()).copy(bone.physics.getOriginalValue());
            }
        }

        return controls;
    }

    private static TrackDescriptor target(ModelForm modelForm, TrackId id, FormProperties properties, String title, int color)
    {
        return new TrackDescriptor(id, channel(properties, id), modelForm, IKey.constant(title),
            id.is(TrackKind.PHYSICS_TARGET) ? Icons.PHYSICS : Icons.IK, color, null);
    }
}
