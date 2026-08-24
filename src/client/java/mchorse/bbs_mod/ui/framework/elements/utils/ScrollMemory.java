package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.ui.framework.elements.UIScrollView;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers how far a properties panel was scrolled, keyed by what it was editing, so
 * stepping away and coming back to the same kind of thing lands where it was left. The
 * panels themselves are thrown away and rebuilt on every pick, which is why the memory
 * has to live outside them.
 *
 * <p>Keyed by TYPE, not by instance: two clips of the same kind (or two keyframes of the
 * same value type) show the same fields, so they share a scroll position — and nothing
 * accumulates as films are opened and closed.
 *
 * @param <K> whatever identifies "the same kind of panel" for its owner: a clip's class,
 *            a keyframe's factory.
 */
public class ScrollMemory <K>
{
    private final Map<K, Integer> scrolls = new HashMap<>();

    public void save(K key, UIScrollView view)
    {
        if (key != null && view != null)
        {
            this.scrolls.put(key, (int) view.scroll.getScroll());
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
            view.scroll.setScroll(this.scrolls.getOrDefault(key, 0));
        }
    }
}
