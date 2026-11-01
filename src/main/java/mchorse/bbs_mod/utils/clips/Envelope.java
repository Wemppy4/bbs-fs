package mchorse.bbs_mod.utils.clips;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class Envelope extends ValueGroup
{
    /**
     * Whether the envelope does anything: not a switch of its own any more, but true while there
     * is a fade on either side or the keys shape it — see {@link #updateEnabled()}. It is still
     * saved, so older builds read the clip the same way.
     */
    public final ValueBoolean enabled = new ValueBoolean("enabled");

    public final ValueFloat fadeIn = new ValueFloat("fadeIn", 0F);
    public final ValueFloat fadeOut = new ValueFloat("fadeOut", 0F);

    public final Interpolation pre = new Interpolation("pre", Interpolations.MAP);
    public final Interpolation post = new Interpolation("post", Interpolations.MAP);

    public final ValueBoolean keyframes = new ValueBoolean("keyframes");
    public final KeyframeChannel<Double> channel = new KeyframeChannel<>("channel", KeyframeFactories.DOUBLE);

    public Envelope(String id)
    {
        super(id);

        this.add(this.enabled);
        this.add(this.fadeIn);
        this.add(this.fadeOut);
        this.add(this.pre);
        this.add(this.post);
        this.add(this.keyframes);
        this.add(this.channel);

        this.channel.insert(0, 0D);
        this.channel.insert(BBSSettings.getDefaultDuration(), 1D);
    }

    /** Call after changing the fades or the keys switch, so {@link #enabled} follows them. */
    public void updateEnabled()
    {
        this.enabled.set(this.keyframes.get() || this.fadeIn.get() > 0F || this.fadeOut.get() > 0F);
    }

    public float factorEnabled(int duration, float tick)
    {
        if (!this.enabled.get())
        {
            return 1;
        }

        return this.factor(duration, tick);
    }

    public float factor(int duration, float tick)
    {
        float envelope = 0;

        if (this.keyframes.get())
        {
            if (this.channel.hasEnabledKeyframes())
            {
                envelope = this.channel.interpolate(tick).floatValue();
            }
        }
        else
        {
            float lowOut = this.fadeIn.get();

            envelope = Lerps.envelope(tick, 0, lowOut, duration - this.fadeOut.get(), duration);
            envelope = (float) (tick <= lowOut ? this.pre : this.post).interpolate(IInterp.context.set(0, 1, envelope));
        }

        return envelope;
    }

    public void breakDown(Clip original, int offset)
    {
        this.fadeIn.set(0F);
        original.envelope.fadeOut.set(0F);
        this.updateEnabled();
        original.envelope.updateEnabled();

        this.channel.moveX(-offset);
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        if (data.isMap())
        {
            MapType map = data.asMap();

            if (map.has("interpolation"))
            {
                BaseType interpolation = map.get("interpolation");

                this.pre.fromData(interpolation);
                this.post.fromData(interpolation);
            }

            /* The old "start only"/"end only" modes are now just a zero fade on the other side */
            int mode = map.getInt("mode");

            if (mode == 1) this.fadeOut.set(0F);
            else if (mode == 2) this.fadeIn.set(0F);
        }

        /* A switched off envelope used to keep its fades and keys switch, doing nothing with them:
         * now that the fades themselves say whether there is one, they are zero there */
        if (!this.enabled.get())
        {
            this.fadeIn.set(0F);
            this.fadeOut.set(0F);
            this.keyframes.set(false);
        }

        this.updateEnabled();
    }
}