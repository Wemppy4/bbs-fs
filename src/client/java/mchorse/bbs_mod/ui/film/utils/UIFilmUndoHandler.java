package mchorse.bbs_mod.ui.film.utils;

import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
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