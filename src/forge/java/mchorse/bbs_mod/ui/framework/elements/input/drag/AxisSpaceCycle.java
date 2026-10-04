package mchorse.bbs_mod.ui.framework.elements.input.drag;
import java.util.List;

public final class AxisSpaceCycle {
    private AxisSpaceCycle() {}

    public static List<TransformSpace> spaces(TransformOp op, TransformSpace base) {
        if (op == TransformOp.SCALE) {
            return java.util.Arrays.asList(base);
        }

        return base.isLocal()
            ? java.util.Arrays.asList(base, TransformSpace.GLOBAL)
            : java.util.Arrays.asList(base, TransformSpace.LOCAL);
    }
}
