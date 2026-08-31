package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelinePanel;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.factory.IFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIClipsPanel extends UITimelinePanel implements IUIClipsDelegate
{
    public UIClips clips;
    public UIFilmPanel filmPanel;

    private UIClip panel;
    private boolean hasClips;

    public UIClipsPanel(UIFilmPanel panel, IFactory<Clip, ClipFactoryData> factory)
    {
        this.filmPanel = panel;
        this.clips = new UIClips(this, factory);

        this.add(this.clips.full(this));
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
        this.target = target;

        return this;
    }

    public void setClips(Clips clips)
    {
        this.hasClips = clips != null;
        this.clips.setClips(clips);
        this.clips.setVisible(this.hasClips && this.timelineVisible);
    }

    /** Gated on there being clips at all, unlike the parent version. */
    @Override
    public void setTimelineVisible(boolean visible)
    {
        this.timelineVisible = visible;
        this.clips.setVisible(this.hasClips && visible);
    }

    public void editClip(Position position)
    {
        if (this.panel != null)
        {
            Map<Clip, Position> snapshots = this.filmPanel.getRunner().getContext().getSnapshots();
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
        return this.filmPanel.getData();
    }

    @Override
    public Camera getCamera()
    {
        return this.filmPanel.getCamera();
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

            if (this.filmPanel.isFlying())
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

        this.filmPanel.pickClip(clip, this);
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
        this.filmPanel.setFlight(flight);
    }

    @Override
    public boolean isFlying()
    {
        return this.filmPanel.isFlying();
    }

    @Override
    public int getCursor()
    {
        return this.filmPanel.getCursor();
    }

    @Override
    public void setCursor(int tick)
    {
        this.filmPanel.setCursor(tick);
    }

    @Override
    public boolean isRunning()
    {
        return this.filmPanel.isRunning();
    }

    @Override
    public void togglePlayback()
    {
        this.filmPanel.togglePlayback();
    }

    @Override
    public boolean canUseKeybinds()
    {
        return this.filmPanel.canUseKeybinds();
    }

    @Override
    public void fillData()
    {
        if (this.panel != null)
        {
            this.panel.fillData();
        }
    }

    @Override
    public void embedView(UIElement element)
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            context.closeContextMenu();
        }

        this.clips.embedView(element);
    }

    @Override
    public void markLastUndoNoMerging()
    {
        this.filmPanel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
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

        this.clips.scale.view(data.getDouble("x_min"), data.getDouble("x_max"));
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
        data.putDouble("x_min", this.clips.scale.getMinValue());
        data.putDouble("x_max", this.clips.scale.getMaxValue());
        data.putDouble("scroll", this.clips.vertical.getScroll());
    }
}