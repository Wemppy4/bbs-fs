package mchorse.bbs_mod.settings.values.base;

import mchorse.bbs_mod.data.IDataSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.IValueNotifier;
import mchorse.bbs_mod.utils.DataPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public abstract class BaseValue implements IDataSerializable<BaseType>, IValueNotifier
{
    protected String id;
    protected BaseValue parent;

    private boolean visible = true;
    private boolean synced;
    private List<IValueListener> preCallbacks;
    private List<IValueListener> postCallbacks;

    public static <T extends BaseValue> void edit(T value, Consumer<T> callback)
    {
        edit(value, 0, callback);
    }

    public static <T extends BaseValue> void edit(T value, int flag, Consumer<T> callback)
    {
        if (callback == null)
        {
            return;
        }

        value.preNotify(flag);
        callback.accept(value);
        value.postNotify(flag);
    }

    public BaseValue(String id)
    {
        this.setId(id);
    }

    /**
     * Don't use it without a reason!
     */
    public void setId(String id)
    {
        this.id = id;
    }

    public BaseValue invisible()
    {
        this.visible = false;

        return this;
    }

    /**
     * Declare that edits to this value (and everything under it) must reach the server's copy
     * of the data. The declaration lives with the value, next to where it is defined — the code
     * that ships edits asks {@link #isSynced()} instead of pattern-matching path strings, which
     * is how whole channels used to silently miss the server when the list fell behind the data.
     */
    public BaseValue synced()
    {
        this.synced = true;

        return this;
    }

    /** Whether this value is inside a subtree declared {@link #synced()}. */
    public boolean isSynced()
    {
        BaseValue value = this;

        while (value != null)
        {
            if (value.synced)
            {
                return true;
            }

            value = value.getParent();
        }

        return false;
    }

    public BaseValue preCallback(IValueListener callback)
    {
        if (this.preCallbacks == null)
        {
            this.preCallbacks = new ArrayList<>();
        }

        this.preCallbacks.add(callback);

        return this;
    }

    public BaseValue postCallback(IValueListener callback)
    {
        if (this.postCallbacks == null)
        {
            this.postCallbacks = new ArrayList<>();
        }

        this.postCallbacks.add(callback);

        return this;
    }

    public boolean isVisible()
    {
        boolean visible = true;
        BaseValue value = this;

        while (value != null)
        {
            visible = visible && value.visible;
            value = value.getParent();
        }

        return visible;
    }

    /**
     * Put this value back to what it was born with — the default its
     * constructor declared, not what the last loaded file happened to hold.
     *
     * <p>Basic values restore their captured default, groups pass the request
     * down to their children. The write goes through the usual notification
     * pair, so whatever listens to this value — undo among them — sees an
     * ordinary edit.</p>
     */
    public void reset()
    {}

    /**
     * Whether this value still holds its declared default, so the interface
     * can tell an untouched property from an edited one.
     */
    public boolean isDefault()
    {
        return true;
    }

    public BaseValue getRoot()
    {
        BaseValue value = this;

        while (true)
        {
            if (value.getParent() == null)
            {
                return value;
            }

            value = value.getParent();
        }
    }

    public void setParent(BaseValue parent)
    {
        this.parent = parent;
    }

    public String getId()
    {
        return this.id;
    }

    public void resetCallbacks()
    {
        this.preCallbacks = this.postCallbacks = null;
    }

    @Override
    public void preNotify(int flag)
    {
        this.preNotify(this, flag);
    }

    @Override
    public void preNotify(BaseValue value, int flag)
    {
        IValueNotifier.super.preNotify(value, flag);

        if (this.preCallbacks != null)
        {
            for (IValueListener callback : this.preCallbacks)
            {
                callback.accept(value, flag);
            }
        }
    }

    @Override
    public void postNotify(int flag)
    {
        this.postNotify(this, flag);
    }

    @Override
    public void postNotify(BaseValue value, int flag)
    {
        IValueNotifier.super.postNotify(value, flag);

        if (this.postCallbacks != null)
        {
            for (IValueListener callback : this.postCallbacks)
            {
                callback.accept(value, flag);
            }
        }
    }

    @Override
    public BaseValue getParent()
    {
        return this.parent;
    }

    public List<String> getPathSegments()
    {
        List<String> strings = new ArrayList<>();
        BaseValue value = this;

        while (value != null)
        {
            String id = value.getId();

            if (!id.isEmpty())
            {
                strings.add(id);
            }

            value = value.getParent();
        }

        Collections.reverse(strings);

        return strings;
    }

    public DataPath getPath()
    {
        List<String> segments = this.getPathSegments();
        DataPath path = new DataPath(false);

        path.strings.addAll(segments);

        return path;
    }

    public DataPath getRelativePath(BaseValue ancestor)
    {
        DataPath strings = new DataPath(false);
        BaseValue value = this;

        while (value != null)
        {
            String id = value.getId();

            if (!id.isEmpty())
            {
                strings.strings.add(id);
            }

            value = value.getParent();

            if (value == ancestor)
            {
                strings.strings.add(value.getId());

                Collections.reverse(strings.strings);

                return strings;
            }
        }

        return null;
    }

    public void copy(BaseValue value)
    {
        this.copy(value, IValueListener.FLAG_DEFAULT);
    }

    public void copy(BaseValue value, int flag)
    {
        this.preNotify(flag);

        if (value != null)
        {
            this.fromData(value.toData());
        }

        this.postNotify(flag);
    }
}