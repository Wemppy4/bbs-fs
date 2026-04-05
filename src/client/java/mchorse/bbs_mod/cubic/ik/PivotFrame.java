package mchorse.bbs_mod.cubic.ik;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public record PivotFrame(Vector3f position, Quaternionf parentRotation)
{
}

