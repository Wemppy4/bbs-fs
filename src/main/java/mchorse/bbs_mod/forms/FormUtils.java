package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FormUtils
{
    public static final String PATH_SEPARATOR = "/";

    public static boolean isPoseProperty(String name)
    {
        return name.startsWith("transform")
            || name.startsWith("pose")
            || name.startsWith("pose_overlay")
            || name.startsWith("shape_keys");
    }

    /**
     * The euler rotation the renderer sums UNDER one pose track's channels for
     * {@code bone} — the contributions of every OTHER pose-valued track of the
     * same form (the pose stack merges per-channel additively; see the model
     * form renderer's pose merge). Feeds the gizmo's
     * {@code GizmoDrag.additiveRotationBase}, so editing an overlay composes
     * its drag deltas at the bone's EFFECTIVE angles instead of the overlay's
     * own near-zero channels. The animator's action channels are not included —
     * like the rest of the drag capture, they are treated as static for the
     * duration of an edit.
     *
     * @return the summed base in radians, or {@code null} when there is no
     *         purely additive base to speak of: {@code editedTrack} doesn't
     *         belong to a pose-stacked form, or any involved bone transform
     *         merges multiplicatively (a non-zero {@code fix} weight lerps, a
     *         quaternion contributor turns the merge into a quaternion product)
     *         — those compositions the drag's parent-frame recovery absorbs on
     *         its own, so a zero base is exactly right for them.
     */
    public static Vector3f additivePoseRotationBase(ValuePose editedTrack, String bone)
    {
        return additivePoseRotationBase(editedTrack, bone, null);
    }

    /**
     * The overload fed by the renderer's EVALUATED channel rotation for the bone
     * (radians, rest + actions + pose): the base is then simply
     * {@code evaluated − the edited track's own contribution}, which folds the
     * animator's actions and the model's rest rotation in — the pose-track sum
     * of the two-argument form can't see those. Falls back to the track sum when
     * {@code evaluatedRadians} is {@code null} (no model bone entry). The
     * additivity guards stay either way: any multiplicative contributor means
     * the whole additive-base model doesn't apply.
     */
    public static Vector3f additivePoseRotationBase(ValuePose editedTrack, String bone, Vector3f evaluatedRadians)
    {
        Form form = getForm(editedTrack);
        List<ValuePose> tracks = new ArrayList<>();

        if (form instanceof IPosedForm posedForm)
        {
            tracks.add(posedForm.getPose());
            tracks.add(posedForm.getPoseOverlay());

            if (form instanceof ModelForm modelForm)
            {
                tracks.addAll(modelForm.additionalOverlays);
            }
        }
        else
        {
            return null;
        }

        if (!tracks.contains(editedTrack))
        {
            return null;
        }

        Vector3f trackSum = new Vector3f();
        Vector3f editedContribution = new Vector3f();

        for (ValuePose track : tracks)
        {
            PoseTransform transform = track.get().transforms.get(bone);

            if (transform == null)
            {
                continue;
            }

            if (transform.rotationMode == Transform.RotationMode.QUATERNION || transform.fix != 0F)
            {
                return null;
            }

            if (track == editedTrack)
            {
                editedContribution.set(transform.rotate);
            }
            else
            {
                trackSum.add(transform.rotate);
            }
        }

        return evaluatedRadians == null ? trackSum : new Vector3f(evaluatedRadians).sub(editedContribution);
    }

    public static Form fromData(BaseType data)
    {
        if (data instanceof MapType map)
        {
            return fromData(map);
        }

        return null;
    }

    public static Form fromData(MapType data)
    {
        try
        {
            return data == null ? null : BBSMod.getForms().fromData(data);
        }
        catch (Exception e)
        {}

        return null;
    }

    public static MapType toData(Form form)
    {
        return form == null ? null : BBSMod.getForms().toData(form);
    }

    public static Form copy(Form form)
    {
        if (form != null)
        {
            FormArchitect forms = BBSMod.getForms();

            return forms.fromData(forms.toData(form));
        }

        return null;
    }

    public static Form getRoot(Form form)
    {
        while (form.getParent() != null)
        {
            form = form.getParentForm();
        }

        return form;
    }

    public static Form getForm(BaseValue property)
    {
        if (property.getParent() instanceof Form form)
        {
            return form;
        }

        return null;
    }

    /**
     * Resolve a body-part path — {@code /}-separated stable part ids — starting at {@code form}.
     * Each segment names a part of the current form and steps into that part's form.
     */
    /**
     * Split cache for the two path walkers below: they run per track per frame over a small,
     * stable set of authored paths, and {@code String.split} allocated a fresh array (plus a
     * regex pass) for each. Concurrent map — tracks apply on the client, actions on the server.
     */
    private static final Map<String, String[]> SPLIT_PATHS = new ConcurrentHashMap<>();

    private static String[] splitPath(String path)
    {
        /* A runaway set of generated paths must not pin memory forever. */
        if (SPLIT_PATHS.size() > 4096)
        {
            SPLIT_PATHS.clear();
        }

        return SPLIT_PATHS.computeIfAbsent(path, (p) -> p.split(PATH_SEPARATOR));
    }

    public static Form getForm(Form form, String path)
    {
        for (String s : splitPath(path))
        {
            BodyPart part = form.parts.get(s) instanceof BodyPart bodyPart ? bodyPart : null;

            if (part == null || part.getForm() == null)
            {
                break;
            }

            form = part.getForm();
        }

        return form;
    }

    /**
     * The body-part path of {@code form} from its root — the stable ids of the parts it hangs
     * under, outermost first; empty for the root form itself.
     */
    public static String getPath(Form form)
    {
        List<String> path = new ArrayList<>();

        appendPartPath(form, path);
        Collections.reverse(path);

        return String.join(PATH_SEPARATOR, path);
    }

    /* Form properties utils */

    /** The property address: its owner form path with the property id as the last segment. */
    public static String getPropertyPath(BaseValue property)
    {
        List<String> path = new ArrayList<>();

        path.add(property.getId());
        appendPartPath(getForm(property), path);
        Collections.reverse(path);

        return String.join(PATH_SEPARATOR, path);
    }

    /** Collect the ids of the body parts above {@code form}, innermost first, into {@code path}. */
    private static void appendPartPath(Form form, List<String> path)
    {
        BaseValue value = form;

        while (value != null)
        {
            if (value instanceof BodyPart part)
            {
                path.add(part.getId());
            }

            value = value.getParent();
        }
    }

    public static List<String> collectPropertyPaths(Form form)
    {
        List<String> properties = new ArrayList<>();

        collectPropertyPaths(form, properties, "");

        /* There is no need to animate body part anchor properties */
        Iterator<String> it = properties.iterator();

        while (it.hasNext())
        {
            if (it.next().endsWith("/anchor"))
            {
                it.remove();
            }
        }

        return properties;
    }

    public static void collectPropertyPaths(Form form, List<String> properties, String prefix)
    {
        if (form == null)
        {
            return;
        }

        for (BaseValue property : form.getAll())
        {
            if (property.isVisible())
            {
                properties.add(StringUtils.combinePaths(prefix, property.getId()));
            }
        }

        for (BodyPart part : form.parts.getAllTyped())
        {
            String newPrefix = StringUtils.combinePaths(prefix, part.getId());

            collectPropertyPaths(part.getForm(), properties, newPrefix);
        }
    }

    /**
     * Resolve a property path — the stable ids of the body parts leading to the owning form,
     * followed by the property's id. A segment that is neither a property nor a part of the
     * current form ends the walk: the path is orphaned (its part was removed or the channel was
     * authored against another form) and resolves to nothing.
     */
    public static BaseValueBasic getProperty(Form form, String path)
    {
        if (form == null)
        {
            return null;
        }

        for (String segment : splitPath(path))
        {
            BaseValueBasic property = form.getBasic(segment);

            if (property != null)
            {
                return property;
            }

            BodyPart part = form.parts.get(segment) instanceof BodyPart bodyPart ? bodyPart : null;

            if (part == null || part.getForm() == null)
            {
                return null;
            }

            form = part.getForm();
        }

        return null;
    }

    /**
     * Prior to 1.6, there was a mechanism called state triggers (commissioned by Checkpoint).
     *
     * It was a way to override form properties by pressing a key. In 1.6, they were superseded
     * by animation states mechanism. This code converts the data from state trigger format into
     * animation states. It's not 1-to-1, but better than nothing.
     */
    public static void readOldStateTriggers(Form form, MapType map)
    {
        if (map.has("stateTriggers") && map.getMap("stateTriggers").has("list"))
        {
            ListType list = map.getMap("stateTriggers").getList("list");

            for (BaseType type : list)
            {
                if (!type.isMap())
                {
                    continue;
                }

                MapType stateTrigger = type.asMap();
                AnimationState state = new AnimationState("");
                MapType states = stateTrigger.getMap("states");

                state.id.set(stateTrigger.getString("id"));
                state.keybind.set(stateTrigger.getInt("hotkey"));

                for (String key : states.keys())
                {
                    BaseType stateData = states.get(key);
                    KeyframeChannel channel = state.properties.getOrCreate(form, key);

                    if (channel != null)
                    {
                        Object o = channel.getFactory().fromData(stateData);

                        channel.insert(0F, o);
                    }
                }

                form.states.add(state);
            }
        }

        form.states.sync();
    }
}