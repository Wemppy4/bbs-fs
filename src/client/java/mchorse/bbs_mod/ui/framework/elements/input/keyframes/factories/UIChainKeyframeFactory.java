package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.chains.ChainControl;
import mchorse.bbs_mod.cubic.chains.ChainControls;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.utils.UIAnchorBinding;

/**
 * Editor for a solver track: pick a chain by the bone that names it and edit that
 * chain's state. The owning form supplies topology and defaults; the key stores animated state.
 */
public abstract class UIChainKeyframeFactory <C extends ChainControl<C>, S extends ChainControls<C, S>> extends UIKeyframeFactory<S>
{
    public UIStringList chains;

    /** Every scalar field, so they can all be greyed out together while no chain is selected. */
    private final List<UIElement> inputs = new ArrayList<>();

    protected ModelForm form;
    protected String selected = "";

    private boolean syncing;

    public UIChainKeyframeFactory(UITrackValue<S> track, UIKeyframes editor)
    {
        super(track, editor);

        UIKeyframeSheet sheet = track.sheet;

        if (sheet != null && sheet.form instanceof ModelForm modelForm)
        {
            this.form = modelForm;
        }

        this.chains = new UIStringList((l) ->
        {
            this.selected = l.isEmpty() ? "" : l.get(0);
            this.display();
        });
        /* Six chains is the minimum; the list grows into whatever the fields below it leave in
         * the properties strip, which is as tall as the timeline. */
        this.chains.background().h(UIConstants.LIST_ITEM_HEIGHT * 6).expand();
    }

    /** Register a scalar field as this editor is built, so it follows the selection's enabled state. */
    protected <T extends UIElement> T input(T element)
    {
        this.inputs.add(element);

        return element;
    }

    /** Called by the subclass once its fields exist: fills the chain list and lays the column out. */
    protected void setup(UIElement... rows)
    {
        this.fillChains();

        /* The column is marked too: the strip's spare height only reaches the list through the
         * layers that asked for it. */
        UIElement column = UI.column(this.chains);

        column.add(rows);
        this.scroll.add(column.expand());

        this.display();
    }

    protected UIElement binding(String id, IKey label, IKey tooltip)
    {
        /* External targets belong to film channels, not form animation states. */
        if (!(this.editor instanceof UIFilmKeyframes))
        {
            return null;
        }

        return this.input(UIAnchorBinding.section(label, tooltip,
            () -> this.displayControl(this.selected).binding(id),
            edit -> this.edit(control ->
            {
                var anchor = control.binding(id).copy();
                edit.accept(anchor);
                control.bindings.put(id, anchor);
            })));
    }

    /** Whether this bone names a chain of the kind this editor keyframes. */
    protected abstract boolean hasChain(FormBone bone);

    /** Push a chain's scalars into the fields. Guarded against writing them back out. */
    protected abstract void sync(C control);

    /** The chain's static scalars on the form, which an untouched chain shows and a fresh edit seeds from. */
    protected abstract C configControl(String bone);

    /** The bones that name a chain of this kind on the form: by default, those whose settings {@link #hasChain} says do. */
    protected List<String> chainBones()
    {
        List<String> bones = new ArrayList<>();

        if (this.form != null)
        {
            for (BaseValue value : this.form.bones.getAll())
            {
                if (value instanceof FormBone bone && this.hasChain(bone))
                {
                    bones.add(bone.getId());
                }
            }
        }

        return bones;
    }

    private void fillChains()
    {
        List<String> bones = new ArrayList<>(this.chainBones());

        Collections.sort(bones);
        this.chains.setList(bones);

        if (!bones.isEmpty())
        {
            this.selected = bones.get(0);
            this.chains.setCurrentScroll(this.selected);
        }
    }

    private void display()
    {
        boolean has = !this.selected.isEmpty();

        for (UIElement input : this.inputs)
        {
            input.setEnabled(has);
        }

        if (!has)
        {
            return;
        }

        C control = this.displayControl(this.selected);

        this.syncing = true;

        try
        {
            this.sync(control);
        }
        finally
        {
            this.syncing = false;
        }
    }

    /** The values to show: the track's own control if it already has one, otherwise the form's config (so fields don't jump to defaults before the first edit). */
    private C displayControl(String bone)
    {
        S controls = this.getDisplayValue();

        if (controls != null && controls.controls.containsKey(bone))
        {
            return controls.controls.get(bone);
        }

        return this.configControl(bone);
    }

    protected void edit(Consumer<C> consumer)
    {
        if (this.syncing || this.selected.isEmpty())
        {
            return;
        }

        this.track.edit((selected) ->
        {
            S controls = (S) selected;

            /* Seed a chain's control from the config the first time it is touched,
             * so an edit to one field doesn't snap the others to defaults (the
             * override REPLACES the config wholesale at playback). */
            boolean fresh = !controls.controls.containsKey(this.selected);
            C control = controls.get(this.selected);

            if (fresh)
            {
                control.copy(this.configControl(this.selected));
            }

            consumer.accept(control);
        });
    }

    @Override
    public void render(UIContext context)
    {
        boolean busy = this.inputs.stream().anyMatch(input ->
            input instanceof UINumericInput<?> number && number.isUserEditing());
        if (!busy) this.display();
        super.render(context);
    }
}
