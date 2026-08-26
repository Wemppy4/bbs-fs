package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.ui.framework.elements.UIScrollView;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers how far something was scrolled, keyed by what it was showing, so stepping
 * away and coming back to the same thing lands where it was left. The views themselves
 * are thrown away and rebuilt (or refilled) on every pick, which is why the memory has to
 * live outside them.
 *
 * <p>For panels the key is a TYPE, not an instance: two clips of the same kind (or two
 * keyframes of the same value type) show the same fields, so they share a scroll position
 * — and nothing accumulates as films are opened and closed. For a film's own timelines the
 * key is the film id.
 *
 * @param <K> whatever identifies "the same scroll" for its owner: a clip's class, a
 *            keyframe's factory, a film id.
 */
public class ScrollMemory <K>
{
    private final Map<K, Double> scrolls = new HashMap<>();

    public void save(K key, UIScrollView view)
    {
        if (key != null && view != null)
        {
            this.save(key, view.scroll.getScroll());
        }
    }

    public void save(K key, double scroll)
    {
        if (key != null)
        {
            this.scrolls.put(key, scroll);
        }
    }

    /**
     * Restore the scroll saved for that kind of panel. Must be called AFTER the panel was
     * laid out, otherwise the scroll gets clamped to 0 against an empty area.
     */
    public void restore(K key, UIScrollView view)
    {
        if (key != null && view != null)
        {
            view.scroll.setScroll((int) this.get(key));
        }
    }

    public boolean has(K key)
    {
        return key != null && this.scrolls.containsKey(key);
    }

    /** The saved scroll, or 0 when nothing was saved for the key. */
    public double get(K key)
    {
        return key == null ? 0 : this.scrolls.getOrDefault(key, 0D);
    }
}
