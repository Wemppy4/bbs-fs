package mchorse.bbs_mod.ui.utils;

/** Stable picking identities, shared by the gizmo and form picking map. */
public final class GizmoPickIds
{
    public final static int STENCIL_X = 1;
    public final static int STENCIL_Y = 2;
    public final static int STENCIL_Z = 3;
    public final static int STENCIL_XZ = 4;
    public final static int STENCIL_XY = 5;
    public final static int STENCIL_ZY = 6;
    public final static int STENCIL_SCALE_X = 7;
    public final static int STENCIL_SCALE_Y = 8;
    public final static int STENCIL_SCALE_Z = 9;
    public final static int STENCIL_SCALE_XZ = 10;
    public final static int STENCIL_SCALE_XY = 11;
    public final static int STENCIL_SCALE_ZY = 12;
    public final static int STENCIL_ROTATE_X = 13;
    public final static int STENCIL_ROTATE_Y = 14;
    public final static int STENCIL_ROTATE_Z = 15;
    public final static int STENCIL_TRACKBALL = 16;
    public final static int STENCIL_VIEW = 17;
    public final static int STENCIL_SCREEN = 18;
    public final static int STENCIL_SCALE_ALL = 19;
    public static final int STENCIL_MAX = STENCIL_SCALE_ALL;

    public enum Handle
    {
        MOVE_X(1), MOVE_Y(2), MOVE_Z(3), MOVE_XZ(4), MOVE_XY(5), MOVE_ZY(6),
        SCALE_X(7), SCALE_Y(8), SCALE_Z(9), SCALE_XZ(10), SCALE_XY(11), SCALE_ZY(12),
        ROTATE_X(13), ROTATE_Y(14), ROTATE_Z(15), TRACKBALL(16), VIEW(17), SCREEN(18), SCALE_ALL(19);

        public final int index;
        Handle(int index) { this.index = index; }
        public static Handle byIndex(int index)
        {
            for (Handle handle : values()) if (handle.index == index) return handle;
            return null;
        }
    }

    private GizmoPickIds() {}
}
