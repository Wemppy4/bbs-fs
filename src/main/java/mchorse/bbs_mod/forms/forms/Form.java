package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.forms.states.AnimationStates;
import mchorse.bbs_mod.forms.states.StatePlayer;
import mchorse.bbs_mod.forms.values.ValueAnchor;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.StableIds;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class Form extends ValueGroup
{
    /** Sentinel in {@link #disabledTracks} meaning every track of this form is hidden on the timeline. */
    public static final String DISABLED_ALL = "*";

    public final ValueBoolean visible = new ValueBoolean("visible", true);
    public final ValueStringKeys disabledTracks = new ValueStringKeys("disabled_tracks");
    public final ValueString trackName = new ValueString("track_name", "");
    public final ValueFloat lighting = new ValueFloat("lighting", 1F);

    /** Color overlay (RGB = color, A = strength): mixes the rendered pixels toward the color, surviving shader packs. */
    public final ValueColor overlayColor = new ValueColor("color_overlay", new Color(1F, 1F, 1F, 0F));

    /** Explicit render layer ({@link #LAYER_AUTO} keeps the old heuristics — see the model renderer). */
    public final ValueInt renderLayer = new ValueInt("render_layer", 0);

    public static final int LAYER_AUTO = 0;
    public static final int LAYER_SOLID = 1;
    public static final int LAYER_CUTOUT = 2;
    public static final int LAYER_TRANSLUCENT = 3;
    public final ValueString name = new ValueString("name", "");
    public final ValueTransform transform = new ValueTransform("transform", new Transform());
    public final ValueTransform transformOverlay = new ValueTransform("transform_overlay", new Transform());
    public final ValueFloat uiScale = new ValueFloat("uiScale", 1F);
    public final ValueAnchor anchor = new ValueAnchor("anchor", new Anchor());
    public final ValueBoolean shaderShadow = new ValueBoolean("shaderShadow", true);
    public final ValueBoolean additiveColor = new ValueBoolean("additive_color", false);

    public final List<ValueTransform> additionalTransforms = new ArrayList<>();

    /* Hitbox properties */
    public final ValueBoolean hitbox = new ValueBoolean("hitbox", false);
    public final ValueFloat hitboxWidth = new ValueFloat("hitboxWidth", 0.5F);
    public final ValueFloat hitboxHeight = new ValueFloat("hitboxHeight", 1.8F);
    public final ValueFloat hitboxSneakMultiplier = new ValueFloat("hitboxSneakMultiplier", 0.9F);
    public final ValueFloat hitboxEyeHeight = new ValueFloat("hitboxEyeHeight", 0.9F);

    /* Morphing properties */
    public final ValueFloat hp = new ValueFloat("hp", 20F);
    public final ValueFloat speed = new ValueFloat("movement_speed", 0.1F);
    public final ValueFloat stepHeight = new ValueFloat("step_height", 0.5F);

    public final ValueInt hotkey = new ValueInt("keybind", 0);

    public final BodyPartManager parts = new BodyPartManager("parts");
    public final AnimationStates states = new AnimationStates("states");

    protected Object renderer;
    protected String cachedID;

    private final List<StatePlayer> statePlayers = new ArrayList<>();

    /**
     * Bumped whenever anything that can move this form's evaluated pose changes: every value
     * notification bubbling through this form ({@link #postNotify(BaseValue, int)}) and the
     * animation state applications, which write silently. Together with the render frame's
     * epoch this keys the per-frame pose caches; a spare bump costs one cache miss (today's
     * behaviour), a missed one costs correctness — so bumping errs generous.
     */
    private int poseVersion;

    public int getPoseVersion()
    {
        return this.poseVersion;
    }

    public void bumpPoseVersion()
    {
        this.poseVersion += 1;
    }

    @Override
    public void postNotify(BaseValue value, int flag)
    {
        this.poseVersion += 1;

        super.postNotify(value, flag);
    }

    public Form()
    {
        super("");

        this.disabledTracks.invisible();
        this.trackName.invisible();
        this.name.invisible();
        this.uiScale.invisible();
        this.shaderShadow.invisible();
        this.additiveColor.invisible();

        /* Not animated: a one-off authoring switch, like the hitbox or the hotkey. */
        this.renderLayer.invisible();

        this.add(this.visible);
        this.add(this.disabledTracks);
        this.add(this.trackName);
        this.add(this.lighting);
        this.add(this.overlayColor);
        this.add(this.renderLayer);
        this.add(this.name);
        this.add(this.transform);
        this.add(this.transformOverlay);

        for (int i = 0; i < BBSSettings.recordingPoseTransformOverlays.get(); i++)
        {
            ValueTransform valueTransform = new ValueTransform("transform_overlay" + i, new Transform());

            this.additionalTransforms.add(valueTransform);
            this.add(valueTransform);
        }

        this.add(this.uiScale);
        this.add(this.anchor);
        this.add(this.shaderShadow);
        this.add(this.additiveColor);

        this.hitbox.invisible();
        this.hitboxWidth.invisible();
        this.hitboxHeight.invisible();
        this.hitboxSneakMultiplier.invisible();
        this.hitboxEyeHeight.invisible();

        this.add(this.hitbox);
        this.add(this.hitboxWidth);
        this.add(this.hitboxHeight);
        this.add(this.hitboxSneakMultiplier);
        this.add(this.hitboxEyeHeight);

        this.hp.invisible();
        this.speed.invisible();
        this.stepHeight.invisible();

        this.add(this.hp);
        this.add(this.speed);
        this.add(this.stepHeight);

        this.hotkey.invisible();

        this.add(this.hotkey);

        this.add(this.parts);
        this.add(this.states);
    }

    public Object getRenderer()
    {
        return this.renderer;
    }

    public void setRenderer(Object renderer)
    {
        this.renderer = renderer;
    }

    public Form getParentForm()
    {
        BaseValue parentValue = this.getParent();

        while (parentValue != null)
        {
            if (parentValue instanceof Form form)
            {
                return form;
            }

            parentValue = parentValue.getParent();
        }

        return null;
    }

    /* Animation states */

    public boolean findState(int hotkey, IStateFoundCallback callback)
    {
        if (callback == null)
        {
            return false;
        }

        for (AnimationState state : this.states.getAllTyped())
        {
            if (state.keybind.get() == hotkey)
            {
                callback.acceptState(this, state);

                return true;
            }
        }

        return false;
    }

    public void clearStatePlayers()
    {
        this.statePlayers.clear();
    }

    public void playState(AnimationState state)
    {
        if (state != null)
        {
            if (state.looping.get())
            {
                for (StatePlayer statePlayer : this.statePlayers)
                {
                    if (statePlayer.getState() == state)
                    {
                        statePlayer.expire();

                        return;
                    }
                }
            }

            this.statePlayers.add(new StatePlayer(state));
        }
    }

    public void playState(String stateId)
    {
        this.playState(this.states.getById(stateId));
    }

    public void playMain()
    {
        this.clearStatePlayers();
        this.playState(this.states.getMainRandom());
    }

    public void applyStates(float transition)
    {
        /* States mutate the pose without notifications; bump only when there are any, so a
         * state-less form keeps one pose version across its render passes and the per-frame
         * pose caches hold. */
        if (!this.statePlayers.isEmpty())
        {
            this.poseVersion += 1;
        }

        for (StatePlayer statePlayer : this.statePlayers)
        {
            statePlayer.assignValues(this, transition);
        }
    }

    public void unapplyStates()
    {
        if (!this.statePlayers.isEmpty())
        {
            this.poseVersion += 1;
        }

        for (StatePlayer statePlayer : this.statePlayers)
        {
            statePlayer.resetValues(this);
        }
    }

    /* Morphing */

    public void onMorph(LivingEntity entity)
    {
        float hp = this.hp.get();
        float speed = this.speed.get();
        float stepHeight = this.stepHeight.get();

        if (hp != 20F)
        {
            entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(hp);
            entity.setHealth(hp);
        }
        if (speed != 0.1F) entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
        if (stepHeight != 0.5F) entity.setStepHeight(stepHeight);
    }

    public void onDemorph(LivingEntity entity)
    {
        entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20F);
        entity.setHealth(20F);
        entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1F);
        entity.setStepHeight(0.5F);
    }

    /* ID and display name */

    public String getFormId()
    {
        if (this.cachedID == null)
        {
            this.cachedID = BBSMod.getForms().getType(this).toString();
        }

        return this.cachedID;
    }

    public String getFormIdOrName()
    {
        String name = this.name.get();

        return name.isEmpty() ? this.getFormId() : name;
    }

    /**
     * The icon this kind of form wears — the one on its main tab in the form editor, and the one a
     * timeline draws on the row of a body part holding it. Declared here so the two agree by
     * construction instead of by two lists kept in step by hand.
     */
    public Icon getIcon()
    {
        return Icons.GEAR;
    }

    public final String getDisplayName()
    {
        String name = this.name.get();

        if (!name.isEmpty())
        {
            return name;
        }

        return this.getDefaultDisplayName();
    }

    protected String getDefaultDisplayName()
    {
        return this.getFormId();
    }

    /**
     * What a timeline calls a track of this form: the animator's own track name when they set one,
     * otherwise the form's name followed by the property.
     *
     * <p>Never the raw address. A track's address is a chain of body part ids, and an id is a
     * random eight characters — readable as data, meaningless as a label. (It was the part's list
     * position before stable ids, which read no better and moved when parts were reordered.)</p>
     *
     * <p>Empty {@code property} asks for the form's label alone, and answers with the custom name
     * or nothing — the callers that show a form itself have their own fallback.</p>
     */
    public String getTrackName(String property)
    {
        String custom = this.trackName.get();

        if (property.isEmpty())
        {
            return custom;
        }

        TrackId track = TrackId.parse(property);
        String last;

        if (track == null)
        {
            int slash = property.lastIndexOf('/');

            last = slash == -1 ? property : property.substring(slash + 1);
        }
        else
        {
            /* Asked of the address itself: a bone track reads "head", not "pose.bones.head". */
            last = track.label();
        }

        /* An address segment (a body part's stable id, or a legacy index) is not a name. */
        boolean address = StableIds.isStableId(last) || StringUtils.isInteger(last);
        String leaf = address ? "" : last;
        String owner = this.getTrackLabel();

        if (owner.isEmpty())
        {
            return leaf;
        }

        return leaf.isEmpty() ? owner : owner + "/" + leaf;
    }

    /**
     * What a timeline calls this form when it prefixes one of its tracks: the animator's own track
     * name when set, otherwise the names of the forms it hangs under as a body part, root-first.
     * Empty for a root form, whose tracks stand for the replay itself and need no prefix.
     *
     * <p>This is the label side of a track's identity; the address side is
     * {@link mchorse.bbs_mod.forms.FormUtils#getPath}. They must not be confused: the address is
     * built from random stable ids and is unreadable by design.</p>
     */
    public String getTrackLabel()
    {
        String custom = this.trackName.get();

        if (!custom.isEmpty())
        {
            return custom;
        }

        List<String> names = new ArrayList<>();
        Form form = this;

        while (form.getParentForm() != null)
        {
            names.add(form.getDisplayName());

            form = form.getParentForm();
        }

        Collections.reverse(names);

        return String.join("/", names);
    }

    /* Update */

    public void update(IEntity entity)
    {
        this.parts.update(entity);

        if (this.renderer instanceof ITickable)
        {
            ((ITickable) this.renderer).tick(entity);
        }

        Iterator<StatePlayer> it = this.statePlayers.iterator();

        while (it.hasNext())
        {
            StatePlayer next = it.next();

            next.update();

            if (next.canBeRemoved())
            {
                it.remove();
            }
        }
    }

    /* Data comparison and (de)serialization */

    @Override
    public void fromData(BaseType data)
    {
        if (data instanceof MapType map)
        {
            /* Compatibility with older forms */
            if (map.has("bodyParts"))
            {
                MapType bodyParts = map.getMap("bodyParts");

                if (bodyParts.has("parts"))
                {
                    map.remove("bodyParts");
                    map.put("parts", bodyParts.getList("parts"));
                }
            }
        }

        super.fromData(data);

        if (data instanceof MapType map)
        {
            /* Compatibility with state triggers */
            FormUtils.readOldStateTriggers(this, map);

            /* The "animatable" toggle was removed; a disabled one meant the form had no tracks,
             * which is now expressed by hiding every track of the form on the timeline. */
            if (map.has("animatable") && !map.getBool("animatable", true))
            {
                this.disabledTracks.get().add(DISABLED_ALL);
            }

            /* The "additive color" toggle was removed (its brighten math clipped to plain white
             * under shader packs). Old scenes that used it convert to the color overlay: the tint
             * becomes the overlay color at the tint's alpha, and the multiply tint resets to white.
             * Not pixel-identical, but the closest the overlay can honestly do. */
            if (this.additiveColor.get())
            {
                if (this.get("color") instanceof ValueColor colorValue)
                {
                    Color color = colorValue.get();

                    this.overlayColor.set(new Color(color.r, color.g, color.b, color.a));
                    colorValue.set(Color.white());
                }

                this.additiveColor.set(false);
            }
        }
    }

    @Override
    public BaseType toData()
    {
        BaseType data = super.toData();

        if (data instanceof MapType map)
        {
            BBSMod.getForms().appendId(this, map);
        }

        return data;
    }
}