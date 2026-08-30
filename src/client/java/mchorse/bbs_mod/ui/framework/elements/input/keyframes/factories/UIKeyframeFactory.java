package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIInterpolationContextMenu;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.tooltips.InterpolationTooltip;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.ui.framework.elements.utils.ScrollMemory;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.HashMap;
import java.util.Map;

public abstract class UIKeyframeFactory <T> extends UIElement
{
    private static final Map<IKeyframeFactory, IUIKeyframeFactoryFactory> FACTORIES = new HashMap<>();

    /**
     * Editors bound to one form property rather than to a value type, keyed the same way a track's
     * colour and icon are - by the last segment of its channel id. A property whose value type says
     * nothing about how it should be edited (a model is a string, but picking one is not typing)
     * takes its editor from here, and everything else falls through to {@link #FACTORIES}.
     */
    private static final Map<String, IUIKeyframeFactoryFactory> PROPERTIES = new HashMap<>();

    private static final ScrollMemory<IKeyframeFactory> SCROLLS = new ScrollMemory<>();

    public UIScrollView scroll;
    public UITrackpad tick;
    public UITrackpad duration;
    public UIIcon interp;

    protected Keyframe<T> keyframe;
    protected UIKeyframes editor;

    static
    {
        register(KeyframeFactories.ANCHOR, UIAnchorKeyframeFactory::new);
        register(KeyframeFactories.BOOLEAN, UIBooleanKeyframeFactory::new);
        register(KeyframeFactories.COLOR, UIColorKeyframeFactory::new);
        register(KeyframeFactories.FLOAT, UIFloatKeyframeFactory::new);
        register(KeyframeFactories.DOUBLE, UIDoubleKeyframeFactory::new);
        register(KeyframeFactories.INTEGER, UIIntegerKeyframeFactory::new);
        register(KeyframeFactories.LINK, UILinkKeyframeFactory::new);
        register(KeyframeFactories.POSE, UIPoseKeyframeFactory::new);
        register(KeyframeFactories.IK, UIIKKeyframeFactory::new);
        register(KeyframeFactories.PHYSICS, UIPhysicsKeyframeFactory::new);
        register(KeyframeFactories.WIND, UIWindKeyframeFactory::new);
        register(KeyframeFactories.POSE_TRANSFORM, UIPoseTransformKeyframeFactory::new);
        register(KeyframeFactories.BONE_CONSTRAINT, UIBoneConstraintKeyframeFactory::new);
        register(KeyframeFactories.STRING, UIStringKeyframeFactory::new);
        register(KeyframeFactories.TRANSFORM, UITransformKeyframeFactory::new);
        register(KeyframeFactories.VECTOR3F, UIVector3fKeyframeFactory::new);
        register(KeyframeFactories.VECTOR3F_SCALE, UIVector3fKeyframeFactory::new);
        register(KeyframeFactories.VECTOR4F, UIVector4fKeyframeFactory::new);
        register(KeyframeFactories.BLOCK_STATE, UIBlockStateKeyframeFactory::new);
        register(KeyframeFactories.ITEM_STACK, UIItemStackKeyframeFactory::new);
        register(KeyframeFactories.ACTIONS_CONFIG, UIActionsConfigKeyframeFactory::new);
        register(KeyframeFactories.SHAPE_KEYS, UIShapeKeysKeyframeFactory::new);
        register(KeyframeFactories.PARTICLE_SETTINGS, UIParticleSettingsKeyframeFactory::new);

        registerProperty("model", UIModelKeyframeFactory::new);
    }

    public static <T> void register(IKeyframeFactory<T> clazz, IUIKeyframeFactoryFactory<T> factory)
    {
        FACTORIES.put(clazz, factory);
    }

    public static <T> void registerProperty(String property, IUIKeyframeFactoryFactory<T> factory)
    {
        PROPERTIES.put(property, factory);
    }

    public static void saveScroll(UIKeyframeFactory editor)
    {
        if (editor != null)
        {
            SCROLLS.save(editor.keyframe.getFactory(), editor.scroll);
        }
    }

    public void restoreScroll()
    {
        SCROLLS.restore(this.keyframe.getFactory(), this.scroll);
    }

    public static <T> UIKeyframeFactory createPanel(Keyframe<T> keyframe, UIKeyframes editor)
    {
        IUIKeyframeFactoryFactory<T> factory = getPropertyFactory(keyframe, editor);

        if (factory == null)
        {
            factory = FACTORIES.get(keyframe.getFactory());
        }

        return factory == null ? null : factory.create(keyframe, editor);
    }

    /**
     * The editor registered for the track's property, if there is one. Bone tracks are left out: their
     * channels end in a bone's name, which is model data and could land on a property's id by accident.
     */
    private static <T> IUIKeyframeFactoryFactory<T> getPropertyFactory(Keyframe<T> keyframe, UIKeyframes editor)
    {
        IUIKeyframeGraph graph = editor == null ? null : editor.getGraph();
        UIKeyframeSheet sheet = graph == null ? null : graph.getSheet(keyframe);

        if (sheet == null || sheet.property == null || sheet.isBoneTrack)
        {
            return null;
        }

        return PROPERTIES.get(StringUtils.fileName(sheet.channel.getId()));
    }

    public UIKeyframeFactory(Keyframe<T> keyframe, UIKeyframes editor)
    {
        this.keyframe = keyframe;
        this.editor = editor;

        this.scroll = UI.scrollView(UIConstants.MARGIN, Math.max(UIConstants.SCROLL_PADDING, 4));
        this.scroll.scroll.cancelScrolling();
        this.scroll.full(this);

        this.tick = new UITrackpad(this::setTick);
        this.tick.tooltip(UIKeys.KEYFRAMES_TICK);
        this.tick.getEvents().register(UITrackpadDragStartEvent.class, (e) -> this.editor.cacheKeyframes());
        this.tick.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.editor.submitKeyframes());
        this.duration = new UITrackpad((v) -> this.setDuration(v.floatValue()));
        this.duration.limit(0, Float.MAX_VALUE).tooltip(UIKeys.KEYFRAMES_FORCED_DURATION);
        this.interp = new UIIcon(Icons.GRAPH, (b) ->
        {
            Interpolation interp = this.keyframe.getInterpolation();
            UIInterpolationContextMenu menu = new UIInterpolationContextMenu(interp);

            this.getContext().replaceContextMenu(menu.callback(() -> this.editor.getGraph().setInterpolation(interp)));
        });
        this.interp.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        this.interp.tooltip(new InterpolationTooltip(0F, 0.5F, () -> this.keyframe.getInterpolation()));
        this.interp.keys().register(Keys.KEYFRAMES_INTERP, this.interp::clickItself).category(UIKeys.KEYFRAMES_KEYS_CATEGORY);

        this.scroll.add(UI.row(UIConstants.MARGIN, 0, 0, this.interp, this.tick, this.duration));

        this.add(this.scroll);

        /* Fill data */
        this.tick.setValue(TimeUtils.toTime(keyframe.getTick()));
        this.duration.setValue(TimeUtils.toTime(keyframe.getDuration()));
    }

    public Keyframe<T> getKeyframe()
    {
        return this.keyframe;
    }

    /**
     * The keyframe an edit made in this panel should land on: the one the panel was opened for,
     * or &mdash; with auto-keyframing on &mdash; the keyframe of the same track at the playhead,
     * made from the track's interpolated value if there is none there yet.
     */
    public Keyframe<T> getEditTarget()
    {
        return this.editor.getGraph().getEditTarget(this.keyframe);
    }

    /**
     * Whether this panel's fields should follow the playhead rather than the keyframe they were
     * opened for. They have to when auto-keyframing, because that is where the next edit lands:
     * a field showing a keyframe at another tick would start a drag from the wrong number.
     */
    protected boolean followsPlayhead()
    {
        return this.editor.getGraph().getAutoKeyframeTick() != null;
    }

    /**
     * What the fields should show: the edited keyframe's value, or what the track reads at the
     * playhead while {@link #followsPlayhead()}. Read-only &mdash; a keyframe is only brought into
     * being once something is actually edited.
     */
    protected T getDisplayValue()
    {
        IUIKeyframeGraph graph = this.editor.getGraph();
        Integer tick = graph.getAutoKeyframeTick();
        UIKeyframeSheet sheet = tick == null ? null : graph.getSheet(this.keyframe);

        if (sheet == null || sheet.channel.isEmpty())
        {
            return this.keyframe.getValue();
        }

        T value = (T) sheet.channel.interpolate(tick);

        return value == null ? this.keyframe.getValue() : value;
    }

    public void setTick(double tick)
    {
        double time = TimeUtils.fromTime(tick);

        this.editor.getGraph().setTick((float) time, false);
    }

    public void setDuration(float value)
    {
        this.editor.getGraph().setDuration(value);
    }

    public void setValue(Object value)
    {
        this.editor.getGraph().setValue(value, true, true);
    }

    public void update()
    {
        this.tick.setValue(TimeUtils.toTime(this.keyframe.getTick()));
    }

    public static interface IUIKeyframeFactoryFactory <T>
    {
        public UIKeyframeFactory<T> create(Keyframe<T> keyframe, UIKeyframes editor);
    }
}
