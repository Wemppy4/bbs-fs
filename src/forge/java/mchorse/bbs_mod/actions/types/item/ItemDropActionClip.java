package mchorse.bbs_mod.actions.types.item;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.EntityLivingBase;

public class ItemDropActionClip extends ItemActionClip
{
    public final ValueDouble posX = new ValueDouble("x", 0D);
    public final ValueDouble posY = new ValueDouble("y", 0D);
    public final ValueDouble posZ = new ValueDouble("z", 0D);
    public final ValueFloat velocityX = new ValueFloat("vx", 0F);
    public final ValueFloat velocityY = new ValueFloat("vy", 0F);
    public final ValueFloat velocityZ = new ValueFloat("vz", 0F);
    public final ValueBoolean relative = new ValueBoolean("relative", false);

    public ItemDropActionClip()
    {
        super();

        this.add(this.posX);
        this.add(this.posY);
        this.add(this.posZ);
        this.add(this.velocityX);
        this.add(this.velocityY);
        this.add(this.velocityZ);
        this.add(this.relative);
    }

    public void shift(double dx, double dy, double dz)
    {
        this.posX.set(this.posX.get() + dx);
        this.posY.set(this.posY.get() + dy);
        this.posZ.set(this.posZ.get() + dz);
    }

    @Override
    public void applyAction(EntityLivingBase actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        this.applyPositionRotation(player, replay, tick);

        double x = this.relative.get() ? this.posX.get() + player.getPositionVector().x : this.posX.get();
        double y = this.relative.get() ? this.posY.get() + player.getPositionVector().y : this.posY.get();
        double z = this.relative.get() ? this.posZ.get() + player.getPositionVector().z : this.posZ.get();
        EntityItem entity = new EntityItem(
            player.getServerWorld(),
            x, y, z, this.itemStack.get().copy()
        );

        entity.motionX = this.velocityX.get();
        entity.motionY = this.velocityY.get();
        entity.motionZ = this.velocityZ.get();
        entity.setDefaultPickupDelay();
        player.world.spawnEntity(entity);
    }

    @Override
    protected Clip create()
    {
        return new ItemDropActionClip();
    }
}