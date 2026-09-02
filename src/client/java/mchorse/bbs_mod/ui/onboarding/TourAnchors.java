package mchorse.bbs_mod.ui.onboarding;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The places a tour can point at, by name. A panel says "this is the timeline" once, in its
 * constructor; a tour step names the place and never sees the panel.
 *
 * <p>Anchors hand out suppliers, not elements: the editors here are rebuilt on the fly (the
 * keyframe editor on every track change, the form editor on every bone click), so an element
 * held today is a dead element tomorrow. The supplier is asked every frame and answers with
 * whatever is there now — or nothing, and nothing is a legitimate answer: a tab that isn't
 * open, a panel that was redesigned away. The tour skips what it cannot find.</p>
 */
public class TourAnchors
{
    private static final Map<String, Supplier<UIElement>[]> anchors = new HashMap<>();

    /**
     * Name a place. Several suppliers make one anchor out of several elements — a pair of
     * buttons that only mean something together.
     */
    @SafeVarargs
    public static void register(String id, Supplier<UIElement>... elements)
    {
        anchors.put(id, elements);
    }

    /**
     * Where the place is on screen right now, or null when no part of it can be seen. What is
     * hidden behind another dock tab, or not built yet, doesn't count.
     */
    public static Area resolve(String id)
    {
        Supplier<UIElement>[] suppliers = anchors.get(id);

        if (suppliers == null)
        {
            return null;
        }

        Area union = null;

        for (Supplier<UIElement> supplier : suppliers)
        {
            UIElement element = supplier.get();

            if (element == null || !element.canBeSeen() || element.area.w <= 0 || element.area.h <= 0)
            {
                continue;
            }

            Area area = element.area;

            if (union == null)
            {
                union = new Area(area);
            }
            else
            {
                union.setPoints(
                    Math.min(union.x, area.x), Math.min(union.y, area.y),
                    Math.max(union.ex(), area.ex()), Math.max(union.ey(), area.ey())
                );
            }
        }

        return union;
    }
}
