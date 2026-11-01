package mchorse.bbs_mod.ui.film.clips.widgets;

import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.TimeUtilsClient;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.clips.Envelope;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * The clip's fades: "In [ticks] [curve]" and "Out [ticks] [curve]", or the keys that shape it
 * instead. There is no switch of its own: a fade above zero is the envelope being on.
 */
public class UIEnvelope extends UIElement
{
    public UIClip<? extends Clip> panel;

    public UIIcon pre;
    public UIIcon post;
    public UITrackpad fadeIn;
    public UITrackpad fadeOut;
    public UIElement fadeInRow;
    public UIElement fadeOutRow;

    public UIToggle keyframes;
    public UIButton editKeyframes;
    public UIKeyframeEditor channel;

    public UIEnvelope(UIClip<? extends Clip> panel)
    {
        super();

        this.panel = panel;

        this.pre = UIClip.interpIcon(() -> this.get().pre);
        this.post = UIClip.interpIcon(() -> this.get().post);

        this.fadeIn = new UITrackpad((v) -> this.edit((envelope) -> envelope.fadeIn.set((float) TimeUtils.fromTime(v.floatValue()))));
        this.fadeIn.limit(0, Float.MAX_VALUE).tooltip(UIKeys.CAMERA_PANELS_ENVELOPES_START_D, Direction.TOP);
        this.fadeOut = new UITrackpad((v) -> this.edit((envelope) -> envelope.fadeOut.set((float) TimeUtils.fromTime(v.floatValue()))));
        this.fadeOut.limit(0, Float.MAX_VALUE).tooltip(UIKeys.CAMERA_PANELS_ENVELOPES_END_D, Direction.TOP);

        this.fadeInRow = UIClip.labelRow(UIKeys.CAMERA_PANELS_ENVELOPES_IN, this.pre, this.fadeIn);
        this.fadeOutRow = UIClip.labelRow(UIKeys.CAMERA_PANELS_ENVELOPES_OUT, this.post, this.fadeOut);

        this.keyframes = new UIToggle(UIKeys.CAMERA_PANELS_KEYFRAMES, (b) ->
        {
            this.edit((envelope) -> envelope.keyframes.set(b.getValue()));
            this.toggleKeyframes(b.getValue());
        });
        this.editKeyframes = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.panel.editor.embedView(this.channel);
            this.channel.view.resetView();
            this.channel.view.setGraphMode(true);
            this.channel.view.getGraph().clearSelection();
        });
        this.channel = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.panel.editor, consumer));
        this.channel.setUndoId("envelope_keyframes");
        this.channel.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.channel.view, (UIClipsPanel) this.panel.editor, (Clips) this.panel.clip.getParent(), this.panel.clip.tick.get());
        });
        this.channel.view.duration(() -> this.panel.clip.duration.get());

        this.column().vertical().stretch();
    }

    /** Edits the envelope of every picked clip, and lets each say again whether it is on. */
    private void edit(Consumer<Envelope> consumer)
    {
        this.panel.editor.editMultiple(this.get(), (envelope) ->
        {
            consumer.accept(envelope);
            envelope.updateEnabled();
        });
    }

    private void toggleKeyframes(boolean toggled)
    {
        this.removeAll();

        if (toggled)
        {
            this.add(this.editKeyframes);
        }
        else
        {
            this.add(this.fadeInRow, this.fadeOutRow);
        }

        this.add(this.keyframes);

        if (toggled)
        {
            this.add(this.panel.keyFields(this.channel));
        }

        if (this.hasParent())
        {
            this.panel.panels.resize();

            if (toggled)
            {
                this.initiate();
            }
        }
    }

    public void initiate()
    {
        this.channel.view.resetView();

        TimeUtilsClient.configure(this.fadeIn, 0);
        TimeUtilsClient.configure(this.fadeOut, 0);

        this.fillIntervals();
    }

    public void fillData()
    {
        Envelope envelope = this.get();

        this.fillIntervals();
        this.keyframes.setValue(envelope.keyframes.get());
        this.channel.setChannel(envelope.channel, Colors.ACTIVE);

        this.toggleKeyframes(envelope.keyframes.get());
    }

    private void fillIntervals()
    {
        Envelope envelope = this.get();

        this.fadeIn.setValue(TimeUtils.toTime(envelope.fadeIn.get().intValue()));
        this.fadeOut.setValue(TimeUtils.toTime(envelope.fadeOut.get().intValue()));
    }

    public Envelope get()
    {
        return this.panel.clip.envelope;
    }
}
