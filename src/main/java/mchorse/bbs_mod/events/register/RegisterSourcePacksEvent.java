package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack;

public class RegisterSourcePacksEvent
{
    public final AssetProvider provider;

    public RegisterSourcePacksEvent(AssetProvider provider)
    {
        this.provider = provider;
    }

    /**
     * Gives an addon's own assets a source of their own: they can then be addressed as
     * {@code <modId>:...} links anywhere BBS takes one, and are read out of
     * {@code assets/<modId>/assets} in the addon's jar.
     *
     * <p>Sharing BBS's own {@code assets:} source instead would mean fighting over file names
     * with it.</p>
     *
     * @param clazz any class of the addon — the source reads its files through that class, and
     *              listing a folder starts from the jar the class was loaded from, so a class
     *              from another mod would list the wrong jar.
     */
    public void registerAddon(String modId, Class<?> clazz)
    {
        this.provider.register(new InternalAssetsSourcePack(modId, "assets/" + modId + "/assets", clazz));
    }
}
