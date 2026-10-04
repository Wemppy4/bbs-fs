package mchorse.bbs_mod.cubic.model;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * The background side of model loading: one permanent daemon worker over a thread-safe deque.
 *
 * <p>Keys are taken newest first — a model is queued the frame its cell first renders, so the
 * last key queued is what is on screen right now, and it loads before whatever was scrolled
 * past. The old loader was a plain {@code LinkedList} shared between threads with a
 * stop-and-restart worker: a key enqueued in the wrong moment was lost, and since the manager
 * marks a key as requested exactly once, its cell stayed empty until a full reload.</p>
 */
public class ModelLoader
{
    private final ModelManager manager;
    private final LinkedBlockingDeque<Request> queue = new LinkedBlockingDeque<>();
    private volatile long generation;

    @com.github.bsideup.jabel.Desugar

    private record Request(String key, long generation) {}

    private Thread thread;
    private boolean closed;

    public ModelLoader(ModelManager manager)
    {
        this.manager = manager;
    }

    public synchronized void add(String key)
    {
        if (this.closed) return;
        if (this.thread == null)
        {
            this.thread = new Thread(this::work, "BBS model loader");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        this.queue.offerFirst(new Request(key, this.generation));
    }

    public synchronized void clear()
    {
        this.generation += 1;
        this.queue.clear();
    }

    /** The asset provider was replaced: release the old manager's permanent worker. */
    public synchronized void close()
    {
        this.closed = true;
        this.clear();
        if (this.thread != null) this.thread.interrupt();
    }

    private void work()
    {
        while (true)
        {
            Request model;

            try
            {
                model = this.queue.takeFirst();
            }
            catch (InterruptedException e)
            {
                return;
            }

            try
            {
                synchronized (this.manager)
                {
                    if (model.generation() == this.generation)
                    {
                        this.manager.loadModel(model.key());
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
