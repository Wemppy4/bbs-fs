package mchorse.bbs_mod.film.sequence;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.ContentType;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The films of the world the game is in that a sequence uses, loaded once and kept while the
 * sequence is open. Over the network a film takes a moment to arrive, so asking for one either
 * answers right away or once it is here.
 *
 * <p>What is kept is what was saved: a film edited in the film editor since is picked up again
 * once this is {@link #clear() cleared} (the sequence editor does it whenever it comes back on
 * screen).
 */
public class SequenceFilms
{
    private final Map<String, Film> films = new HashMap<>();
    private final Map<String, String> fingerprints = new HashMap<>();
    private final Map<String, List<Consumer<Film>>> waiting = new HashMap<>();

    /**
     * The film, or null while it is still on its way (it is asked for then).
     */
    public Film get(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        Film film = this.films.get(id);

        if (film == null)
        {
            this.request(id, null);
        }

        return film;
    }

    /**
     * Hands the film to the callback once it is here; null when there is no such film.
     */
    public void request(String id, Consumer<Film> callback)
    {
        if (this.films.containsKey(id))
        {
            if (callback != null)
            {
                callback.accept(this.films.get(id));
            }

            return;
        }

        boolean asked = this.waiting.containsKey(id);
        List<Consumer<Film>> callbacks = this.waiting.computeIfAbsent(id, (k) -> new ArrayList<>());

        if (callback != null)
        {
            callbacks.add(callback);
        }

        if (asked)
        {
            return;
        }

        ContentType.FILMS.getRepository().load(id, (data) -> MinecraftClient.getInstance().execute(() ->
        {
            Film film = (Film) data;
            List<Consumer<Film>> list = this.waiting.remove(id);

            if (film != null)
            {
                this.films.put(id, film);
            }

            if (list != null)
            {
                for (Consumer<Film> consumer : list)
                {
                    consumer.accept(film);
                }
            }
        }));
    }

    /**
     * The fingerprint of the film as it is now (see {@link FilmClip#fingerprint(Film)}), or null
     * while the film is on its way.
     */
    public String getFingerprint(String id)
    {
        String fingerprint = this.fingerprints.get(id);

        if (fingerprint == null)
        {
            Film film = this.get(id);

            if (film != null)
            {
                fingerprint = FilmClip.fingerprint(film);

                this.fingerprints.put(id, fingerprint);
            }
        }

        return fingerprint;
    }

    public void clear()
    {
        this.films.clear();
        this.fingerprints.clear();
    }

    /**
     * How long a film runs when it is recorded: its camera's length, or the length of its actors
     * when it has no camera.
     */
    public static int getDuration(Film film)
    {
        int duration = film.camera.calculateDuration();

        return duration > 0 ? duration : Math.max(1, film.calculateDuration());
    }
}
