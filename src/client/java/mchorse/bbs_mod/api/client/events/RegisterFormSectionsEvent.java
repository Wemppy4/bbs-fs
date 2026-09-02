package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.sections.FormSection;

import java.util.function.Function;

/**
 * Posted on the client before the form palette is built for the first time.
 *
 * <p>A section is a top-level tab of the palette — "Models", "Particles", "Extra" are BBS's own.
 * Without one, an addon's forms exist and work but there is nowhere for a user to pick them
 * from, which used to be the single place BBS offered no way in at all.</p>
 */
public class RegisterFormSectionsEvent
{
    /**
     * @param factory makes the section. It is asked again on every asset reload, which rebuilds
     *                the palette from scratch — a section handed over once would be thrown away
     *                the first time the user touched a file in the assets folder.
     */
    public void register(Function<FormCategories, FormSection> factory)
    {
        FormCategories.registerSection(factory);
    }
}
