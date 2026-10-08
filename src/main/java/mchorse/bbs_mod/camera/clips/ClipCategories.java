package mchorse.bbs_mod.camera.clips;

import mchorse.bbs_mod.resources.Link;

/**
 * The ids of BBS's own columns in the palette of clip types. A column is registered on the
 * factory with {@link mchorse.bbs_mod.utils.clips.ClipFactory#category(Link)}, a clip type joins
 * one with {@link ClipFactoryData#in(Link)}, and the column's title is the language key
 * {@code bbs.ui.camera.clip_categories.<id>}.
 */
public class ClipCategories
{
    /* Camera clips, after the packages they live in */
    public static final Link OVERWRITE = Link.bbs("overwrite");
    public static final Link MODIFIERS = Link.bbs("modifiers");
    public static final Link MISC = Link.bbs("misc");

    /* Action clips: the ones of their own packages, and the rest together */
    public static final Link GENERAL = Link.bbs("general");
    public static final Link BLOCKS = Link.bbs("blocks");
    public static final Link ITEMS = Link.bbs("items");

    /**
     * Where a clip type goes that names no column, or one its factory does not have. It comes
     * last, and only when something is in it.
     */
    public static final Link OTHER = Link.bbs("other");
}
