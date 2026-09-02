package mchorse.bbs_mod.data.migration;

import mchorse.bbs_mod.data.types.MapType;

/**
 * One step of the ladder from an older {@link SaveVersion} to the current one, for a single kind of
 * document.
 *
 * <p>Migrations work on the <em>raw</em> data, before it is handed to {@code fromData}, and that is
 * not a matter of taste: {@code ValueGroup.fromData} drops keys it does not recognize, so by the
 * time a document has been read into its values, the old shape it was written in is already gone.
 * The window between "bytes parsed" and "values populated" is the only place a converter can exist.
 *
 * <p>The version number is mod-wide but the ladder is per document kind — a step that reshapes
 * films has nothing to say about particle schemes, and simply doesn't appear in their list.
 */
public interface IDataMigration
{
    /**
     * The version this migration upgrades a document <em>from</em>; applying it produces
     * {@code getVersion() + 1}. A document is run through every migration at or above its own
     * version, in ascending order.
     */
    int getVersion();

    /** Reshape the document in place, from {@link #getVersion()} to the next version. */
    void migrate(MapType data);
}
