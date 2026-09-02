package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * Keyframe segment.
 *
 * This structure contains all the necessary data to create an interpolated
 * value.
 */
public class KeyframeSegment <T>
{
    public Keyframe<T> a;
    public Keyframe<T> b;

    public Keyframe<T> preA;
    public Keyframe<T> postB;
    public float duration;
    public float offset;
    public float x;

    public KeyframeSegment()
    {}

    public KeyframeSegment(Keyframe<T> a, Keyframe<T> b)
    {
        this.fill(a, b);
    }

    public KeyframeSegment(Keyframe<T> a, Keyframe<T> b, int index)
    {
        this.fill(a, b, index);
    }

    public void setup(Keyframe<T> a, Keyframe<T> b, float ticks)
    {
        this.fill(a, b);
        this.setup(ticks);
    }

    public void fill(Keyframe<T> a, Keyframe<T> b)
    {
        KeyframeChannel<T> channel = (KeyframeChannel<T>) a.getParent();

        this.fill(a, b, channel.indexOf(a));
    }

    /**
     * Fill with the index of {@code a} already in hand. The binary search that finds a segment
     * knows this index; taking it here keeps the neighbour lookup O(1) instead of re-scanning
     * the channel for a keyframe the caller just pulled out of it.
     */
    public void fill(Keyframe<T> a, Keyframe<T> b, int index)
    {
        this.a = a;
        this.b = b;

        KeyframeChannel<T> channel = (KeyframeChannel<T>) a.getParent();

        if (index >= 0)
        {
            Keyframe<T> preA = channel.get(index - 1);
            Keyframe<T> postB = channel.get(index + 2);

            this.preA = preA == null ? a : preA;
            this.postB = postB == null ? b : postB;
        }
        else
        {
            this.preA = a;
            this.postB = b;
        }
    }

    public void setup(float ticks)
    {
        float forcedDuration = this.a.getDuration();

        this.duration = forcedDuration > 0 ? forcedDuration : this.b.getTick() - this.a.getTick();
        this.offset = ticks - this.a.getTick();
        this.x = MathUtils.clamp(this.duration == 0 ? 0F : this.offset / this.duration, 0F, 1F);
    }

    public T createInterpolated()
    {
        IKeyframeFactory<T> factory = this.a.getFactory();

        if (this.isSame())
        {
            return factory.copy(this.a.getValue());
        }

        double duration = IInterp.context.duration;
        double startTick = IInterp.context.startTick;

        IInterp.context.segment(this.duration, this.a.getTick());

        try
        {
            return factory.copy(factory.interpolate(this.preA, this.a, this.b, this.postB, this.a.getInterpolation(), this.x));
        }
        finally
        {
            IInterp.context.segment(duration, startTick);
        }
    }

    public boolean isSame()
    {
        return this.a == this.b;
    }

    public Keyframe<T> getClosest()
    {
        return this.x > 0.5F ? this.b : this.a;
    }
}