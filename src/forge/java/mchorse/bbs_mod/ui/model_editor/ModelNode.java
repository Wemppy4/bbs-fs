package mchorse.bbs_mod.ui.model_editor;

/**
 * A row of the model editor's tree: a group of the model, or one cube of a group.
 *
 * <p>The record is the row's ADDRESS — a group by its name, a cube by its group's name and its
 * place among that group's cubes — and nothing more. The objects behind it are replaced whenever
 * the model is reloaded (every undo step, every save), so anything that held them would go stale;
 * an address is looked up again on the model as it stands. Two rows with the same address are the
 * same row, which is what lets the tree keep its pick across a rebuild.</p>
 */
@com.github.bsideup.jabel.Desugar
public record ModelNode(Kind kind, String group, int cube)
{
    public enum Kind
    {
        GROUP, CUBE
    }

    public static ModelNode group(String id)
    {
        return new ModelNode(Kind.GROUP, id, -1);
    }

    public static ModelNode cube(String group, int index)
    {
        return new ModelNode(Kind.CUBE, group, index);
    }

    public boolean isGroup()
    {
        return this.kind == Kind.GROUP;
    }

    public boolean isCube()
    {
        return this.kind == Kind.CUBE;
    }

    /** The address as text — what an undo step merges by. */
    public String key()
    {
        return this.isCube() ? "c:" + this.group + "#" + this.cube : "g:" + this.group;
    }
}
