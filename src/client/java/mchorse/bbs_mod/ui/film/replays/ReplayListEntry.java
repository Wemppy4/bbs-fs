package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.categories.CategoryPath;

/**
 * Row in {@link UIReplayList}: a folder (open/closed) or a replay.
 */
public final class ReplayListEntry
{
    public enum Kind
    {
        FOLDER,
        REPLAY
    }

    public final Kind kind;
    /** Full path of the folder — {@code "Crowd/Guards"}; empty on a replay row. */
    public final String folderPath;
    public final Replay replay;
    /** How many folders deep the row sits; 0 at the root. */
    public final int depth;
    /** Bit per ancestor level whose tree guide still runs past this row. */
    public final int lines;
    /** Last row of its folder, which corners its guide instead of teeing it. */
    public final boolean last;
    /** Folder rows: how many replays are in there, counted once when the rows are built. */
    public final int count;
    /** The stripe of the folder this row belongs to, inherited from above it; 0 for none. */
    public final int color;

    private ReplayListEntry(Kind kind, String folderPath, Replay replay, int depth, int lines, boolean last, int count, int color)
    {
        this.kind = kind;
        this.folderPath = folderPath == null ? "" : folderPath;
        this.replay = replay;
        this.depth = depth;
        this.lines = lines;
        this.last = last;
        this.count = count;
        this.color = color;
    }

    public static ReplayListEntry folder(String path, int depth, int lines, boolean last, int count, int color)
    {
        return new ReplayListEntry(Kind.FOLDER, path, null, depth, lines, last, count, color);
    }

    public static ReplayListEntry replay(Replay replay, int depth, int lines, boolean last, int color)
    {
        return new ReplayListEntry(Kind.REPLAY, "", replay, depth, lines, last, 0, color);
    }

    public boolean isReplay()
    {
        return this.kind == Kind.REPLAY;
    }

    public boolean isFolder()
    {
        return this.kind == Kind.FOLDER;
    }

    /** What the folder row shows: the last segment of its path. */
    public String folderName()
    {
        return CategoryPath.name(this.folderPath);
    }
}
