package mchorse.bbs_mod.settings.values.core;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.ui.utils.icons.Icon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ValueGroup extends BaseValueGroup
{
    private Map<String, BaseValue> children = new LinkedHashMap<>();

    /**
     * Namespaced keys read out of the data that no child of this group claimed, kept so they
     * can be written back untouched.
     *
     * <p>They belong to an addon that is not loaded right now. Dropping them would mean that
     * starting the game once without the addon quietly rewrites every scene that used it — so
     * the round trip preserves them instead.</p>
     *
     * <p>Only keys carrying a namespace ({@code myaddon:something}) are kept. BBS's own
     * properties never do, which is what lets a removed property still disappear on save
     * instead of trailing after the data forever.</p>
     */
    private Map<String, BaseType> foreign;

    public Icon icon;

    public ValueGroup(String id)
    {
        super(id);
    }

    public void removeAll()
    {
        this.children.clear();
    }

    public void add(BaseValue value)
    {
        if (value != null)
        {
            this.children.put(value.getId(), value);
            value.setParent(this);
        }
    }

    public void remove(BaseValue child)
    {
        BaseValue baseValue = this.children.get(child.getId());

        if (baseValue == child)
        {
            this.children.remove(child.getId());
        }
    }

    @Override
    public List<BaseValue> getAll()
    {
        return new ArrayList<>(this.children.values());
    }

    /** One child by id, if it is a basic value — the per-lookup shape of {@link #getAllMap()}. */
    public BaseValueBasic getBasic(String id)
    {
        BaseValue child = this.children.get(id);
        return child instanceof BaseValueBasic ? (BaseValueBasic) child : null;
    }

    public Map<String, BaseValueBasic> getAllMap()
    {
        Map<String, BaseValueBasic> map = new HashMap<>();

        for (BaseValue value : this.children.values())
        {
            if (value instanceof BaseValueBasic)
            {
                BaseValueBasic<?> basic = (BaseValueBasic<?>) value;
                map.put(basic.getId(), basic);
            }
        }

        return map;
    }

    @Override
    public BaseValue get(String key)
    {
        return this.children.get(key);
    }

    @Override
    public void copy(BaseValueGroup group)
    {
        for (BaseValue groupValue : group.getAll())
        {
            BaseValue value = this.children.get(groupValue.getId());

            if (value != null)
            {
                value.copy(groupValue);
            }
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean equals = super.equals(obj);

        if (equals)
        {
            return equals;
        }

        if (obj instanceof ValueGroup)
        {
            ValueGroup group = (ValueGroup) obj;
            return this.children.equals(group.children);
        }

        return false;
    }

    @Override
    public BaseType toData()
    {
        return this.toData(this::serializeChild);
    }

    protected BaseType serializeChild(BaseValue value)
    {
        return value.toData();
    }

    /** Serialize selected child representations while retaining unknown fields. Null omits a child. */
    public BaseType toData(java.util.function.Function<BaseValue, BaseType> serialize)
    {
        MapType data = new MapType();

        for (BaseValue value : this.children.values())
        {
            if (this.canPersist(value))
            {
                BaseType child = serialize.apply(value);
                if (child != null) data.put(value.getId(), child);
            }
        }

        if (this.foreign != null)
        {
            for (Map.Entry<String, BaseType> entry : this.foreign.entrySet())
            {
                if (!data.has(entry.getKey()))
                {
                    data.put(entry.getKey(), entry.getValue().copy());
                }
            }
        }

        return data;
    }

    protected boolean canPersist(BaseValue value)
    {
        return true;
    }

    /** Override only in groups whose saved property names have registered migrations. */
    protected String resolveReadKey(String key)
    {
        return key;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (!data.isMap())
        {
            return;
        }

        this.foreign = null;

        for (Map.Entry<String, BaseType> entry : data.asMap())
        {
            String key = this.resolveReadKey(entry.getKey());
            if (!key.equals(entry.getKey()) && data.asMap().has(key)) continue;
            BaseValue value = this.children.get(key);

            if (value != null)
            {
                value.setParent(this);
                value.fromData(entry.getValue());
            }
            else if (key.indexOf(':') >= 0)
            {
                if (this.foreign == null)
                {
                    this.foreign = new LinkedHashMap<>();
                }

                this.foreign.put(key, entry.getValue().copy());
            }
        }
    }
}
