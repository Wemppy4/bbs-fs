package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.Pair;

/**
 * Which replay's motion path stays on screen when the selection moves on. Without a pin the
 * path follows whatever is selected, which is exactly wrong while lining one actor up against
 * another's route.
 */
public class MotionPathPin
{
    private final UIFilmController controller;

    public MotionPathPin(UIFilmController controller)
    {
        this.controller = controller;
    }

    private Replay pinnedReplay;
    private Pair<String, Boolean> pinnedBone;

    public boolean isPinned()
    {
        if (this.pinnedReplay != null && this.controller.panel.getData() != null && !this.controller.panel.getData().replays.getList().contains(this.pinnedReplay))
        {
            this.unpin();
        }

        return this.pinnedReplay != null;
    }

    /** Pin the currently selected replay and bone so its motion path stays shown. */
    public void pin()
    {
        Replay replay = this.controller.getReplay();

        this.pinnedReplay = replay;
        this.pinnedBone = replay == null ? null : this.controller.getBone();
    }

    public void unpin()
    {
        this.pinnedReplay = null;
        this.pinnedBone = null;
    }

    public void toggle()
    {
        if (this.isPinned())
        {
            this.unpin();
        }
        else
        {
            this.pin();
        }
    }

    /** The pinned replay, or null when the path should follow the selection. */
    public Replay getReplay()
    {
        return this.pinnedReplay;
    }

    /** The bone the pin was taken on, so the path keeps tracking the same point of the actor. */
    public Pair<String, Boolean> getBone()
    {
        return this.pinnedBone;
    }
}
