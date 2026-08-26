package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIRecordOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UIUtils;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Keying a replay by hand at the cursor, as opposed to shooting a take: what gets written
 * depends on which tab is open and which mouse mode is active, so that pressing the key means
 * "key what I am looking at" rather than "key everything".
 *
 * <p>Split out of {@link UIFilmController} as a companion of its own: this is the manual half
 * of recording and answers to the editor's state, not to the take's.
 */
public class FilmKeyframeInsertion
{
    private final UIFilmController controller;

    public FilmKeyframeInsertion(UIFilmController controller)
    {
        this.controller = controller;
    }

    /**
     * Key the replay at the cursor from what the actor is doing right now — the manual
     * counterpart of shooting a take.
     */
    public void insertFrame()
    {
        Replay replay = this.controller.getReplay();

        if (replay == null)
        {
            return;
        }

        UIReplaysEditor.ReplayCategory category = this.controller.panel.replayEditor.getCategory();

        if (category == UIReplaysEditor.ReplayCategory.POSE)
        {
            UIReplaysEditorUtils.insertPoseKeyframesAtTick(replay, this.controller.getTick(), this.controller.panel.replayEditor.getExpandedPoseTabIds());
            return;
        }

        /* Only the Replay tab keys the player's own channels; every other tab (Form, IK,
         * Physics...) has no take on "insert frame" and must not silently write position and
         * rotation keys the animator never asked for. */
        if (category != UIReplaysEditor.ReplayCategory.REPLAY)
        {
            return;
        }

        /* The replay's own tracks */
        if (Window.isCtrlPressed())
        {
            ActorMouseControl.togglePointer(false);

            UIRecordOverlayPanel panel = new UIRecordOverlayPanel(
                UIKeys.FILM_CONTROLLER_INSERT_FRAME_TITLE,
                UIKeys.FILM_CONTROLLER_INSERT_FRAME_DESCRIPTION,
                (groups) ->
                {
                    BaseValue.edit(replay.keyframes, (keyframes) ->
                    {
                        keyframes.record(this.controller.getTick(), this.controller.getCurrentEntity(), groups);
                    });
                }
            );

            panel.onClose((event) -> ActorMouseControl.togglePointer(this.controller.getControlled() != null));

            UIOverlay.addOverlay(this.controller.getContext(), panel);
        }
        else
        {
            List<String> chosenGroups = Arrays.asList(ReplayKeyframes.GROUP_POSITION, ReplayKeyframes.GROUP_ROTATION);

            int mode = this.controller.mouse.getMode();

            if (mode == 1) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_LEFT_STICK);
            else if (mode == 2) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_RIGHT_STICK);
            else if (mode == 3) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_TRIGGERS);
            else if (mode == 4) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_EXTRA1);
            else if (mode == 5) chosenGroups = Collections.singletonList(ReplayKeyframes.GROUP_EXTRA2);

            final List<String> groups = chosenGroups;

            BaseValue.edit(replay.keyframes, (keyframes) ->
            {
                keyframes.record(this.controller.getTick(), this.controller.getCurrentEntity(), groups);
            });
        }
    }

    /**
     * Insert position and rotation keyframes at the current tick from the live
     * Minecraft player's world transform - a quick way to "teleport" the replay
     * to where the player is standing (and facing). Only the transform channels
     * are touched (unlike full player recording), so no stance/velocity noise is
     * added to the replay.
     */
    public void insertPlayerFrame()
    {
        Replay replay = this.controller.getReplay();

        if (replay == null || MinecraftClient.getInstance().player == null)
        {
            return;
        }

        Morph morph = Morph.getMorph(MinecraftClient.getInstance().player);

        if (morph == null)
        {
            return;
        }

        IEntity player = morph.entity;
        int tick = this.controller.getTick();

        BaseValue.edit(replay.keyframes, (keyframes) ->
        {
            keyframes.x.insert(tick, player.getX());
            keyframes.y.insert(tick, player.getY());
            keyframes.z.insert(tick, player.getZ());

            keyframes.yaw.insert(tick, (double) player.getYaw());
            keyframes.pitch.insert(tick, (double) player.getPitch());
            keyframes.headYaw.insert(tick, (double) player.getHeadYaw());
            keyframes.bodyYaw.insert(tick, (double) player.getBodyYaw());
        });

        UIUtils.playClick();
    }
}
