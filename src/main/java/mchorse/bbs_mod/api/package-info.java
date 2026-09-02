/**
 * BBS's addon API.
 *
 * <p>An addon declares its subscriber in {@code fabric.mod.json} under the {@code bbs-addon}
 * entry point (both sides) and {@code bbs-client-addon} (the client only, for the events declared
 * in the client source set), implementing {@link mchorse.bbs_mod.api.BBSAddonMod}. Its
 * {@link mchorse.bbs_mod.api.Subscribe}-annotated methods are then called by BBS as it registers
 * its own things, and that is where an addon registers its own.</p>
 *
 * <p><b>What is and isn't a contract.</b> This package and its sub-packages are; the rest of BBS
 * is not. A signature in here does not change without {@link mchorse.bbs_mod.api.BBSApi#VERSION}
 * changing with it, and the whole of it is dumped into {@code api/bbs-api.txt} in the repository,
 * so a change shows up in the diff of the commit that made it.</p>
 *
 * <p>See {@code ADDONS.md} for the recipes.</p>
 */
package mchorse.bbs_mod.api;
