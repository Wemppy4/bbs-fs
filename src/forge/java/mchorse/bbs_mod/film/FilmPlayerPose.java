package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.EntityState;
import mchorse.bbs_mod.forms.entities.EntityPose;

import java.util.List;
import java.util.Map;

/** The recorded pose of a player currently driven by a world film, including a paused film. */
@com.github.bsideup.jabel.Desugar
public record FilmPlayerPose(boolean sneaking, EntityPose pose)
{
    /**
     * Resolve ownership afresh so stopping, seeking or replacing a film cannot leave an override
     * behind. Frozen editor frames have no actors and must not hide a playing controller of the
     * same film. The last applicable replay wins, just as in BaseFilmController.updateEndWorld.
     */
    public static FilmPlayerPose get(int entityId)
    {
        Films films = BBSModClient.getFilms();

        if (films == null)
        {
            return null;
        }

        List<BaseFilmController> controllers = films.getControllers();

        for (int c = controllers.size() - 1; c >= 0; c--)
        {
            BaseFilmController controller = controllers.get(c);
            Map<String, Integer> actors = controller.getActors();

            if (actors == null || !actors.containsValue(entityId))
            {
                continue;
            }

            List<Replay> replays = controller.film.replays.getList();

            for (int i = replays.size() - 1; i >= 0; i--)
            {
                Replay replay = replays.get(i);
                Integer actorId = actors.get(replay.getId());

                if (actorId == null || actorId != entityId || i == controller.exception
                    || !replay.enabled.get() || !controller.getEntities().containsKey(replay.getId()))
                {
                    continue;
                }

                int tick = replay.getTick(controller.getTick());
                boolean sneaking = EntityState.isOn(replay.keyframes.state(EntityState.SNEAKING).interpolate(tick));
                boolean swimming = EntityState.isOn(replay.keyframes.state(EntityState.SWIMMING).interpolate(tick));
                boolean gliding = EntityState.isOn(replay.keyframes.state(EntityState.GLIDING).interpolate(tick));

                return new FilmPlayerPose(sneaking, EntityState.pose(gliding, swimming, sneaking));
            }
        }

        return null;
    }
}
