package mchorse.bbs_mod.settings.values.base;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

public class BaseKeyframeFactoryValue<T> extends BaseValueBasic<T>
{
    private final IKeyframeFactory<T> factory;
    private boolean animatable = true;

    /** Explicit animation capability, independent of whether an ordinary field is visible. */
    public BaseKeyframeFactoryValue<T> animatable(boolean enabled)
    {
        this.animatable = enabled;
        return this;
    }

    public boolean isAnimatable()
    {
        return this.animatable && !this.isBound();
    }

    public BaseKeyframeFactoryValue(String id, IKeyframeFactory<T> factory, T value)
    {
        super(id, value);

        this.factory = factory;

        /* The default taken in super() couldn't be copied — the factory that
         * knows how wasn't assigned yet — so take it again now that it is. */
        this.captureDefault();
    }

    public IKeyframeFactory<T> getFactory()
    {
        return this.factory;
    }

    @Override
    protected T copyValue(T value)
    {
        return this.factory == null || value == null ? value : this.factory.copy(value);
    }

    @Override
    protected boolean compareValue(T a, T b)
    {
        return this.factory == null ? super.compareValue(a, b) : this.factory.compare(a, b);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof BaseKeyframeFactoryValue && ((BaseKeyframeFactoryValue<?>) obj).factory == this.factory)
        {
            BaseKeyframeFactoryValue<T> property = (BaseKeyframeFactoryValue<T>) obj;
            return this.factory.compare(this.getOriginalValue(), property.getOriginalValue());
        }

        return super.equals(obj);
    }

    @Override
    public BaseType toData()
    {
        return this.factory.toData(this.getOriginalValue());
    }

    @Override
    public void fromData(BaseType data)
    {
        this.setOriginalValue(this.factory.fromData(data));
    }
}
