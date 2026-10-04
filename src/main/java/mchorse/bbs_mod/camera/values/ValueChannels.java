package mchorse.bbs_mod.camera.values;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ValueChannels extends ValueGroup
{
    private final List<KeyframeChannel<Double>> doubleChannels = new ArrayList<>();
    private final List<KeyframeChannel<Color>> colorChannels = new ArrayList<>();

    public ValueChannels(String id)
    {
        super(id);
    }

    public KeyframeChannel<Double> addChannel(String id)
    {
        return this.addChannel(id, KeyframeFactories.DOUBLE);
    }

    public <T> KeyframeChannel<T> addChannel(String id, IKeyframeFactory<T> factory)
    {
        KeyframeChannel<T> channel = new KeyframeChannel<>(id, factory);

        this.preNotify();
        this.add(channel);
        this.postNotify();

        return channel;
    }

    public void removeChannel(KeyframeChannel<?> channel)
    {
        BaseValue baseValue = this.get(channel.getId());

        if (baseValue == channel)
        {
            this.preNotify();
            this.remove(baseValue);
            this.postNotify();
        }
    }

    public List<KeyframeChannel<Double>> getChannels()
    {
        return this.collectInto(KeyframeFactories.DOUBLE, this.doubleChannels);
    }

    public List<KeyframeChannel<Color>> getColorChannels()
    {
        return this.collectInto(KeyframeFactories.COLOR, this.colorChannels);
    }

    /**
     * All keyframe channels (any factory), sorted by id. For iteration that does not depend on value type.
     */
    public List<KeyframeChannel<?>> getAllKeyframeChannels()
    {
        List<KeyframeChannel<?>> out = new ArrayList<>();

        for (BaseValue baseValue : this.getAll())
        {
            if (baseValue instanceof KeyframeChannel)
            {
                KeyframeChannel<?> channel = (KeyframeChannel<?>) baseValue;
                out.add(channel);
            }
        }

        out.sort((a, b) -> a.getId().compareToIgnoreCase(b.getId()));

        return out;
    }

    private <T> List<KeyframeChannel<T>> collectInto(IKeyframeFactory<T> factory, List<KeyframeChannel<T>> buffer)
    {
        buffer.clear();

        for (BaseValue baseValue : this.getAll())
        {
            if (baseValue instanceof KeyframeChannel && ((KeyframeChannel<?>) baseValue).getFactory() == factory)
            {
                KeyframeChannel<?> channel = (KeyframeChannel<?>) baseValue;
                buffer.add((KeyframeChannel<T>) channel);
            }
        }

        buffer.sort((a, b) -> a.getId().compareToIgnoreCase(b.getId()));

        return buffer;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.removeAll();

        if (data.isMap())
        {
            MapType map = data.asMap();
            Set<String> keys = new HashSet<>(map.keys());

            for (String key : keys)
            {
                String newKey = key.replaceAll("/", ".");

                if (!newKey.equals(key))
                {
                    map.put(newKey, map.get(key));
                    map.remove(key);
                }

                BaseType entry = map.get(newKey);
                IKeyframeFactory<?> factory = KeyframeFactories.DOUBLE;

                if (entry != null && entry.isMap() && entry.asMap().has("type"))
                {
                    IKeyframeFactory<?> f = KeyframeFactories.FACTORIES.get(entry.asMap().getString("type"));

                    if (f != null)
                    {
                        factory = f;
                    }
                }

                this.add(new KeyframeChannel<>(newKey, factory));
            }
        }

        super.fromData(data);
    }
}
