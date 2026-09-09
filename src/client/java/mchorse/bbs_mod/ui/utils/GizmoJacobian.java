package mchorse.bbs_mod.ui.utils;

import org.joml.Matrix3d;
import org.joml.Matrix3f;

public class GizmoJacobian
{
    public static Matrix3f inverse(Matrix3f jacobian)
    {
        double scale = 0D;

        for (int column = 0; column < 3; column++)
        {
            for (int row = 0; row < 3; row++)
            {
                scale = Math.max(scale, Math.abs(jacobian.get(column, row)));
            }
        }

        if (scale == 0D || !Double.isFinite(scale))
        {
            return new Matrix3f().zero();
        }

        Matrix3d normalized = new Matrix3d(jacobian).scale(1D / scale);

        if (Math.abs(normalized.determinant()) > 1.0E-6D)
        {
            return new Matrix3f().set(normalized.invert().scale(1D / scale).get(new float[9]));
        }

        /* Flattened forms have no depth derivative. Damping solves only the visible motion. */
        Matrix3d transpose = new Matrix3d(normalized).transpose();
        Matrix3d normal = new Matrix3d(transpose).mul(normalized);

        normal.m00(normal.m00() + 1.0E-8D);
        normal.m11(normal.m11() + 1.0E-8D);
        normal.m22(normal.m22() + 1.0E-8D);

        return new Matrix3f().set(normal.invert().mul(transpose).scale(1D / scale).get(new float[9]));
    }
}
