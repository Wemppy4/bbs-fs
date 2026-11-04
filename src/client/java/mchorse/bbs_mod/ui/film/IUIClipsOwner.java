package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * The editor a clips panel ({@link UIClipsPanel}) lives in: its time and playback, its undo, and
 * what its clips play on. The panel asks it for everything beyond the clips themselves, so a
 * clips timeline isn't bound to the film editor - which is one such editor.
 */
public interface IUIClipsOwner extends ICursor
{
    /**
     * The film the clips belong to (its markers, its replays), or {@code null} when they
     * belong to none.
     */
    public Film getFilm();

    /**
     * The camera the preview looks through, which camera clips take their position from.
     */
    public Camera getCamera();

    /**
     * What plays the camera clips, and so knows where each of them put the camera on the
     * current frame.
     */
    public RunnerCameraController getRunner();

    public boolean isFlying();

    public void setFlight(boolean flight);

    public void togglePlayback();

    public boolean canUseKeybinds();

    /**
     * Keeps the last undo apart from whatever is edited next.
     */
    public void markLastUndoNoMerging();

    /**
     * A clip (or none) was picked on one of the owner's clips panels.
     */
    public void pickClip(Clip clip, UIClipsPanel panel);
}
