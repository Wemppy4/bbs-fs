package mchorse.bbs_mod.api;

/**
 * Container of {@link Subscribe} handlers. A Forge {@code @Mod} entrypoint implementing this
 * interface is registered before BBS's common registry events. Separate entrypoints use
 * {@link BBSAddon}; client classes must set {@code clientOnly = true}, so Forge can discover
 * their metadata without loading them on a dedicated server.
 */
public interface BBSAddonMod {}