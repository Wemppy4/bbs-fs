package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.undo.UndoManager;

import java.util.function.Supplier;

/**
 * A visual list of an {@link UndoManager}'s stack — clicking an entry undoes/redoes until the manager's
 * position reaches it. Panel-agnostic: it's handed the manager, the context supplier that undo/redo needs,
 * and an optional callback run after driving to the picked index. Editors whose widgets live-track the data
 * (the film timeline) pass {@code null}; ones with static widgets (the model editor) pass their rebuild.
 */
public class UIUndoHistoryOverlay extends UIOverlayPanel
{
    private UIUndoList<ValueGroup> list;

    public UIUndoHistoryOverlay(IKey title, UndoManager<ValueGroup> undoManager, Supplier<ValueGroup> context, Runnable onApplied)
    {
        super(title);

        this.list = new UIUndoList((l) ->
        {
            int index = this.list.getIndex();

            while (undoManager.getCurrentUndoIndex() != index)
            {
                if (undoManager.getCurrentUndoIndex() > index)
                {
                    undoManager.undo(context.get());
                }
                else
                {
                    undoManager.redo(context.get());
                }
            }

            if (onApplied != null)
            {
                onApplied.run();
            }

            UIUtils.playClick();
        });
        this.list.setList(undoManager.getUndos());
        this.list.full(this.content);
        this.list.setIndex(undoManager.getCurrentUndoIndex());

        this.content.add(this.list);
    }
}
