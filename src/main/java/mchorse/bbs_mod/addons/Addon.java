package mchorse.bbs_mod.addons;

import mchorse.bbs_mod.api.BBSAddonSwitch;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * One BBS addon: the mod's own passport from {@code fabric.mod.json} plus what BBS saw it do.
 */
public class Addon
{
    public final ModContainer mod;

    /** The addon's entry point objects, the common one and the client one. */
    public final List<Object> subscribers = new CopyOnWriteArrayList<>();

    /** Ids of the settings modules the addon registered, in order. */
    public final List<String> settings = new CopyOnWriteArrayList<>();

    private volatile Throwable error;
    private volatile String errorWhere;

    public Addon(ModContainer mod)
    {
        this.mod = mod;
    }

    public ModMetadata getMetadata()
    {
        return this.mod.getMetadata();
    }

    public String getId()
    {
        return this.getMetadata().getId();
    }

    public String getName()
    {
        return this.getMetadata().getName();
    }

    public String getVersion()
    {
        return this.getMetadata().getVersion().getFriendlyString();
    }

    public String getDescription()
    {
        return this.getMetadata().getDescription();
    }

    public String getAuthors()
    {
        return this.getMetadata().getAuthors().stream().map(Person::getName).collect(Collectors.joining(", "));
    }

    /** The page from {@code contact.homepage}, the only link the window offers. */
    public Optional<String> getHomepage()
    {
        return this.getMetadata().getContact().get("homepage");
    }

    public Optional<String> getIconPath(int size)
    {
        return this.getMetadata().getIconPath(size);
    }

    public BBSAddonSwitch getSwitch()
    {
        for (Object subscriber : this.subscribers)
        {
            if (subscriber instanceof BBSAddonSwitch addonSwitch)
            {
                return addonSwitch;
            }
        }

        return null;
    }

    public boolean hasError()
    {
        return this.error != null;
    }

    /** Only the first failure is kept: later ones are usually its consequences. */
    void fail(String where, Throwable error)
    {
        if (this.error == null)
        {
            this.errorWhere = where;
            this.error = error;
        }
    }

    /** What "copy error" puts on the clipboard: enough for the addon's author to act on. */
    public String getErrorReport()
    {
        if (this.error == null)
        {
            return "";
        }

        StringWriter writer = new StringWriter();

        this.error.printStackTrace(new PrintWriter(writer));

        return this.getName() + " " + this.getVersion() + " (" + this.getId() + ")\n"
            + "Failed in: " + this.errorWhere + "\n\n"
            + writer;
    }
}
