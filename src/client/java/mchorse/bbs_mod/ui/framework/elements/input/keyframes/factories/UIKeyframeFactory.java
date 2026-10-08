package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeParameters;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.ui.framework.elements.utils.ScrollMemory;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class UIKeyframeFactory <T> extends UIElement
{
    /** Resolve the primary gizmo transform in a caller-owned value copy. */
    public Transform getGizmoTransform(T value) { return null; }

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
    public UIKeyframeParameters parameters;
    protected final UITrackValue<T> track;
    protected UIKeyframes editor;
    private float displayTick = Float.NaN;

    /**
     * Fills the registry. Called by BBS while it initialises, and followed by the event that
     * lets addons add to it.
     *
     * <p>This used to be a static initialiser, which ran whenever something first touched the
     * class — a moment nobody chose and an addon could not aim at.</p>
     */
    public static void setup()
    {
        register(KeyframeFactories.ANCHOR, UIAnchorKeyframeFactory::new);
        register(KeyframeFactories.BOOLEAN, UIBooleanKeyframeFactory::new);
        register(KeyframeFactories.COLOR, UIColorKeyframeFactory::new);
        register(KeyframeFactories.FLOAT, UIFloatKeyframeFactory::new);
        register(KeyframeFactories.DOUBLE, UIDoubleKeyframeFactory::new);
        register(KeyframeFactories.INTEGER, UIIntegerKeyframeFactory::new);
        register(KeyframeFactories.LONG, UILongKeyframeFactory::new);
        register(KeyframeFactories.LINK, UILinkKeyframeFactory::new);
        register(KeyframeFactories.POSE, UIPoseKeyframeFactory::new);
        register(KeyframeFactories.IK, UIIKKeyframeFactory::new);
        register(KeyframeFactories.PHYSICS, UIPhysicsKeyframeFactory::new);
        register(KeyframeFactories.SHAKE, UIShakeKeyframeFactory::new);
        register(KeyframeFactories.WIND, UIWindKeyframeFactory::new);
        register(KeyframeFactories.SPLINE, UISplineKeyframeFactory::new);
        register(KeyframeFactories.SPLINE_POINTS, UISplinePointsKeyframeFactory::new);
        register(KeyframeFactories.POSE_TRANSFORM, UIPoseTransformKeyframeFactory::new);
        register(KeyframeFactories.BONE_CONSTRAINT, UIBoneConstraintKeyframeFactory::new);
        register(KeyframeFactories.STRING, UIStringKeyframeFactory::new);
        register(KeyframeFactories.TRANSFORM, UITransformKeyframeFactory::new);
        register(KeyframeFactories.VECTOR4F, UIVector4fKeyframeFactory::new);
        register(KeyframeFactories.BLOCK_STATE, UIBlockStateKeyframeFactory::new);
        register(KeyframeFactories.ITEM_STACK, UIItemStackKeyframeFactory::new);
        register(KeyframeFactories.HOTBAR, UIHotbarKeyframeFactory::new);
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
            SCROLLS.save(editor.track.getFactory(), editor.scroll);
        }
    }

    public void restoreScroll()
    {
        SCROLLS.restore(this.track.getFactory(), this.scroll);
    }

    public static <T> UIKeyframeFactory createPanel(UITrackValue<T> track, UIKeyframes editor)
    {
        IUIKeyframeFactoryFactory<T> factory = getPropertyFactory(track);

        if (factory == null)
        {
            factory = FACTORIES.get(track.getFactory());
        }

        UIKeyframeFactory<T> panel = factory == null ? null : factory.create(track, editor);

        if (panel != null)
        {
            /* Key parameters keep their own undo through cacheKeyframes and submitKeyframes. */
            List<UINumericInput> parameters = panel.parameters.getChildren(UINumericInput.class);

            for (UINumericInput<?> input : panel.getChildren(UINumericInput.class))
            {
                if (parameters.contains(input)) continue;
                input.getEvents().register(UITrackpadDragStartEvent.class, event -> editor.beginValueGesture());
                input.getEvents().register(UITrackpadDragEndEvent.class, event ->
                {
                    if (event.cancelled) editor.cancelValueGesture();
                    else editor.endValueGesture();
                });
            }
        }

        return panel;
    }

    /**
     * The editor registered for the track's property, if there is one. Bone tracks are left out: their
     * channels end in a bone's name, which is model data and could land on a property's id by accident.
     */
    private static <T> IUIKeyframeFactoryFactory<T> getPropertyFactory(UITrackValue<T> track)
    {
        UIKeyframeSheet sheet = track.sheet;

        if (sheet == null || sheet.property == null || sheet.isBoneTrack)
        {
            return null;
        }

        return PROPERTIES.get(StringUtils.fileName(sheet.channel.getId()));
    }

    public UIKeyframeFactory(UITrackValue<T> track, UIKeyframes editor)
    {
        this.track = track;
        this.editor = editor;
        this.scroll = UI.scrollView(UIConstants.MARGIN, Math.max(UIConstants.SCROLL_PADDING, 4));
        this.scroll.scroll.cancelScrolling();
        this.scroll.full(this);
        this.parameters = new UIKeyframeParameters(editor, KeyframeFactories.isNumeric(track.getFactory()));
        this.scroll.add(this.parameters);
        this.add(this.scroll);
    }

    public UIKeyframeSheet getSheet() { return this.track.sheet; }

    protected T getDisplayValue() { return this.track.getValue(); }

    public void setValue(Object value) { this.track.setValue((T) value); }

    public void update() {}

    public void requestUpdate()
    {
        this.displayTick = Float.NaN;
    }

    @Override
    public void render(UIContext context)
    {
        float tick = this.editor.getTick();
        if (this.isUserEditing())
        {
            /* Re-read once the edit ends, even if the playhead did not move. */
            this.displayTick = Float.NaN;
        }
        else if (Float.compare(tick, this.displayTick) != 0)
        {
            this.displayTick = tick;
            this.update();
        }
        super.render(context);
    }

    public interface IUIKeyframeFactoryFactory<T>
    {
        UIKeyframeFactory<T> create(UITrackValue<T> track, UIKeyframes editor);
    }
}
