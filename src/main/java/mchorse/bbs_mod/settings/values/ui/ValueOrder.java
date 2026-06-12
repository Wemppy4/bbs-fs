package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An ordering of a fixed token set: the value is always a permutation of the
 * tokens given to the constructor, which also define the default order.
 * Deserialization keeps known tokens in their saved order, appends missing
 * ones in default order and drops unknown ones, so the permutation invariant
 * survives config edits and older files.
 *
 * <p>Like {@link mchorse.bbs_mod.settings.values.numeric.ValueInt#modes(IKey...)},
 * per-token display labels (and optional tint colors) are attached to the
 * value itself so the settings UI can render the tokens without knowing what
 * they mean.</p>
 */
public class ValueOrder extends BaseValueBasic<List<String>>
{
    private final List<String> tokens;
    private List<IKey> labels;
    private int[] colors;

    public ValueOrder(String id, String... tokens)
    {
        super(id, new ArrayList<>(Arrays.asList(tokens)));

        this.tokens = Collections.unmodifiableList(Arrays.asList(tokens));
    }

    public List<String> getTokens()
    {
        return this.tokens;
    }

    /** Display labels, parallel to the constructor's token order. */
    public ValueOrder labels(IKey... labels)
    {
        this.labels = Arrays.asList(labels);

        return this;
    }

    /** Display tint colors, parallel to the constructor's token order ({@code 0} = default). */
    public ValueOrder colors(int... colors)
    {
        this.colors = colors;

        return this;
    }

    public IKey getLabel(String token)
    {
        int index = this.tokens.indexOf(token);

        return this.labels != null && index >= 0 && index < this.labels.size() ? this.labels.get(index) : IKey.constant(token);
    }

    public int getColor(String token)
    {
        int index = this.tokens.indexOf(token);

        return this.colors != null && index >= 0 && index < this.colors.length ? this.colors[index] : 0;
    }

    public void reset()
    {
        this.set(new ArrayList<>(this.tokens));
    }

    @Override
    public BaseType toData()
    {
        ListType list = new ListType();

        for (String token : this.value)
        {
            list.addString(token);
        }

        return list;
    }

    @Override
    public void fromData(BaseType data)
    {
        List<String> order = new ArrayList<>();

        if (data.isList())
        {
            for (BaseType type : data.asList())
            {
                if (type.isString() && this.tokens.contains(type.asString()) && !order.contains(type.asString()))
                {
                    order.add(type.asString());
                }
            }
        }

        for (String token : this.tokens)
        {
            if (!order.contains(token))
            {
                order.add(token);
            }
        }

        this.value = order;
    }
}
