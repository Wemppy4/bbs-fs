package mchorse.bbs_mod.utils.factory;

import mchorse.bbs_mod.resources.Link;

/**
 * A stand-in for a type its factory doesn't know, holding on to the type it stands in for.
 *
 * <p>Something has to happen when data names a type this build has no class for — an addon that
 * is switched off, or a scene authored where one was installed. Dropping it is the one answer
 * that cannot be taken back: the next save writes the absence to disk, and the user's work is
 * gone for good. A stand-in keeps the original data verbatim and hands it back unchanged on
 * save, so switching an addon off stays a decision about the addon and not about the scene.</p>
 */
public interface IUnknownType
{
    /**
     * The type the data named — written back on save in place of one this factory could give it.
     */
    public Link getUnknownType();
}
