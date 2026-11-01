package mchorse.bbs_mod.ui.film.utils;

import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;
import mchorse.bbs_mod.utils.Timer;

import java.util.HashSet;
import java.util.Set;

public class UIFilmUndoHandler extends UIFormUndoHandler
{
    private Timer actionsTimer = new Timer(100);
    private Set<BaseValue> syncData = new HashSet<>();

    public UIFilmUndoHandler(UIFilmPanel panel)
    {
        super(panel);
    }

    @Override
    public void handlePreValues(BaseValue baseValue, int flag)
    {
        /* time_spent_active is a passive counter updated every second; it should not
         * pollute undo history with dozens of entries per minute */
        if (baseValue.getPath().getLast().equals("time_spent_active"))
        {
            return;
        }

        /* Opening and closing a category of the replay list is a way of looking at the film, not a
         * change to it; it is saved with the film all the same, but Ctrl+Z has nothing to say to it. */
        if (baseValue.getPath().getLast().equals("expanded") && baseValue.getPath().strings.contains("replay_categories"))
        {
            return;
        }

        /* A take writes keyframes on every tick, and FilmRecordingController puts the whole take
         * into the history as one edit when it stops. Letting the ticks in as well left a second
         * entry under that one, so undoing a take took two Ctrl+Z. */
        if (((UIFilmPanel) this.uiElement).getController().isRecording())
        {
            return;
        }

        super.handlePreValues(baseValue, flag);
    }

    @Override
    protected void handleValue(BaseValue value)
    {
        super.handleValue(value);

        /* The value itself declares whether the server's copy needs it (Film marks the replays
         * subtree) — this used to be a hand-written list of path endings that kept falling
         * behind the data model, silently keeping new channels off the server. */
        if (value.isSynced())
        {
            /* TODO: Variant A for the lazy-channel desync — if 'value' is a keyframe
             * inside a channel, promote it to its parent KeyframeChannel here so the
             * sync sends the whole channel ('properties/<key>') instead of an indexed
             * keyframe path ('properties/<key>/0'). A channel created client-side by
             * FormProperties.getOrCreate during UI building (UIReplaysEditor
             * .collectFormPropertySheets) is never synced, so the server lacks it and
             * the indexed path can't be resolved. Currently handled reactively by the
             * full-film resync request (ServerNetwork.requestFilmResync). */
            this.syncData.add(value);
            this.actionsTimer.mark();
        }
        else if (value instanceof ValueGroup group)
        {
            /* Edits from different corners of the film are folded into the place they all share
             * (UIFormUndoHandler.reduceUndoRedundancy), and for a take's end - the keyframes, the
             * film's HUD values and the action clips the server sends back - that is the film
             * itself, which isn't synced as a whole. Its synced part still has to go: the take's
             * dropped items and swings stayed off the server's copy until an unrelated edit
             * happened to touch the replays (Wemppy, 2026-11-01). */
            this.addSyncedParts(group);
        }
    }

    private void addSyncedParts(ValueGroup group)
    {
        for (BaseValue child : group.getAll())
        {
            if (child.isSynced())
            {
                this.syncData.add(child);
                this.actionsTimer.mark();
            }
            else if (child instanceof ValueGroup childGroup)
            {
                this.addSyncedParts(childGroup);
            }
        }
    }

    @Override
    protected void handleTimers()
    {
        super.handleTimers();

        if (this.actionsTimer.checkReset())
        {
            for (BaseValue syncData : this.syncData)
            {
                ClientNetwork.sendSyncData(((UIFilmPanel) this.uiElement).getData().getId(), syncData);
            }

            this.syncData.clear();
        }
    }

}