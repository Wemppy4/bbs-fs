package mchorse.bbs_mod.utils;

import com.ibm.icu.impl.Assert;
import mchorse.bbs_mod.utils.MatrixUtils;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public class MatrixUtilsTest
{
    public static void main(String[] args)
    {
        testRotationOrder(MatrixUtils.RotationOrder.YXZ, MatrixUtils.RotationOrder.YXZ,
                45, 45, 45,
                45, 45, 45);

        testRotationOrder(MatrixUtils.RotationOrder.YXZ, MatrixUtils.RotationOrder.XYZ,
                -45, 45, -45,
                45, -45,  -45);

        testRotationOrder(MatrixUtils.RotationOrder.YXZ, MatrixUtils.RotationOrder.XYZ,
                -182, 45, 45,
                45, -182 + 360, 45);

        testRotationOrder(MatrixUtils.RotationOrder.YXZ, MatrixUtils.RotationOrder.XYZ,
                45, 182, 45,
                182, 45, 45);

        testRotationOrder(MatrixUtils.RotationOrder.YXZ, MatrixUtils.RotationOrder.XYZ,
                45, 182, -45,
                182, 45, -45);

        testRotationOrder(MatrixUtils.RotationOrder.YXZ, MatrixUtils.RotationOrder.XYZ,
                45, 182, -245,
                182, 45, -45);
    }

    public static void testRotationOrder(MatrixUtils.RotationOrder order, MatrixUtils.RotationOrder orderOrigin, double angle0, double angle1, double angle2, double expectedX, double expectedY, double expectedZ)
    {
        Matrix3d rot = orderOrigin.getRotationMatrix(Math.toRadians(angle0), Math.toRadians(angle1), Math.toRadians(angle2));
        Vector3d testDirection = rot.transform(new Vector3d(0, 0, 1));
        Vector3d testOrientation = rot.transform(new Vector3d(0, 1, 0));

        Vector3d angles = order.getEulerAngles(rot);
        Matrix3d rotNew = order.getRotationMatrixFromXYZ(angles.x, angles.y, angles.z);
        Vector3d newDirection = rotNew.transform(new Vector3d(0, 0, 1));
        Vector3d newOrientation = rotNew.transform(new Vector3d(0, 1, 0));

        if (newDirection.dot(testDirection) < 0.9999 || newOrientation.dot(testOrientation) < 0.9999)
        {
            Assert.fail("Direction or orientation don't match");
        }
    }
}