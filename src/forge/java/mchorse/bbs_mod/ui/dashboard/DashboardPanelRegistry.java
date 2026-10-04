package mchorse.bbs_mod.ui.dashboard;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import java.util.*;
import java.util.function.*;

/** Real panel constructors registered by each implemented module before warmup.
 * Registration removes compile-time editor cycles; no placeholder panels are constructed. */
public final class DashboardPanelRegistry
{
    public static final class Entry
    {
        public final String id;
        public final int order;
        public final IKey title;
        public final Icon icon;
        public final Function<UIDashboard, UIDashboardPanel> factory;
        private BiConsumer<UIDashboardPanel, MapType> layout;
        private Consumer<UIDashboardPanel> exportSize;
        private Entry(String id, int order, IKey title, Icon icon, Function<UIDashboard, UIDashboardPanel> factory)
        { this.id=id; this.order=order; this.title=title; this.icon=icon; this.factory=factory; }
        public Entry layout(BiConsumer<UIDashboardPanel, MapType> action) { this.layout=action; return this; }
        public Entry exportSize(Consumer<UIDashboardPanel> action) { this.exportSize=action; return this; }
    }
    public static final class PinnedAction
    {
        public final String id; public final Icon icon; public final IKey title; public final Consumer<UIContext> action;
        private PinnedAction(String id, Icon icon, IKey title, Consumer<UIContext> action)
        { this.id=id; this.icon=icon; this.title=title; this.action=action; }
    }
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, PinnedAction> PINNED = new LinkedHashMap<>();
    private static final Map<UIDashboardPanel, Entry> INSTANCES = new WeakHashMap<>();
    public static Entry register(String id, int order, IKey title, Icon icon, Function<UIDashboard, UIDashboardPanel> factory)
    {
        Entry entry = new Entry(id, order, title, icon, factory);
        ENTRIES.put(id, entry);
        return entry;
    }
    public static void registerPinned(String id, Icon icon, IKey title, Consumer<UIContext> action)
    { PINNED.put(id, new PinnedAction(id, icon, title, action)); }
    public static List<Entry> entries()
    {
        List<Entry> result = new ArrayList<>(ENTRIES.values());
        result.sort(Comparator.comparingInt(e -> e.order));
        return result;
    }
    public static Collection<PinnedAction> pinned() { return Collections.unmodifiableCollection(PINNED.values()); }
    static void bind(UIDashboardPanel panel, Entry entry) { INSTANCES.put(panel, entry); }
    public static boolean applyLayout(UIDashboardPanel panel, MapType data)
    {
        Entry entry = INSTANCES.get(panel);
        if (entry == null || entry.layout == null) return false;
        entry.layout.accept(panel, data);
        return true;
    }
    public static void applyExportSize(UIDashboardPanel panel)
    {
        Entry entry = INSTANCES.get(panel);
        if (entry != null && entry.exportSize != null) entry.exportSize.accept(panel);
    }
    private DashboardPanelRegistry() {}
}
