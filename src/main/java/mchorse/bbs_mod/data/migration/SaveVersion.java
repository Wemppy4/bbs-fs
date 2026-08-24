package mchorse.bbs_mod.data.migration;

import mchorse.bbs_mod.data.types.MapType;

/**
 * Version of the format BBS' own documents are saved in — one number for the whole mod, stamped at
 * the root of every self-standing save (see {@code BaseManager}).
 *
 * <p>It is deliberately <em>not</em> a per-document-kind counter. A form is not a file but a
 * fragment living inside films, model blocks, morphs and other body parts, so a migration that
 * reshapes a form has to be reachable from every container at once; separate counters would mean
 * the same converter sitting on three independent ladders with three different step numbers. One
 * number costs a document a walk past migrations that didn't concern it, which is a no-op.
 *
 * <p>It is also not a {@code Value} inside the document. The version describes the file, not the
 * thing the user authored — as a value it would show up in panels, in undo, and be editable.
 *
 * <p>Do not confuse it with the {@code "BBS1"} magic in {@code DataStorage}: that identifies the
 * binary container, a layer below the meaning of what is inside it.
 */
public class SaveVersion
{
    /** Key the version is stamped under, at the root of a document. */
    public static final String KEY = "bbs_version";

    /**
     * Version this build writes. Bumped by any migration that changes anything on disk, whichever
     * document kind it belongs to.
     *
     * <p>1 — versioning itself; the shape of the data is unchanged from the versionless files.
     */
    public static final int CURRENT = 1;

    /** Documents written before versioning existed: they carry no {@link #KEY} at all. */
    public static final int LEGACY = 0;

    /** Version a just-read document was written by. Absence of the key means {@link #LEGACY}. */
    public static int read(MapType data)
    {
        return data == null ? LEGACY : data.getInt(KEY, LEGACY);
    }

    /** Mark a document as written by this build. Called on the way out, for every save. */
    public static void stamp(MapType data)
    {
        if (data != null)
        {
            data.putInt(KEY, CURRENT);
        }
    }
}
