package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.film.utils.shader.CurveLabels;
import mchorse.bbs_mod.ui.film.utils.shader.UICurvePicker;
import mchorse.bbs_mod.ui.film.utils.shader.WorldCurve;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.KeyframeState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;

/**
 * The curve clip's properties: the clip's own fields, and the ways to the curve picker (the button
 * and right click in the keyframe editor), where the world's curves and the shader pack's options
 * are picked. The tracks themselves are edited in the keyframe editor that replaces the timeline.
 */
public class UICurveClip extends UIClip<CurveClip>
{
    public UIKeyframeEditor keyframes;
    public UIButton edit;
    public UIButton add;

    public UICurveClip(CurveClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("curve_keyframes");

        this.keyframes.view.context((menu) ->
        {
            menu.icon(MenuVerb.ADD, this::openPicker).label(UIKeys.CAMERA_PANELS_CURVE_ADD);

            UIKeyframeSheet sheet = this.keyframes.view.getGraph().getSheet(this.getContext().mouseX, this.getContext().mouseY);

            menu.icon(MenuVerb.REMOVE, () ->
            {
                this.clip.channels.removeChannel(sheet.channel);
                this.fillData();
            }).label(UIKeys.CAMERA_PANELS_CURVE_REMOVE).enabled(sheet != null);
        });

        this.edit = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) -> this.openKeyframes());
        this.registerEditKeys(this.edit, this.keyframes);

        this.add = new UIButton(UIKeys.CAMERA_PANELS_CURVE_ADD, (b) -> this.openPicker());
    }

    private void openKeyframes()
    {
        this.editor.embedView(this.keyframes);
        this.keyframes.view.resetView();
        this.keyframes.view.getGraph().clearSelection();
    }

    private void openPicker()
    {
        List<String> existing = new ArrayList<>();

        for (KeyframeChannel<?> channel : this.clip.channels.getAllKeyframeChannels())
        {
            existing.add(channel.getId());
        }

        UICurvePicker.open(this.getContext(), existing, this::createCurve, this::removeCurve);
    }

    /** A keyframe put on an empty track starts from the value the curve has now, like a new curve */
    private void addKeyframeSheet(KeyframeChannel<?> channel)
    {
        String id = channel.getId();
        int sheetColor = id.hashCode() & Colors.RGB;

        this.keyframes.view.addSheet(new UIKeyframeSheet(id, (IKey) () -> CurveLabels.of(id), sheetColor, channel, null)
            .seed(() -> this.startValue(channel)));
    }

    /* === Curves === */

    /**
     * Makes a track with one keyframe of the value the curve has now ({@link #startValue}), so the
     * picture doesn't change: at the playhead while it's over the clip, at the clip's start
     * otherwise. The keyframe editor opens, since the track is where the curve gets shaped from
     * then on.
     */
    public void createCurve(String channelId)
    {
        if (this.clip.channels.get(channelId) instanceof KeyframeChannel<?>)
        {
            return;
        }

        int tick = this.editor.getCursor() - this.clip.tick.get();
        KeyframeChannel channel = new KeyframeChannel(channelId, CurveClip.isColorChannelId(channelId) ? KeyframeFactories.COLOR : KeyframeFactories.DOUBLE);

        channel.insert(tick >= 0 && tick <= this.clip.duration.get() ? tick : 0, this.startValue(channel));
        this.clip.channels.addChannel(channel);
        this.fillData();

        if (!this.keyframes.hasParent())
        {
            this.openKeyframes();
        }
    }

    public void removeCurve(String channelId)
    {
        if (this.clip.channels.get(channelId) instanceof KeyframeChannel<?> channel)
        {
            this.clip.channels.removeChannel(channel);
            this.fillData();
        }
    }

    /**
     * The value a curve starts from, the one seen now: the world's (read with the film's curves on
     * it), or for a shader option the value a curve clip of the film gives it at the playhead (the
     * top layer winning), else the pack's. A colour for a colour curve.
     */
    private Object startValue(KeyframeChannel<?> channel)
    {
        String id = channel.getId();
        WorldCurve world = WorldCurve.get(id);

        if (world != null)
        {
            return world.start();
        }

        if (id.startsWith(CurveClip.SHADER_CURVES_PREFIX))
        {
            Double curve = this.filmCurveValue(id);

            if (curve != null)
            {
                return curve;
            }

            ShaderCurves.ShaderVariable variable = ShaderCurves.variableMap.get(id.substring(CurveClip.SHADER_CURVES_PREFIX.length()));

            if (variable != null)
            {
                return (double) variable.defaultValue;
            }
        }

        return channel.getFactory().createEmpty();
    }

    private Double filmCurveValue(String channelId)
    {
        int tick = this.editor.getCursor();
        Double value = null;

        for (Clip clip : ((Clips) this.clip.getParent()).getClips(tick))
        {
            if (clip.enabled.get() && clip instanceof CurveClip curveClip
                && curveClip.channels.get(channelId) instanceof KeyframeChannel<?> channel
                && channel.getFactory() == KeyframeFactories.DOUBLE && channel.hasEnabledKeyframes())
            {
                value = (Double) channel.interpolate(tick - clip.tick.get());
            }
        }

        return value;
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:curve"), this.edit, this.add, this.keyFields(this.keyframes)));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        KeyframeState state = this.keyframes.view.getSheets().isEmpty() ? null : this.keyframes.view.cacheState();
        this.keyframes.view.removeAllSheets();

        for (KeyframeChannel<?> channel : this.clip.channels.getAllKeyframeChannels())
        {
            this.addKeyframeSheet(channel);
        }
        if (state != null) this.keyframes.view.applyState(state);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        if (data.getString("embed").equals("curve"))
        {
            this.editor.embedView(this.keyframes);
        }

        super.applyUndoData(data);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        if (this.keyframes.hasParent())
        {
            data.putString("embed", "curve");
        }

        super.collectUndoData(data);
    }
}
