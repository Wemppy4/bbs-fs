package mchorse.bbs_mod.events;

/**
 * An addon event subscribers container.
 *
 * <p>In {@code fabric.mod.json}, there are two entrypoints for it. {@code bbs-addon} is read on
 * both sides, at the very top of BBS's own initialization. {@code bbs-client-addon} is read on
 * the client only, before BBS posts any of its client-side events — the events declared in the
 * client source set can only be subscribed to from there, since a class mentioning them can't be
 * loaded on a dedicated server.</p>
 */
public interface BBSAddonMod
{}
