package mchorse.bbs_mod.actions.values;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.EnumUtils;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

public class ValueBlockHitResult extends ValueGroup
{
    public final ValueInt x = new ValueInt("x", 0);
    public final ValueInt y = new ValueInt("y", 0);
    public final ValueInt z = new ValueInt("z", 0);
    public final ValueDouble hitX = new ValueDouble("hitX", 0D);
    public final ValueDouble hitY = new ValueDouble("hitY", 0D);
    public final ValueDouble hitZ = new ValueDouble("hitZ", 0D);
    public final ValueInt direction = new ValueInt("direction", 0);
    public final ValueBoolean inside = new ValueBoolean("inside", false);

    public ValueBlockHitResult(String id)
    {
        super(id);

        this.add(this.x);
        this.add(this.y);
        this.add(this.z);
        this.add(this.hitX);
        this.add(this.hitY);
        this.add(this.hitZ);
        this.add(this.direction);
        this.add(this.inside);
    }

    public void setHitResult(RayTraceResult result)
    {
        this.x.set(result.getBlockPos().getX());
        this.y.set(result.getBlockPos().getY());
        this.z.set(result.getBlockPos().getZ());
        this.hitX.set(result.hitVec.x);
        this.hitY.set(result.hitVec.y);
        this.hitZ.set(result.hitVec.z);
        this.inside.set(false);
        this.direction.set(result.sideHit.ordinal());
    }

    public void shift(double x, double y, double z)
    {
        this.x.set((int) (this.x.get() + x));
        this.y.set((int) (this.y.get() + y));
        this.z.set((int) (this.z.get() + z));
        this.hitX.set(this.hitX.get() + x);
        this.hitY.set(this.hitY.get() + y);
        this.hitZ.set(this.hitZ.get() + z);
    }

    public BlockPos getBlockPos()
    {
        return new BlockPos(this.x.get(), this.y.get(), this.z.get());
    }

    public RayTraceResult getHitResult()
    {
        Vec3d vec = new Vec3d(this.hitX.get(), this.hitY.get(), this.hitZ.get());

        return new RayTraceResult(vec, EnumUtils.getValue(this.direction.get(), EnumFacing.values(), EnumFacing.UP), this.getBlockPos());
    }
}