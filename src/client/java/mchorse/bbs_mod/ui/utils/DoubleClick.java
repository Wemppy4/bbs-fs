package mchorse.bbs_mod.ui.utils;

/**
 * Tells a second click on the same thing from a first one. Feed every click; it answers
 * whether this one came quickly enough after a click on the same item to count as a double.
 *
 * @param <T> what's being clicked
 */
public class DoubleClick<T>
{
    public static final long INTERVAL = 300;

    private final boolean identity;
    private T last;
    private long time;

    /** @param identity match items by reference rather than {@link Object#equals} */
    public DoubleClick(boolean identity)
    {
        this.identity = identity;
    }

    public boolean hit(T item)
    {
        long now = System.currentTimeMillis();
        boolean same = item != null && this.last != null && (this.identity ? item == this.last : item.equals(this.last));
        boolean twice = same && now - this.time < INTERVAL;

        this.last = twice ? null : item;
        this.time = now;

        return twice;
    }
}
