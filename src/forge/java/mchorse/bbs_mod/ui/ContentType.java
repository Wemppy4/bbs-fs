package mchorse.bbs_mod.ui;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.model.config.ModelManagerRepository;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.utils.repos.FilmRepository;
import mchorse.bbs_mod.utils.repos.FolderManagerRepository;
import mchorse.bbs_mod.utils.repos.IRepository;
import net.minecraft.client.Minecraft;
import java.util.function.Function;
import java.util.function.Supplier;

/** Original content identities with late binding to the current world and real editor modules. */
public class ContentType
{
    private static final IRepository<? extends ValueGroup> MODEL_REPOSITORY = new ModelManagerRepository();
    private static final IRepository<? extends ValueGroup> FILMS_REMOTE_REPOSITORY = new FilmRepository();
    public static final ContentType MODELS = new ContentType("models", () -> MODEL_REPOSITORY, null);
    public static final ContentType PARTICLES = new ContentType("particles", () -> new FolderManagerRepository<>(BBSModClient.getParticles()), null);
    public static final ContentType FILMS = new ContentType("films", ContentType::getFilmsRepository, null);

    private static IRepository<? extends ValueGroup> getFilmsRepository()
    {
        if (Minecraft.getMinecraft().isIntegratedServerRunning())
            return new FolderManagerRepository<>(BBSMod.getFilms());
        return ClientNetwork.isIsBBSModOnServer() ? FILMS_REMOTE_REPOSITORY
            : new FolderManagerRepository<>(BBSModClient.getLocalFilms());
    }

    private final String id;
    private Supplier<IRepository<? extends ValueGroup>> manager;
    private final Function<UIDashboard, UIDataDashboardPanel> dashboardPanel;
    public ContentType(String id, Supplier<IRepository<? extends ValueGroup>> manager, Function<UIDashboard, UIDataDashboardPanel> dashboardPanel)
    { this.id=id; this.manager=manager; this.dashboardPanel=dashboardPanel; }
    public String getId() { return this.id; }
    /** Called by the actual module before registering its panel. */
    public void bindRepository(Supplier<IRepository<? extends ValueGroup>> manager)
    { this.manager=java.util.Objects.requireNonNull(manager, "repository"); }
    public IRepository<? extends ValueGroup> getRepository()
    {
        if (this.manager == null) throw new IllegalStateException("Content repository is not registered: " + this.id);
        return this.manager.get();
    }
    public UIDataDashboardPanel get(UIDashboard dashboard)
    {
        if (this.dashboardPanel != null) return this.dashboardPanel.apply(dashboard);
        for (UIDashboardPanel panel : dashboard.getPanels().panels)
            if (panel instanceof UIDataDashboardPanel && ((UIDataDashboardPanel) panel).getType() == this)
                return (UIDataDashboardPanel) panel;
        return null;
    }
}
