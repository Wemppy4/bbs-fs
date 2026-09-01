package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.data.migration.FormStableIds;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.UnknownForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.factory.MapFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FormArchitect extends MapFactory<Form, Void>
{
    /** Steps an addon added to the making of every form. See {@link #registerModifier}. */
    private static final List<Consumer<Form>> MODIFIERS = new ArrayList<>();

    @Override
    public String getTypeKey()
    {
        return "id";
    }

    /**
     * Every form read anywhere passes through here, so this is where form fragments living outside
     * the versioned documents — model blocks, morphs, items, old clipboards — get their body part
     * ids on first contact. Film documents arrive already converted (see {@code FilmStableIds});
     * for them this is a no-op walk.
     */
    @Override
    public Form fromData(MapType data)
    {
        if (data != null)
        {
            FormStableIds.ensure(data);
        }

        return super.fromData(data);
    }

    /**
     * Adds a step that runs on every form BBS makes, so an addon can put values of its own on
     * forms it did not write.
     *
     * <p>Everything else follows from the value existing: it is saved and read like any other,
     * the timeline finds a track for it because the track catalogue walks a form's values
     * rather than a list of names, and its keyframes work because the value carries its own
     * factory. Give it your namespace, or it will be dropped from the data whenever your addon
     * is not loaded.</p>
     *
     * <p>The palette's forms are templates and are built directly, so they are not modified;
     * anything that lands in a scene is copied, and a copy is read through here.</p>
     */
    public static void registerModifier(Consumer<Form> modifier)
    {
        MODIFIERS.add(modifier);
    }

    @Override
    public Form create(Link type)
    {
        Form form = super.create(type);

        for (int i = 0; i < MODIFIERS.size(); i++)
        {
            MODIFIERS.get(i).accept(form);
        }

        return form;
    }

    /**
     * A stand-in is deliberately left alone: it hands its data back exactly as it was read, so
     * anything added to it would be dropped on save anyway.
     */
    @Override
    public Form createUnknown(Link type, MapType data)
    {
        return new UnknownForm(type);
    }

    public boolean has(MapType data)
    {
        if (data.has(this.getTypeKey()))
        {
            Link id = Link.create(data.getString(this.getTypeKey()));

            return this.factory.containsKey(id);
        }

        return false;
    }
}
