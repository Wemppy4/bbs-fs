package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.data.model.CubeFace;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.List;

/**
 * How a cube's six sides read in the model editor: the arrow each one is drawn by, and what it is
 * called. The weld rows of the configuration editor and the unwrap rows of the model editor both
 * name sides, and they name them the same way.
 */
public final class ModelFaces
{
    /** The sides in the enum's own order, for a picker to walk. */
    public static final List<CubeFace> ALL = java.util.Collections.unmodifiableList(java.util.Arrays.asList(CubeFace.values()));

    private static final Icon[] ICONS = {Icons.FORWARD, Icons.BACKWARD, Icons.ARROW_RIGHT, Icons.ARROW_LEFT, Icons.ARROW_UP, Icons.ARROW_DOWN};
    private static final IKey[] LABELS = {
        UIKeys.MODEL_EDITOR_FACE_FRONT, UIKeys.MODEL_EDITOR_FACE_BACK, UIKeys.MODEL_EDITOR_FACE_RIGHT,
        UIKeys.MODEL_EDITOR_FACE_LEFT, UIKeys.MODEL_EDITOR_FACE_TOP, UIKeys.MODEL_EDITOR_FACE_BOTTOM
    };

    private ModelFaces()
    {}

    public static Icon icon(CubeFace face)
    {
        return ICONS[face.ordinal()];
    }

    public static IKey label(CubeFace face)
    {
        return LABELS[face.ordinal()];
    }

    /** The icon a side is shown by, from the name a weld calls it; null when the name names none. */
    public static Icon icon(String face)
    {
        CubeFace value = CubeFace.fromName(face);

        return value == null ? null : icon(value);
    }
}
