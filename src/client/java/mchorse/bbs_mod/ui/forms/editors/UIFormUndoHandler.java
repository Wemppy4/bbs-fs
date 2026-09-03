package mchorse.bbs_mod.ui.forms.editors;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.undo.CompoundUndo;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UIFormUndoHandler
{
    /**
     * How long a recording ({@link IValueListener#FLAG_BATCH}) may go without an edit before the
     * history seals it. Auto-keyframing writes on every tick of the film, so anything above a
     * fraction of a second keeps a whole take in one entry, and a deliberate pause starts a new one.
     */
    private static final long BATCH_TIMEOUT = 500;

    protected UndoManager<ValueGroup> undoManager;

    protected Map<BaseValue, BaseType> cachedValues = new HashMap<>();
    protected boolean cacheMarkLastUndoNoMerging;
    protected MapType uiData;

    protected Timer undoTimer = new Timer(1000);

    /** A recording is being collected: edits keep piling into one entry instead of pushing their own. */
    protected boolean batching;
    protected Timer batchTimer = new Timer(BATCH_TIMEOUT);

    protected UIElement uiElement;

    /**
     * Remove any child tree entries if one of the parents is present already.
     * For example, let's say the undo submitted at the same time:
     *
     * - film.clips
     * - film.clips.0
     * - film.clips.0.duration
     *
     * There is no point in caching .0 and .0.duration since films .clips will get
     * cached anyway. Therefore, it's smart to eliminate those from the cache, and
     * submit only films.clips.
     */
    public static void reduceUndoRedundancy(Map<BaseValue, BaseType> cachedValues)
    {
        Iterator<BaseValue> it = cachedValues.keySet().iterator();

        while (it.hasNext())
        {
            BaseValue value = it.next().getParent();
            boolean remove = false;

            while (value != null)
            {
                if (cachedValues.containsKey(value))
                {
                    remove = true;

                    break;
                }

                value = value.getParent();
            }

            if (remove)
            {
                it.remove();
            }
        }
    }

    public UIFormUndoHandler(UIElement uiElement)
    {
        this.uiElement = uiElement;

        this.reset();
    }

    public UndoManager<ValueGroup> getUndoManager()
    {
        return this.undoManager;
    }

    public void reset()
    {
        this.undoManager = new UndoManager<>(100);
        this.undoManager.setCallback(this::handleUndos);

        this.batching = false;
        this.batchTimer.reset();
    }

    /**
     * Handle undo/redo. This method primarily updates the UI state, according to
     * the undo/redo changes were done.
     */
    private void handleUndos(IUndo<ValueGroup> undo, boolean redo)
    {
        IUndo<ValueGroup> anotherUndo = undo;

        if (anotherUndo instanceof CompoundUndo)
        {
            anotherUndo = ((CompoundUndo<ValueGroup>) anotherUndo).getFirst(ValueChangeUndo.class);
        }

        if (anotherUndo instanceof ValueChangeUndo)
        {
            ValueChangeUndo change = (ValueChangeUndo) anotherUndo;

            this.uiElement.getRoot().applyAllUndoData(change.getUIData(redo));
        }
    }

    public void handlePreValues(BaseValue baseValue, int flag)
    {
        if ((flag & IValueListener.FLAG_BATCH) != 0)
        {
            this.batching = true;
            this.batchTimer.mark();
        }

        if (this.batching)
        {
            baseValue = promoteToChannel(baseValue);
        }

        if (this.uiData == null && this.uiElement.getRoot() != null)
        {
            this.uiData = this.uiElement.getRoot().collectAllUndoData();
        }

        if (!this.cachedValues.containsKey(baseValue))
        {
            this.cachedValues.put(baseValue, baseValue.toData());
        }

        if ((flag & IValueListener.FLAG_UNMERGEABLE) != 0)
        {
            this.cacheMarkLastUndoNoMerging = true;
        }
    }

    /**
     * While a recording is collected into one entry, an edit inside a keyframe channel is cached
     * as the whole channel.
     *
     * <p>An entry keeps one before-state per value, taken the first time that value is touched.
     * Caching keyframes one by one would take theirs from an already recorded state — the channel
     * itself is only touched when a keyframe is born, which happens after the first keyframe of the
     * take was already written into — and undoing a keyframe by index after its channel was rolled
     * back would land on a different keyframe entirely.</p>
     */
    private static BaseValue promoteToChannel(BaseValue value)
    {
        BaseValue parent = value;

        while (parent != null)
        {
            if (parent instanceof KeyframeChannel)
            {
                return parent;
            }

            parent = parent.getParent();
        }

        return value;
    }

    public void submitUndo()
    {
        this.submitUndo(false);
    }

    /**
     * @param force seal what is collected right now even mid-recording — the history is about to
     *              be walked or thrown away, so it has to hold everything done so far.
     */
    public void submitUndo(boolean force)
    {
        this.handleTimers();

        boolean batched = this.batching;

        if (this.batching)
        {
            if (!force && !this.batchTimer.checkReset())
            {
                return;
            }

            this.batching = false;
            this.batchTimer.reset();
        }

        if (this.cachedValues.isEmpty())
        {
            return;
        }

        reduceUndoRedundancy(this.cachedValues);

        List<ValueChangeUndo> changeUndos = new ArrayList<>();

        for (Map.Entry<BaseValue, BaseType> entry : this.cachedValues.entrySet())
        {
            BaseValue value = entry.getKey();
            BaseType after = value.toData();

            /* A recording claims the track the moment an edit is aimed at it, before anyone knows
             * whether the edit moves anything — a click that goes nowhere must not leave an entry
             * that eats a Ctrl+Z without undoing anything. */
            if (batched && entry.getValue().equals(after))
            {
                continue;
            }

            ValueChangeUndo undo = new ValueChangeUndo(value.getPath(), entry.getValue(), after);

            undo.cacheAfter(this.uiElement);
            undo.cacheBefore(this.uiData);
            changeUndos.add(undo);

            this.handleValue(value);
        }

        if (changeUndos.size() == 1)
        {
            this.undoManager.pushUndo(changeUndos.get(0));
        }
        else if (!changeUndos.isEmpty())
        {
            this.undoManager.pushUndo(new CompoundUndo<>(changeUndos.toArray(new IUndo[0])));
        }

        this.cachedValues.clear();
        this.uiData = null;

        this.undoTimer.mark();

        if (this.cacheMarkLastUndoNoMerging)
        {
            this.cacheMarkLastUndoNoMerging = false;

            this.undoManager.markLastUndoNoMerging();
        }
    }

    protected void handleValue(BaseValue value)
    {}

    protected void handleTimers()
    {
        if (this.undoTimer.checkReset())
        {
            this.undoManager.markLastUndoNoMerging();
        }
    }
}