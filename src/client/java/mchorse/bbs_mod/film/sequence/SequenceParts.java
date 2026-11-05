package mchorse.bbs_mod.film.sequence;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;

import java.io.File;

/**
 * Where the recorded pieces of the sequences' films are kept: a folder of their own next to the
 * sequences (hidden from their list by its underscore), one folder per sequence. They are assets
 * under the {@link #SOURCE} source, so a piece plays like any video of a video clip.
 */
public class SequenceParts
{
    public static final String SOURCE = "sequences";

    public static File getFolder()
    {
        return new File(BBSMod.getAssetsFolder().getParentFile(), "data/sequences/_parts");
    }

    /**
     * The folder the pieces of the given sequence go into.
     */
    public static File getFolder(String sequenceId)
    {
        return new File(getFolder(), sequenceId);
    }

    public static Link getLink(String sequenceId, String fileName)
    {
        return new Link(SOURCE, sequenceId + "/" + fileName);
    }
}
