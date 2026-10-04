package mchorse.bbs_mod.api.events;

/**
 * Posted on both sides at the very end of BBS's own initialization, when every registry it owns
 * is filled and every addon has had its turn at them.
 *
 * <p>This is where something that has to look at the finished picture belongs — walking the
 * registered forms, say. Registering into a registry from here is too late.</p>
 */
public class BBSReadyEvent
{}
