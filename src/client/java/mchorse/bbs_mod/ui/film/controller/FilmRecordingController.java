package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIRecordOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector2i;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Shooting a take into a replay: the pre-roll countdown, which groups of channels are being
 * written, and the commit that turns a take into one undoable edit.
 *
 * <p>Split out of {@link UIFilmController} as a companion of its own, the way the orbit camera
 * and the gizmo interaction already are — recording is a small state machine with its own
 * lifetime (arm, count down, roll, commit), and it was interleaved with everything else the
 * controller does.
 *
 * <p>It still asks the controller for the things a take needs but does not own: who is being
 * controlled, the mouse mode, the cursor. The countdown in particular is not private business —
 * it is the ONLY synchronisation between this client-side recorder and the server's action
 * recorder, so it is read from outside while a take is being armed.
 */
public class FilmRecordingController
{
    private final UIFilmController controller;

    private int recordingTick;
    private boolean recording;
    private int recordingCountdown;
    private List<String> recordingGroups;
    private BaseType recordingOld;

    public FilmRecordingController(UIFilmController controller)
    {
        this.controller = controller;
    }

    public boolean isRecording()
    {
        return this.recording;
    }

    public int getRecordingCountdown()
    {
        return this.recordingCountdown;
    }

    public List<String> getRecordingGroups()
    {
        return this.recordingGroups;
    }

    private boolean hasTransformRecordingGroup()
    {
        return this.recordingGroups != null && this.recordingGroups.contains(ReplayKeyframes.GROUP_TRANSFORM);
    }

    public boolean isTransformRecording()
    {
        return this.recording
            && this.recordingCountdown <= 0
            && this.hasTransformRecordingGroup();
    }

    public void startRecording(List<String> groups)
    {
        if (groups != null && groups.contains("outside"))
        {
            MinecraftClient.getInstance().setScreen(null);

            Replay replay = this.controller.panel.replayEditor.getReplay();
            int index = this.controller.panel.getData().replays.getList().indexOf(replay);

            if (index >= 0)
            {
                /* On the mark: started from the editor, at a cursor the editor chose,
                 * so the take begins where the replay itself stands at that tick */
                BBSModClient.getFilms().startRecording(this.controller.panel.getData(), index, this.controller.panel.getCursor(), true);
            }

            return;
        }

        this.recordingTick = this.controller.getTick();
        this.recording = true;
        this.recordingCountdown = Math.max(0, TimeUtils.toTick(BBSSettings.recordingCountdown.get()));
        this.recordingGroups = groups;
        boolean transformRecording = groups != null && groups.contains(ReplayKeyframes.GROUP_TRANSFORM);

        this.recordingOld = transformRecording ? this.controller.getReplay().properties.toData() : this.controller.getReplay().keyframes.toData();

        if (transformRecording)
        {
            if (this.controller.getControlled() != null)
            {
                this.controller.toggleControl();
            }

            this.controller.setMouseMode(0);
        }
        else if (groups != null)
        {
            if (groups.contains(ReplayKeyframes.GROUP_LEFT_STICK))
            {
                this.controller.setMouseMode(1);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_RIGHT_STICK))
            {
                this.controller.setMouseMode(2);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_TRIGGERS))
            {
                this.controller.setMouseMode(3);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_EXTRA1))
            {
                this.controller.setMouseMode(4);
            }
            else if (groups.contains(ReplayKeyframes.GROUP_EXTRA2))
            {
                this.controller.setMouseMode(5);
            }
            else
            {
                this.controller.setMouseMode(0);
            }
        }

        if (!transformRecording && this.controller.getControlled() == null)
        {
            this.controller.toggleControl();
        }

        this.controller.toggleMousePointer(!transformRecording && this.controller.getControlled() != null);

        /* No countdown means the take starts now. Without this nothing ever started it: the countdown
         * branch below is what calls togglePlayback, and it only runs while the counter is still above
         * zero — with the setting at 0 the very first tick fell straight through to "the film is not
         * running, so the take is over" and stopped the recording before a single frame was written. */
        this.startPlayback();
    }

    /**
     * Begin playback for a take, unless the film is already running (the editor can be playing when a
     * recording starts). Never a blind {@code togglePlayback} — that would stop it instead.
     */
    private void startPlayback()
    {
        if (this.recordingCountdown <= 0 && !this.controller.panel.getRunner().isRunning())
        {
            this.controller.panel.togglePlayback();
        }
    }

    public void stopRecording()
    {
        if (!this.recording)
        {
            return;
        }

        boolean transformRecording = this.hasTransformRecordingGroup();

        this.recording = false;
        this.recordingGroups = null;

        if (!transformRecording && this.controller.getControlled() != null)
        {
            this.controller.toggleControl();
        }

        this.controller.panel.setCursor(this.recordingTick);

        if (this.controller.panel.getRunner().isRunning())
        {
            this.controller.panel.togglePlayback();
        }

        if (this.recordingCountdown > 0)
        {
            return;
        }

        Replay replay = this.controller.getReplay();

        if (replay != null && this.recordingOld != null)
        {
            if (transformRecording)
            {
                for (Map.Entry<TrackId, KeyframeChannel> entry : replay.properties.tracks.entrySet())
                {
                    if (entry.getKey().is(TrackKind.BONE))
                    {
                        entry.getValue().simplify();
                    }
                }

                BaseType newData = replay.properties.toData();

                replay.properties.fromData(this.recordingOld);
                replay.properties.preNotify();
                replay.properties.fromData(newData);
                replay.properties.postNotify();

                if (this.controller.panel.replayEditor.getReplay() == replay)
                {
                    this.controller.panel.replayEditor.setReplay(replay, false, UIReplaysEditor.OrbitReaction.SWITCH);
                }
            }
            else
            {
                for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
                {
                    channel.simplify();
                }

                BaseType newData = replay.keyframes.toData();

                replay.keyframes.fromData(this.recordingOld);
                replay.keyframes.preNotify();
                replay.keyframes.fromData(newData);
                replay.keyframes.postNotify();
            }

            this.recordingOld = null;
        }

        this.controller.setMouseMode(ClientNetwork.isIsBBSModOnServer() ? 0 : 1);
    }

    public void pickRecording()
    {
        if (this.controller.panel.replayEditor.getReplay() == null)
        {
            return;
        }

        if (this.recording)
        {
            this.stopRecording();

            return;
        }

        this.controller.toggleMousePointer(false);

        UIRecordOverlayPanel overlay = new UIRecordOverlayPanel(
            UIKeys.FILM_CONTROLLER_RECORD_TITLE,
            UIKeys.FILM_CONTROLLER_RECORD_DESCRIPTION,
            this::startRecording,
            this.controller.panel.replayEditor.getCategory() == UIReplaysEditor.ReplayCategory.POSE
        );
        UIIcon icon = new UIIcon(Icons.UPLOAD, (b) -> overlay.submit(Arrays.asList("outside")));

        icon.tooltip(UIKeys.FILM_GROUPS_OUTSIDE);
        overlay.bar.add(icon);
        overlay.keys().register(Keys.RECORDING_GROUP_OUTSIDE, icon::clickItself);

        UIOverlay.addOverlay(this.controller.getContext(), overlay);
    }

    /** The tick hook: run the countdown down and end the take when the film stops or loops. */
    public void update(RunnerCameraController runner)
    {
        if (this.recording)
        {
            if (this.recordingCountdown > 0)
            {
                this.recordingCountdown -= 1;

                this.startPlayback();
            }

            if (this.recordingCountdown <= 0)
            {
                boolean stopped = !runner.isRunning();

                if (BBSSettings.editorLoop.get())
                {
                    Vector2i loop = this.controller.panel.getLoopingRange();
                    int min = loop.x;
                    int max = loop.y;
                    int ticks = this.controller.panel.getCursor();

                    if (min >= 0 && max >= 0 && min < max && (ticks >= max - 1 || ticks < min) || stopped)
                    {
                        this.stopRecording();
                    }
                }
                else if (stopped)
                {
                    this.stopRecording();
                }
            }
        }
    }
}
