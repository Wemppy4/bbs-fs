package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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

        if (form instanceof ModelForm modelForm)
        {
            tracks.add(modelForm.pose);
            tracks.add(modelForm.poseOverlay);
            tracks.addAll(modelForm.additionalOverlays);
        }
        else if (form instanceof MobForm mobForm)
        {
            tracks.add(mobForm.pose);
            tracks.add(mobForm.poseOverlay);
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

    public static Form getForm(Form form, String path)
    {
        String[] split = path.split(PATH_SEPARATOR);

        for (String s : split)
        {
            try
            {
                int index = Integer.parseInt(s);
                BodyPart safe = CollectionUtils.getSafe(form.parts.getAllTyped(), index);

                if (safe != null)
                {
                    form = safe.getForm();
                }
                else
                {
                    break;
                }
            }
            catch (Exception e)
            {
                break;
            }
        }

        return form;
    }

    /** The form address: body part indices from the root down, {@code ""} for a root form. */
    public static String getPath(Form form)
    {
        if (form.getParent() == null)
        {
            return "";
        }

        return buildPath(form, null);
    }

    /* Form properties utils */

    /** The property address: its owner form path with the property id as the last segment. */
    public static String getPropertyPath(BaseValue property)
    {
        return buildPath(getForm(property), property.getId());
    }

    /**
     * Walks up the body part tree collecting the index each form sits at in its parent, and
     * joins them root-first. The leaf, when given, is the segment the address ends on (a
     * property id) - the walk itself is the same either way.
     *
     * <p>The list is local on purpose: this used to share one static buffer between both
     * callers, which only held because no walk ever happened inside another one.
     */
    private static String buildPath(Form form, String leaf)
    {
        List<String> path = new ArrayList<>();

        if (leaf != null)
        {
            path.add(leaf);
        }

        while (form != null)
        {
            Form parent = form.getParentForm();

            if (parent != null)
            {
                int i = 0;

                for (BodyPart part : parent.parts.getAllTyped())
                {
                    if (part.getForm() == form)
                    {
                        path.add(String.valueOf(i));
                    }

                    i += 1;
                }
            }

            form = parent;
        }

        Collections.reverse(path);

        return String.join(PATH_SEPARATOR, path);
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

        List<BodyPart> all = form.parts.getAllTyped();

        for (int i = 0; i < all.size(); i++)
        {
            String newPrefix = StringUtils.combinePaths(prefix, String.valueOf(i));

            collectPropertyPaths(all.get(i).getForm(), properties, newPrefix);
        }
    }

    public static BaseValueBasic getProperty(Form form, String path)
    {
        if (form == null)
        {
            return null;
        }

        if (!path.contains(PATH_SEPARATOR))
        {
            return form.getAllMap().get(path);
        }

        String[] segments = path.split(PATH_SEPARATOR);

        for (int i = 0; i < segments.length; i++)
        {
            String segment = segments[i];
            BaseValueBasic property = form.getAllMap().get(segment);

            if (property == null)
            {
                try
                {
                    int index = Integer.parseInt(segment);

                    if (CollectionUtils.inRange(form.parts.getAll(), index))
                    {
                        form = form.parts.getAllTyped().get(index).getForm();

                        if (form == null)
                        {
                            return null;
                        }
                    }
                    else
                    {
                        return null;
                    }
                }
                catch (Exception e)
                {}
            }
            else
            {
                return property;
            }
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