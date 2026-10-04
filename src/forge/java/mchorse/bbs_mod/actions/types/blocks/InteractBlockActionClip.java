package mchorse.bbs_mod.actions.types.blocks;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.values.ValueBlockHitResult;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;

public class InteractBlockActionClip extends ActionClip
{
    public final ValueBlockHitResult hit = new ValueBlockHitResult("hit");
    public final ValueBoolean hand = new ValueBoolean("hand", true);

    public InteractBlockActionClip()
    {
        super();

        this.add(this.hit);
        this.add(this.hand);
    }

    @Override
    public void shift(double dx, double dy, double dz)
    {
        super.shift(dx, dy, dz);

        this.hit.shift(dx, dy, dz);
    }

    @Override
    public void applyAction(EntityLivingBase actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        this.applyPositionRotation(player, replay, tick);

        RayTraceResult result = this.hit.getHitResult();

        net.minecraft.util.math.BlockPos pos = result.getBlockPos();
        net.minecraft.block.state.IBlockState state = player.world.getBlockState(pos);
        state.getBlock().onBlockActivated(player.world, pos, state, player, this.hand.get() ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND,
            result.sideHit, (float) (result.hitVec.x-pos.getX()), (float) (result.hitVec.y-pos.getY()), (float) (result.hitVec.z-pos.getZ()));
    }

    /**
     * A container the interaction opened stays open for as long as the clip is
     * long (LUCKYWAY) - the recorder grew it for exactly as long as the player
     * kept the screen up. A clip of a single tick is a tap, a button or a door,
     * and holds nothing open.
     */
    @Override
    public void applyRange(EntityLivingBase actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        if (this.duration.get() > 1)
        {
            player.wantLidOpen(this.hit.getBlockPos());
        }
    }

    @Override
    protected Clip create()
    {
        return new InteractBlockActionClip();
    }
}