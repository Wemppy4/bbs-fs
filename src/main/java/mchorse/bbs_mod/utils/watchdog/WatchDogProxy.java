package mchorse.bbs_mod.utils.watchdog;

import mchorse.bbs_mod.utils.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Batches the watchdog's events instead of dispatching each one the moment it lands.
 *
 * <p>Loading a set of models creates a burst of file events (each material without a texture
 * makes a folder), and every one of them used to trigger its listeners immediately — for the
 * form sections that meant a full rescan of the models tree plus a UI rebuild PER EVENT, on
 * the main thread. Now events collect in a queue (duplicates dropped) and flush after a short
 * stretch of quiet; a whole burst costs its listeners one pass.</p>
 *
 * <p>Main thread only: the watchdog hands events here through the client executor, and
 * {@link #tick()} runs from the client tick.</p>
 */
public class WatchDogProxy implements IWatchDogListener
{
    /** Client ticks of quiet before the queue flushes; every new event pushes it back. */
    private static final int FLUSH_DELAY = 10;

    private List<IWatchDogListener> listeners = new ArrayList<>();
    private List<Pair<Path, WatchDogEvent>> queue = new ArrayList<>();
    private int tick;

    public void register(IWatchDogListener listener)
    {
        this.listeners.add(listener);
    }

    public void tick()
    {
        if (this.queue.isEmpty())
        {
            return;
        }

        this.tick -= 1;

        if (this.tick > 0)
        {
            return;
        }

        /* Listeners can cause new file events (a rescan may create folders); those belong
         * to the next batch, not to the one being flushed. */
        List<Pair<Path, WatchDogEvent>> batch = new ArrayList<>(this.queue);

        this.queue.clear();

        for (Pair<Path, WatchDogEvent> pair : batch)
        {
            for (IWatchDogListener listener : this.listeners)
            {
                listener.accept(pair.a, pair.b);
            }
        }
    }

    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        this.tick = FLUSH_DELAY;

        for (Pair<Path, WatchDogEvent> pair : this.queue)
        {
            if (pair.b == event && pair.a.equals(path))
            {
                return;
            }
        }

        this.queue.add(new Pair<>(path, event));
    }
}
