package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.camera.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** The editor's block/entity ray, using Forge 1.12 collision boxes and native results. */
public class RayTracing
{
    public static Vec3d fromVector3d(Vector3d v) { return new Vec3d(v.x,v.y,v.z); }
    public static Vec3d fromVector3f(Vector3f v) { return new Vec3d(v.x,v.y,v.z); }
    public static RayTraceResult rayTrace(World world,Camera camera,double distance)
    {
        return rayTrace(world,fromVector3d(camera.position),fromVector3f(camera.getLookDirection()),distance);
    }
    public static RayTraceResult rayTrace(World world,Vec3d position,Vec3d direction,double distance)
    {
        Vec3d end=position.add(direction.normalize().scale(distance));
        RayTraceResult result=world.rayTraceBlocks(position,end,false,false,false);
        return result!=null ? result : new RayTraceResult(RayTraceResult.Type.MISS,end,null,new BlockPos(end));
    }
    public static RayTraceResult rayTraceEntity(World world,Camera camera,double distance)
    {
        return rayTraceEntity(world,fromVector3d(camera.position),fromVector3f(camera.getLookDirection()),distance);
    }
    public static RayTraceResult rayTraceEntity(World world,Vec3d position,Vec3d direction,double distance)
    {
        return rayTraceEntity(null,world,position,direction,distance);
    }
    public static RayTraceResult rayTraceEntity(Entity owner,World world,Vec3d position,Vec3d direction,double distance)
    {
        RayTraceResult closest=rayTrace(world,position,direction,distance);
        double nearest=closest.typeOfHit==RayTraceResult.Type.BLOCK ? closest.hitVec.squareDistanceTo(position) : distance*distance;
        Vec3d delta=direction.normalize().scale(distance),end=position.add(delta);
        AxisAlignedBB search=new AxisAlignedBB(position.x-.5,position.y-.5,position.z-.5,position.x+.5,position.y+.5,position.z+.5)
            .expand(delta.x,delta.y,delta.z).grow(1);
        for(Entity candidate:world.getEntitiesInAABBexcluding(owner,search,e -> EntitySelectors.NOT_SPECTATING.apply(e)&&e.canBeCollidedWith()))
        {
            AxisAlignedBB box=candidate.getEntityBoundingBox().grow(candidate.getCollisionBorderSize());
            RayTraceResult intercept=box.calculateIntercept(position,end);
            Vec3d point=box.contains(position) ? position : intercept==null ? null : intercept.hitVec;
            if(point==null)continue;
            double squared=position.squareDistanceTo(point);
            if(squared<nearest || squared==0)
            {
                if(owner!=null && candidate.getLowestRidingEntity()==owner.getLowestRidingEntity() && !candidate.canRiderInteract())
                {
                    if(nearest==0)closest=new RayTraceResult(candidate,point);
                    continue;
                }
                nearest=squared; closest=new RayTraceResult(candidate,point);
            }
        }
        return closest;
    }
}
