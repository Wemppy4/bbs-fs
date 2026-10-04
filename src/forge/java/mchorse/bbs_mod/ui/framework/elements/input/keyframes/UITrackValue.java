package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A value editor's binding. Reading never creates a key or exposes stored mutable data. */
public class UITrackValue<T>
{
    public final UIKeyframeSheet sheet;
    public final UIKeyframes editor;

    public UITrackValue(UIKeyframeSheet sheet, UIKeyframes editor)
    {
        this.sheet = sheet;
        this.editor = editor;
    }

    public IKeyframeFactory<T> getFactory()
    {
        return this.sheet.channel.getFactory();
    }

    public T getValue()
    {
        return (T) this.sheet.sample(this.editor.getTick());
    }

    private List<UIKeyframeSheet> targets()
    {
        List<UIKeyframeSheet> targets = new ArrayList<>();

        for (UIKeyframeSheet candidate : this.editor.getOperationSheets())
        {
            if (candidate.channel.getFactory() == this.getFactory() && candidate.selection.hasAny())
            {
                targets.add(candidate);
            }
        }

        if (targets.isEmpty() || this.editor.getAutoKeyframeTick() != null && !targets.contains(this.sheet))
        {
            targets.add(this.sheet);
        }

        return targets;
    }

    public void edit(Consumer<T> edit)
    {
        this.write(edit, null, null);
    }

    public void setValue(T value)
    {
        this.setValue(value, this.getValue());
    }

    /** Numeric widgets supply their displayed starting value for the existing relative edit. */
    public void setValue(T value, T before)
    {
        this.write(null, value, before);
    }

    private void write(Consumer<T> edit, T value, T before)
    {
        List<UIKeyframeSheet> targets = this.targets();
        boolean cursor = this.editor.getAutoKeyframeTick() != null
            || targets.stream().noneMatch(target -> target.selection.hasAny());
        boolean stopped = this.editor.stopPlaybackOnValueChange();

        for (UIKeyframeSheet target : targets)
        {
            this.editor.applyValueChange(target, () ->
            {
                List<Keyframe<T>> keys = cursor ? java.util.Arrays.asList(target.ensureKeyframe(this.editor.getTick()))
                    : (List) target.selection.getSelected();

                for (Keyframe<T> key : keys)
                {
                    if (stopped)
                    {
                        target.selection.clear();
                        target.selection.add(key);
                    }

                    if (edit != null) edit.accept(key.getValue());
                    else if (cursor && target == this.sheet) key.setValue(this.getFactory().copy(value), false);
                    else target.setValueOn(key, value, before, false);
                }
            });
        }

        this.editor.triggerChange();
    }
}
