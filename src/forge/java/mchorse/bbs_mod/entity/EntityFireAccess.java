package mchorse.bbs_mod.entity;

/** Access to the exact native fire timer when a projectile's damage is rejected. */
public interface EntityFireAccess
{
    int bbs$getFireTicks();
    void bbs$setFireTicks(int ticks);
}
