package mchorse.bbs_mod.ui.framework.elements.input.drag;
import java.util.List;

public final class AxisSpaceCycle {
    private AxisSpaceCycle() {}

    public static List<TransformSpace> spaces(TransformOp op, TransformSpace base) {
        if (op == TransformOp.SCALE) {
            return List.of(base);
        }

        return base.isLocal()
            ? List.of(base, TransformSpace.GLOBAL)
            : List.of(base, TransformSpace.LOCAL);
    }
}
