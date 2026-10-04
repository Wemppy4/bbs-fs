package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumHand;

public class SwipeActionClip extends ActionClip
{
    @Override
    public boolean isClient()
    {
        return true;
    }

    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        entity.swingArm();
    }

    @Override
    public void applyAction(EntityLivingBase actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        super.applyAction(actor, player, film, replay, tick);

        if (actor != null)
        {
            actor.swingArm(EnumHand.MAIN_HAND);
        }
    }

    @Override
    protected Clip create()
    {
        return new SwipeActionClip();
    }
}