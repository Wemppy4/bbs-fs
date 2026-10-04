package mchorse.bbs_mod.api;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

/**
 * The entry point of BBS's addon API.
 *
 * <p>Everything in {@code mchorse.bbs_mod.api} is a contract: it does not change without
 * {@link #VERSION} changing with it. Everything outside of that package is BBS's own business and
 * moves without notice — an addon reaching into it is on its own, and the breakage shows up in
 * the game rather than on the build.</p>
 */
public final class BBSApi
{
    /**
     * The version of the addon API.
     *
     * <p>It is bumped whenever a contract in {@code mchorse.bbs_mod.api} changes in a way that an
     * addon compiled against the previous one cannot survive, or a new feature set must be
     * distinguishable by addons. Version 2 adds editor, pose and structure extension points;
     * addons requiring version 1 remain compatible.</p>
     */
    public static final int VERSION = 2;

    private BBSApi()
    {}

    /**
     * The version of BBS itself, as its mod metadata reports it — {@code 2.5.2-1.20.4} and such.
     */
    public static String getModVersion()
    {
        ModContainer container = Loader.instance().getIndexedModList().get("bbs");
        return container == null ? "unknown" : container.getVersion();
    }

    public static boolean isAtLeast(int version)
    {
        return VERSION >= version;
    }

    /**
     * Stops an addon built against a newer API from limping along on an older BBS.
     *
     * <p>Without it, the mismatch reads as "the game crashes on the first right click" instead of
     * "this addon does not fit this BBS build" — call it from the addon's entry point.</p>
     */
    public static void requireVersion(String modId, int version)
    {
        if (!isAtLeast(version))
        {
            throw new IllegalStateException(modId + " requires BBS addon API version " + version
                + ", but this BBS (" + getModVersion() + ") provides " + VERSION
                + ". Update BBS, or install a build of " + modId + " made for it.");
        }
    }
}
