package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.bones.UIBoneTreeList;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The model editor's bone tree: a click picks a bone (its settings show under the tree), a hidden
 * bone (one in the config's {@code disabledBones}) reads gray, and the row's context menu hides or
 * shows the bone with its whole branch, or shows everything again.
 */
public class UIModelBoneList extends UIBoneTreeList
{
    private final Supplier<ValueStringKeys> hidden;
    private final Runnable onChange;

    private IModel model;

    public UIModelBoneList(Consumer<List<String>> callback, Supplier<ValueStringKeys> hidden, Runnable onChange)
    {
        super(callback);

        this.hidden = hidden;
        this.onChange = onChange;

        this.background();
        this.disabled(this::isHidden);
        this.context((menu) ->
        {
            int index = this.getIndexAtCursor(this.getContext());
            String bone = index < 0 ? null : this.getList().get(index);

            if (bone != null && this.model != null)
            {
                boolean shown = !this.isHidden(bone);

                menu.action(shown ? Icons.INVISIBLE : Icons.VISIBLE, shown ? UIKeys.MODEL_EDITOR_BONES_HIDE_BRANCH : UIKeys.MODEL_EDITOR_BONES_SHOW_BRANCH, () -> this.setBranch(bone, !shown));
            }

            ValueStringKeys value = this.hidden.get();

            if (value != null && !value.get().isEmpty())
            {
                menu.action(Icons.VISIBLE, UIKeys.MODEL_EDITOR_BONES_SHOW_ALL, () -> this.set(new ArrayList<>(value.get()), true));
            }
        });
    }

    /** Show a model's bones (null empties the list). */
    public void fill(IModel model)
    {
        this.model = model;

        this.fillBones(model, null);
    }

    private boolean isHidden(String bone)
    {
        ValueStringKeys value = this.hidden.get();

        return value != null && value.get().contains(bone);
    }

    private void setBranch(String bone, boolean shown)
    {
        List<String> bones = new ArrayList<>();

        bones.add(bone);
        bones.addAll(this.model.getAllChildrenKeys(bone));

        this.set(bones, shown);
    }

    /** The set is mutated in place, so the edit is bracketed in a notify for undo to catch. */
    private void set(Collection<String> bones, boolean shown)
    {
        ValueStringKeys value = this.hidden.get();

        if (value == null)
        {
            return;
        }

        BaseValue.edit(value, (v) ->
        {
            if (shown)
            {
                value.get().removeAll(bones);
            }
            else
            {
                value.get().addAll(bones);
            }
        });

        if (this.onChange != null)
        {
            this.onChange.run();
        }
    }
}
