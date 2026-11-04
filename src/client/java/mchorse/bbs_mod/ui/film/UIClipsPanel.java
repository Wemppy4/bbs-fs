package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.clips.widgets.UIKeyFields;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelinePanel;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipFactory;
import mchorse.bbs_mod.utils.clips.Clips;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A clips timeline with the picked clip's panel beside it (and, {@link #withKeys optionally}, its
 * keys under it). Everything beyond the clips themselves - time, playback, undo, the camera and
 * the film - comes from the editor it lives in, its {@link IUIClipsOwner owner}.
 */
public class UIClipsPanel extends UITimelinePanel implements IUIClipsDelegate
{
    public UIClips clips;
    public final IUIClipsOwner owner;

    private UIClip panel;
    private boolean hasClips;

    /* A clip's keys, opened with its Edit button, under the clips on the same time (see #withKeys) */
    private static final String KEYS_OWNER = "clip_keys";
    /** Room for the cross and the graph switch, and for what keys with no tracks at all say */
    private static final int KEYS_MIN_HEIGHT = UIConstants.ICON_SIZE * 2;

    private UIElement keysArea;
    private UIKeyframeEditor keys;
    /** Where the open keys put the picked key's fields, in the clip's own panel */
    private UIKeyFields keyFields;
    private int keysHeight = -1;
    private final FoldState<String> keysFolds = new FoldState<>();
    private final UIKeyframeSheet.Section keysOwner = UIKeyframeSheet.Section.owner(KEYS_OWNER, () -> this.getClipDisplayName(this.getClip()), Icons.FRUSTUM);

    /* Both time axes as the last sync left them, to tell which of the two moved since */
    private double syncedZoom = Double.NaN;
    private double syncedShift;
    private double syncedKeysZoom;
    private double syncedKeysShift;
    private int syncedTick;
    private double syncedOrigin;

    public UIClipsPanel(IUIClipsOwner owner, ClipFactory factory)
    {
        this.owner = owner;
        this.clips = new UIClips(this, factory);

        this.add(this.clips.full(this));
    }

    /**
     * A clip's Edit button opens its keys under the clips instead of over them: on the same time
     * and without a ruler of their own, the clips' ruler right above serves both. They are as tall
     * as their rows, a graph as tall as it can be, but never so tall that the clips lose their
     * ruler and the picked clip's lane; past that they scroll. The cross in their corner closes
     * them, the button next to it switches the graph. The picked key's fields go into the clip's
     * own panel, in the section of the button that opened the keys, right under it.
     */
    public UIClipsPanel withKeys()
    {
        this.keysArea = new UIElement();
        this.keysFolds.set(KEYS_OWNER, true);

        UIIcon close = new UIIcon(Icons.CLOSE, (b) -> this.embedView(null));
        UIIcon graph = UIKeyframes.modeButton(() -> this.keys == null ? null : this.keys.view);

        close.relative(this.keysArea).x(1F, -UIConstants.ICON_SIZE);
        graph.relative(this.keysArea).x(1F, -UIConstants.ICON_SIZE * 2);
        this.keysArea.add(graph, close);
        this.keysArea.setVisible(false);
        this.add(this.keysArea);

        return this;
    }

    /**
     * Puts these keys under the clips, or takes the open ones away with null. Their key fields go
     * to the clip's panel, their folds are the clips panel's own, so the clip's heading stays as it
     * was left from clip to clip.
     */
    private void showKeys(UIKeyframeEditor keys)
    {
        if (this.keys == keys)
        {
            return;
        }

        if (this.keys != null)
        {
            this.keys.removeFromParent();
        }

        UIKeyFields closed = this.keyFields;

        this.keys = keys;
        this.keyFields = keys == null || this.panel == null ? null : this.panel.getKeyFields(keys);
        this.syncedZoom = Double.NaN;

        if (closed != null && closed != this.keyFields)
        {
            this.fitKeyFields(closed);
        }

        if (keys != null)
        {
            if (this.keyFields != null)
            {
                keys.target(this.keyFields);
                /* The spot is as tall as the fields, so there is no room for a hint while none is picked */
                keys.setEmptyState(() -> null);
            }

            keys.view.setRuler(false);
            keys.resetFlex().relative(this.keysArea).w(1F).h(1F);
            keys.view.getDopeSheet().setExpanded(this.keysFolds);
            keys.setPropertiesVisible(this.propertiesVisible);
            this.keysArea.prepend(keys);
        }

        this.updateKeysVisible();
        this.keysHeight = -1;
        this.layoutKeys();
    }

    private void updateKeysVisible()
    {
        this.keysArea.setVisible(this.keys != null && this.hasClips && this.timelineVisible);
    }

    /** The spot follows the fields in it, and the clip's panel lays out again when it grew or shrank. */
    private void fitKeyFields(UIKeyFields fields)
    {
        if (fields.fit() && this.panel != null)
        {
            this.panel.panels.resize();
        }
    }

    /**
     * As tall as the keys' rows, a graph as tall as it can be, but the clips always keep their
     * ruler and one lane, the picked clip's, in sight.
     */
    private void layoutKeys()
    {
        int height = 0;

        if (this.keys != null)
        {
            int max = Math.max(0, this.area.h - this.clips.getOneLaneHeight());
            int rows = this.keys.view.isEditing() ? max : Math.max(KEYS_MIN_HEIGHT, this.keys.view.getDopeSheet().getYAxis().scrollSize);

            height = Math.min(max, rows);
        }

        if (height == this.keysHeight)
        {
            return;
        }

        this.keysHeight = height;
        this.clips.h(1F, -height);
        this.keysArea.relative(this).y(1F, -height).w(1F).h(height);
        this.resize();

        Clip clip = this.getClip();

        if (clip != null && height > 0)
        {
            this.clips.scrollToLayer(clip.layer.get());
        }
    }

    /** Keeps the open keys under one heading with the clip's name, on the clips' time, as tall as they need. */
    private void updateKeys()
    {
        if (this.getClip() != null)
        {
            this.ownKeys();
            this.syncKeysTime();
        }

        if (this.keyFields != null)
        {
            this.fitKeyFields(this.keyFields);
        }

        this.layoutKeys();
    }

    /** The clip's tracks hang under one heading with the clip's name, its summary on top of them. */
    private void ownKeys()
    {
        boolean changed = false;

        for (UIKeyframeSheet sheet : this.keys.view.getSheets())
        {
            if (sheet.section == null)
            {
                sheet.section = this.keysOwner;
                changed = true;
            }
        }

        if (changed)
        {
            this.keys.view.getDopeSheet().setExpanded(this.keysFolds);
        }
    }

    /**
     * The keys and the clips share one time: a tick stands at the same x in both, so the playhead
     * runs through both as one line. The clips lead; a zoom or scroll made over the keys carries
     * up to the clips. The keys count from the clip's start, hence the clip's tick in between.
     */
    private void syncKeysTime()
    {
        Scale clips = this.clips.getXAxis();
        Scale keys = this.keys.view.getXAxis();
        int tick = this.getClip().tick.get();
        double origin = keys.to(keys.getShift()) - clips.to(clips.getShift());
        boolean clipsMoved = clips.getZoom() != this.syncedZoom || clips.getShift() != this.syncedShift
            || tick != this.syncedTick || origin != this.syncedOrigin;

        if (clipsMoved)
        {
            keys.copy(clips);
            keys.setShift(clips.getShift() - tick + origin / clips.getZoom());
        }
        else if (keys.getZoom() != this.syncedKeysZoom || keys.getShift() != this.syncedKeysShift)
        {
            double zoom = keys.getZoom();

            clips.copy(keys);
            clips.setShift(keys.getShift() + tick - origin / zoom);
        }

        this.syncedZoom = clips.getZoom();
        this.syncedShift = clips.getShift();
        this.syncedKeysZoom = keys.getZoom();
        this.syncedKeysShift = keys.getShift();
        this.syncedTick = tick;
        this.syncedOrigin = origin;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.keys != null)
        {
            this.updateKeys();
        }

        super.render(context);
    }

    @Override
    public void setPropertiesVisible(boolean visible)
    {
        super.setPropertiesVisible(visible);

        if (this.keys != null)
        {
            this.keys.setPropertiesVisible(visible);
        }
    }

    @Override
    protected UIElement getPropertiesPanel()
    {
        return this.panel;
    }

    @Override
    protected UIElement getTimeline()
    {
        return this.clips;
    }

    public UIClipsPanel target(UIElement target)
    {
        this.setTarget(target);
        this.setEmptyState(this::getEmptyLabel);

        return this;
    }

    /**
     * An empty timeline and an empty pick are different problems, and the way out of each is a
     * different gesture. With no clips at all there is nothing to explain — the timeline itself
     * is gone, and the tab belongs to whatever put it away.
     */
    private IKey getEmptyLabel()
    {
        if (!this.hasClips)
        {
            return null;
        }

        Clips clips = this.clips.getClips();

        return clips == null || clips.get().isEmpty()
            ? UIKeys.CAMERA_TIMELINE_EMPTY_ADD
            : UIKeys.CAMERA_TIMELINE_EMPTY_PICK;
    }

    public void setClips(Clips clips)
    {
        this.hasClips = clips != null;
        this.clips.setClips(clips);
        this.clips.setVisible(this.hasClips && this.timelineVisible);

        if (this.keysArea != null)
        {
            this.updateKeysVisible();
        }
    }

    /** Gated on there being clips at all, unlike the parent version. */
    @Override
    public void setTimelineVisible(boolean visible)
    {
        this.timelineVisible = visible;
        this.clips.setVisible(this.hasClips && visible);

        if (this.keysArea != null)
        {
            this.updateKeysVisible();
        }
    }

    public void editClip(Position position)
    {
        if (this.panel != null)
        {
            Map<Clip, Position> snapshots = this.owner.getRunner().getContext().getSnapshots();
            Position newPosition = new Position();
            Position snapshot = snapshots.get(this.panel.clip);

            newPosition.copy(position);

            if (snapshot != null)
            {
                Clip top = this.panel.clip;

                for (Clip clip : snapshots.keySet())
                {
                    if (clip.layer.get() > top.layer.get())
                    {
                        top = clip;
                    }
                }

                Position topPosition = snapshots.get(top);

                if (topPosition != null)
                {
                    newPosition.point.x -= topPosition.point.x - snapshot.point.x;
                    newPosition.point.y -= topPosition.point.y - snapshot.point.y;
                    newPosition.point.z -= topPosition.point.z - snapshot.point.z;

                    newPosition.angle.yaw -= topPosition.angle.yaw - snapshot.angle.yaw;
                    newPosition.angle.pitch-= topPosition.angle.pitch - snapshot.angle.pitch;
                    newPosition.angle.roll -= topPosition.angle.roll - snapshot.angle.roll;
                    newPosition.angle.fov -= topPosition.angle.fov - snapshot.angle.fov;
                }
            }

            this.panel.editClip(newPosition);
        }
    }

    @Override
    public Film getFilm()
    {
        return this.owner.getFilm();
    }

    @Override
    public Camera getCamera()
    {
        return this.owner.getCamera();
    }

    @Override
    public Clip getClip()
    {
        return this.panel == null ? null : this.panel.clip;
    }

    @Override
    public String getClipDisplayName(Clip clip)
    {
        return clip != null ? this.clips.getClipDisplayName(clip) : "";
    }

    @Override
    public void pickClip(Clip clip)
    {
        UIClip.saveScroll(this.panel);

        if (this.panel != null)
        {
            if (this.panel.clip == clip)
            {
                this.panel.fillData();

                return;
            }
            else
            {
                this.panel.removeFromParent();
            }
        }

        /* Another clip, or none: the open keys belonged to the one before, as the Edit view always did */
        if (this.keysArea != null)
        {
            this.showKeys(null);
        }

        if (clip == null)
        {
            this.panel = null;

            this.clips.w(1F, 0);
            this.clips.clearSelection();
            this.resize();

            return;
        }

        try
        {
            this.clips.embedView(null);

            this.panel = UIClip.createPanel(clip, this);
            this.panel.setUndoId("clip_panel");

            this.attachPropertiesPanel(this.panel, 160);
            this.resize();
            this.resizeTarget();
            this.panel.fillData();
            this.panel.setVisible(this.propertiesVisible);
            this.panel.restoreScroll();

            if (this.owner.isFlying())
            {
                this.setCursor(clip.tick.get());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        this.clips.w(1F, this.target == null ? -160 : 0);
        this.resize();
        this.resizeTarget();

        this.owner.pickClip(clip, this);
    }

    private void resizeTarget()
    {
        if (this.target != null)
        {
            this.target.resize();
        }
    }

    @Override
    public void setFlight(boolean flight)
    {
        this.owner.setFlight(flight);
    }

    @Override
    public boolean isFlying()
    {
        return this.owner.isFlying();
    }

    @Override
    public int getCursor()
    {
        return this.owner.getCursor();
    }

    @Override
    public void setCursor(int tick)
    {
        this.owner.setCursor(tick);
    }

    @Override
    public float getCursor(float transition)
    {
        return this.owner.getCursor(transition);
    }

    @Override
    public void setCursor(float tick)
    {
        this.owner.setCursor(tick);
    }

    @Override
    public boolean isRunning()
    {
        return this.owner.isRunning();
    }

    @Override
    public void togglePlayback()
    {
        this.owner.togglePlayback();
    }

    @Override
    public boolean canUseKeybinds()
    {
        return this.owner.canUseKeybinds();
    }

    @Override
    public void fillData()
    {
        if (this.panel != null)
        {
            this.panel.fillData();
        }
    }

    /**
     * A clip's keys go under the clips (see {@link #withKeys}) and closing them leaves the clips
     * as they are; anything else an addon embeds still goes over the clips, the keys closed.
     */
    @Override
    public void embedView(UIElement element)
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            context.closeContextMenu();
        }

        if (this.keysArea != null)
        {
            boolean keys = element instanceof UIKeyframeEditor;

            this.showKeys(keys ? (UIKeyframeEditor) element : null);

            if (keys)
            {
                this.clips.embedView(null);

                return;
            }
        }

        this.clips.embedView(element);
    }

    @Override
    public void markLastUndoNoMerging()
    {
        this.owner.markLastUndoNoMerging();
    }

    @Override
    public <T extends BaseValue> void editMultiple(T property, Consumer<T> consumer)
    {
        DataPath path = property.getRelativePath(this.getClip());

        if (path == null)
        {
            /* The property doesn't belong to the edited clip — apply it as is */
            consumer.accept(property);

            return;
        }

        for (Clip clip : this.clips.getClipsFromSelection())
        {
            /* Clips of other types simply have no such property */
            BaseValue value = clip.findRecursively(path);

            if (value != null && value.getClass() == property.getClass())
            {
                consumer.accept((T) value);
            }
        }
    }

    @Override
    public void editMultiple(ValueInt property, int value)
    {
        int difference = value - property.get();
        List<Clip> clips = this.clips.getClipsFromSelection();

        for (Clip clip : clips)
        {
            ValueInt clipValue = (ValueInt) clip.get(property.getId());
            int newValue = clipValue.get() + difference;

            if (newValue < clipValue.getMin() || newValue > clipValue.getMax())
            {
                return;
            }
        }

        for (Clip clip : clips)
        {
            ValueInt clipValue = (ValueInt) clip.get(property.getId());

            clipValue.set(clipValue.get() + difference);
        }
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        List<Integer> selection = DataStorageUtils.intListFromData(data.getList("selection"));

        this.clips.getXAxis().view(data.getDouble("x_min"), data.getDouble("x_max"));
        this.clips.vertical.setScroll(data.getDouble("scroll"));
        this.clips.vertical.updateTarget();

        this.clips.setSelection(selection);
        this.pickClip(selection.isEmpty() ? null : this.clips.getClips().get(selection.get(selection.size() - 1)));
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.put("selection", DataStorageUtils.intListToData(this.clips.getSelection()));
        data.putDouble("x_min", this.clips.getXAxis().getMinValue());
        data.putDouble("x_max", this.clips.getXAxis().getMaxValue());
        data.putDouble("scroll", this.clips.vertical.getScroll());
    }
}
