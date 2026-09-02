package mchorse.bbs_mod.ui.film.clips;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.actions.types.DamageActionClip;
import mchorse.bbs_mod.actions.types.SwipeActionClip;
import mchorse.bbs_mod.actions.types.blocks.BreakBlockActionClip;
import mchorse.bbs_mod.actions.types.blocks.InteractBlockActionClip;
import mchorse.bbs_mod.actions.types.blocks.PlaceBlockActionClip;
import mchorse.bbs_mod.actions.types.chat.ChatActionClip;
import mchorse.bbs_mod.actions.types.chat.CommandActionClip;
import mchorse.bbs_mod.actions.types.item.ItemDropActionClip;
import mchorse.bbs_mod.actions.types.item.ReleaseUseItemActionClip;
import mchorse.bbs_mod.actions.types.item.UseBlockItemActionClip;
import mchorse.bbs_mod.actions.types.item.UseItemActionClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.misc.CurveClientClip;
import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.clips.misc.VideoClientClip;
import mchorse.bbs_mod.camera.clips.modifiers.AngleClip;
import mchorse.bbs_mod.camera.clips.modifiers.DollyZoomClip;
import mchorse.bbs_mod.camera.clips.modifiers.DragClip;
import mchorse.bbs_mod.camera.clips.modifiers.LookClip;
import mchorse.bbs_mod.camera.clips.modifiers.MathClip;
import mchorse.bbs_mod.camera.clips.modifiers.OrbitClip;
import mchorse.bbs_mod.camera.clips.modifiers.RemapperClip;
import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.camera.clips.modifiers.TranslateClip;
import mchorse.bbs_mod.camera.clips.overwrite.DollyClip;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.clips.overwrite.PathClip;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;
import mchorse.bbs_mod.settings.values.core.ValuePlacement;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.actions.UIAttackActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIBreakBlockActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIChatActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UICommandActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIDamageActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIInteractBlockActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIItemDropActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIPlaceBlockActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIReleaseUseItemActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UISwipeActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIUseBlockItemActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIUseItemActionClip;
import mchorse.bbs_mod.ui.film.clips.widgets.UIEnvelope;
import mchorse.bbs_mod.ui.film.clips.widgets.UIPlacement;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.ScrollMemory;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.TimeUtilsClient;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.undo.IUndo;

public abstract class UIClip <T extends Clip> extends UIElement
{
    private static final Map<Class, IUIClipFactory> FACTORIES = new HashMap<>();
    private static final ScrollMemory<Class> SCROLLS = new ScrollMemory<>();

    public T clip;
    public IUIClipsDelegate editor;

    public UIToggle enabled;
    public UITextbox title;
    public UITrackpad layer;
    public UITrackpad tick;
    public UITrackpad duration;

    public UIEnvelope envelope;

    public UIScrollView panels;

    /** How each bound widget reads its property back - see {@link #bind(Object, Runnable)}. */
    private final List<Runnable> fillers = new ArrayList<>();

    /**
     * Fills the registry. Called by BBS while it initialises, and followed by the event that
     * lets addons add to it.
     *
     * <p>This used to be a static initialiser, which ran whenever something first touched the
     * class — a moment nobody chose and an addon could not aim at.</p>
     */
    public static void setup()
    {
        register(IdleClip.class, UIIdleClip::new);
        register(DollyClip.class, UIDollyClip::new);
        register(PathClip.class, UIPathClip::new);
        register(KeyframeClip.class, UIKeyframeClip::new);
        register(TranslateClip.class, UITranslateClip::new);
        register(AngleClip.class, UIAngleClip::new);
        register(DragClip.class, UIDragClip::new);
        register(ShakeClip.class, UIShakeClip::new);
        register(MathClip.class, UIMathClip::new);
        register(LookClip.class, UILookClip::new);
        register(TrackerClientClip.class, UITrackerClip::new);
        register(OrbitClip.class, UIOrbitClip::new);
        register(RemapperClip.class, UIRemapperClip::new);
        register(AudioClientClip.class, UIAudioClip::new);
        register(VideoClientClip.class, UIVideoClip::new);
        register(SubtitleClip.class, UISubtitleClip::new);
        register(ImageClip.class, UIImageClip::new);
        register(CurveClientClip.class, UICurveClip::new);
        register(DollyZoomClip.class, UIDollyZoomClip::new);

        register(ChatActionClip.class, UIChatActionClip::new);
        register(CommandActionClip.class, UICommandActionClip::new);
        register(PlaceBlockActionClip.class, UIPlaceBlockActionClip::new);
        register(InteractBlockActionClip.class, UIInteractBlockActionClip::new);
        register(BreakBlockActionClip.class, UIBreakBlockActionClip::new);
        register(UseItemActionClip.class, UIUseItemActionClip::new);
        register(UseBlockItemActionClip.class, UIUseBlockItemActionClip::new);
        register(ReleaseUseItemActionClip.class, UIReleaseUseItemActionClip::new);
        register(AttackActionClip.class, UIAttackActionClip::new);
        register(DamageActionClip.class, UIDamageActionClip::new);
        register(ItemDropActionClip.class, UIItemDropActionClip::new);
        register(SwipeActionClip.class, UISwipeActionClip::new);
    }

    public static <T extends Clip> void register(Class<T> clazz, IUIClipFactory<T> factory)
    {
        FACTORIES.put(clazz, factory);
    }

    public static void saveScroll(UIClip editor)
    {
        if (editor != null)
        {
            SCROLLS.save(editor.clip.getClass(), editor.panels);
        }
    }

    public void restoreScroll()
    {
        SCROLLS.restore(this.clip.getClass(), this.panels);
    }

    public static UIClip createPanel(Clip clip, IUIClipsDelegate delegate)
    {
        IUIClipFactory factory = FACTORIES.get(clip.getClass());

        return factory == null ? null : factory.create(clip, delegate);
    }

    public UIClip(T clip, IUIClipsDelegate editor)
    {
        this.clip = clip;
        this.editor = editor;

        this.enabled = this.toggle(UIKeys.CAMERA_PANELS_ENABLED, clip.enabled);
        this.title = this.textbox(1000, this.clip.title);
        this.title.tooltip(UIKeys.CAMERA_PANELS_TITLE_TOOLTIP);
        this.layer = this.bind(new UITrackpad((v) -> this.editor.editMultiple(this.clip.layer, v.intValue())), () -> this.layer.setValue(this.clip.layer.get()));
        this.layer.limit(0, Integer.MAX_VALUE, true).tooltip(UIKeys.CAMERA_PANELS_LAYER);
        this.tick = new UITrackpad((v) -> this.editor.editMultiple(this.clip.tick, (int) TimeUtils.fromTime(v)));
        this.tick.limit(0, Integer.MAX_VALUE, true).tooltip(UIKeys.CAMERA_PANELS_TICK);
        this.duration = new UITrackpad((v) ->
        {
            this.editor.editMultiple(this.clip.duration, (int) TimeUtils.fromTime(v));
            this.updateDuration((int) TimeUtils.fromTime(v));
        });
        this.duration.limit(1, Integer.MAX_VALUE, true).tooltip(UIKeys.CAMERA_PANELS_DURATION);
        this.envelope = new UIEnvelope(this);
        this.envelope.channel.setUndoId("envelope_keyframes");

        boolean horizontal = BBSSettings.isHorizontalClipEditorEffective();

        this.panels = new UIScrollView(horizontal ? ScrollDirection.HORIZONTAL : ScrollDirection.VERTICAL);
        this.panels.scroll.cancelScrolling();

        if (horizontal)
        {
            this.panels.full(this).column(UIConstants.MARGIN).scroll().width(140).padding(UIConstants.SCROLL_PADDING);
        }
        else
        {
            this.panels.full(this).column(UIConstants.MARGIN).scroll().vertical().stretch().padding(UIConstants.SCROLL_PADDING);
        }

        this.registerUI();
        this.registerPanels();

        this.add(this.panels);
    }

    protected void registerUI()
    {}

    protected void registerPanels()
    {
        this.panels.add(UI.row(this.title, this.enabled.label(IKey.EMPTY).w(26)));
        this.panels.add(UI.row(this.layer, this.tick, this.duration));

        this.addEnvelopes();
    }

    protected void addEnvelopes()
    {
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_ENVELOPES_TITLE, this.envelope));
    }

    /**
     * Wrap a group of fields into a collapsible section. The shared way clip panels group
     * their parameters, so every clip type stays consistent.
     */
    protected UISection section(IKey title, UIElement... fields)
    {
        UISection section = new UISection(title);

        section.fields.add(fields);

        return section;
    }

    /**
     * Bind a widget to a clip property: the widget reads the value back by itself, on every frame
     * it is drawn, which is why the helpers below are all a panel needs to spend on a property.
     * Use this directly for a widget the typed helpers don't cover.
     *
     * <p>This is the same binding form panels use — see
     * {@link mchorse.bbs_mod.ui.framework.elements.UIElement#valueBinding(Runnable)}.</p>
     */
    protected <T extends UIElement> T bind(T element, Runnable filler)
    {
        element.valueBinding(filler);

        return element;
    }

    /**
     * The same, for a widget that must not be re-read on every frame: one that rebuilds a group of
     * sub-fields, allocates on each read, or holds a gesture of its own. Such a widget is filled
     * when {@link #fillData()} runs instead — on selection, on undo, on an edit landing.
     */
    protected <T> T bindOnDemand(T element, Runnable filler)
    {
        this.fillers.add(filler);

        return element;
    }

    protected UIToggle toggle(IKey label, ValueBoolean value)
    {
        UIToggle toggle = new UIToggle(label, (b) -> this.editor.editMultiple(value, (v) -> v.set(b.getValue())));

        return this.bind(toggle, () -> toggle.setValue(value.get()));
    }

    protected UITrackpad trackpad(ValueInt value)
    {
        UITrackpad trackpad = new UITrackpad((v) -> this.editor.editMultiple(value, (i) -> i.set(v.intValue())));

        trackpad.integer();

        return this.bind(trackpad, () -> trackpad.setValue(value.get()));
    }

    protected UITrackpad trackpad(ValueFloat value)
    {
        UITrackpad trackpad = new UITrackpad((v) -> this.editor.editMultiple(value, (f) -> f.set(v.floatValue())));

        return this.bind(trackpad, () -> trackpad.setValue(value.get()));
    }

    protected UITrackpad trackpad(ValueDouble value)
    {
        UITrackpad trackpad = new UITrackpad((v) -> this.editor.editMultiple(value, (d) -> d.set(v)));

        return this.bind(trackpad, () -> trackpad.setValue(value.get()));
    }

    protected UITextbox textbox(int maxLength, ValueString value)
    {
        UITextbox textbox = new UITextbox(maxLength, (t) -> this.editor.editMultiple(value, (v) -> v.set(t)));

        return this.bind(textbox, () -> textbox.setText(value.get()));
    }

    protected UIColor color(ValueInt value)
    {
        UIColor color = new UIColor((c) -> this.editor.editMultiple(value, (v) -> v.set(c)));

        return this.bind(color, () -> color.setColor(value.get()));
    }

    protected UIItemStack itemStack(ValueItemStack value)
    {
        UIItemStack itemStack = new UIItemStack((stack) -> this.editor.editMultiple(value, (v) -> v.set(stack)));

        /* Reading copies the stack, so it is not something to do sixty times a second. */
        return this.bindOnDemand(itemStack, () -> itemStack.setStack(value.get()));
    }

    protected UIPlacement placement(ValuePlacement value, Placement defaultPlacement)
    {
        UIPlacement placement = new UIPlacement(defaultPlacement, (p) -> this.editor.editMultiple(value, (v) -> v.set(p.copy())));

        /* Reading refills eight sub-fields and recomputes the grid. */
        return this.bindOnDemand(placement, () -> placement.setPlacement(value.get()));
    }

    protected UIPropTransform transform(ValueTransform value)
    {
        UIPropTransform transform = new UIPropTransform();

        transform.callbacks(
            () -> this.editor.editMultiple(value, IValueNotifier::preNotify),
            () -> this.editor.editMultiple(value, (t) ->
            {
                t.set(transform.getTransform().copy());
                t.postNotify();
            })
        );

        /* The transform holds a gesture of its own: read on every frame it would pull the gizmo
         * back from under the hand dragging it. Bound per frame once the transform widget can say
         * it is being worked in. */
        return this.bindOnDemand(transform, () -> transform.setTransform(value.get()));
    }

    public void handleUndo(IUndo<ValueGroup> undo, boolean redo)
    {
        this.fillData();
    }

    protected void updateDuration(int duration)
    {}

    public void editClip(Position position)
    {
        this.fillData();
    }

    public void fillData()
    {
        TimeUtilsClient.configure(this.tick, 0);
        TimeUtilsClient.configure(this.duration, 1);

        this.title.placeholder(IKey.constant(this.editor.getClipDisplayName(this.clip)));
        this.tick.setValue(TimeUtils.toTime(this.clip.tick.get()));
        this.duration.setValue(TimeUtils.toTime(this.clip.duration.get()));
        this.envelope.fillData();

        for (Runnable filler : this.fillers)
        {
            filler.run();
        }
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("envelope"))
        {
            this.editor.embedView(this.envelope.channel);
            this.envelope.channel.view.editSheet(this.envelope.channel.view.getGraph().getSheets().get(0));
            this.envelope.channel.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.envelope.channel.hasParent())
        {
            data.putString("embed", "envelope");
        }
    }

    public static interface IUIClipFactory <T extends Clip>
    {
        public UIClip create(T clip, IUIClipsDelegate delegate);
    }
}