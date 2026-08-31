package com.example.bbsaddon;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.BBSApi;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.events.RegisterFormModifiersEvent;
import mchorse.bbs_mod.api.events.RegisterFormsEvent;
import mchorse.bbs_mod.api.events.RegisterSettingsEvent;
import mchorse.bbs_mod.api.events.RegisterSourcePacksEvent;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The common half of the example addon — registered under the {@code bbs-addon} entry point and
 * loaded on both sides.
 *
 * <p>Its client half is {@link ExampleAddonClient}, and it has to be a separate class: the client
 * events live in BBS's client source set, and a class that so much as mentions one of them cannot
 * be loaded on a dedicated server.</p>
 */
public class ExampleAddon implements BBSAddonMod
{
    public static final String MOD_ID = "bbsaddontemplate";

    /**
     * The id of the value this addon puts on every form.
     *
     * <p>Namespaced on purpose. That is what makes BBS keep it in the saved data while this addon
     * is not loaded — an un-namespaced key is indistinguishable from one of BBS's own that was
     * removed, and is dropped on the next save.</p>
     */
    public static final String WOBBLE = MOD_ID + ":wobble";

    @Subscribe
    public void onSourcePacks(RegisterSourcePacksEvent event)
    {
        /* Before anything else: a mismatch here reads as "this addon does not fit this BBS build"
         * rather than as a crash on the first thing the user does. */
        BBSApi.requireVersion(MOD_ID, 1);

        /* Makes this addon's own assets addressable as bbsaddontemplate:... links. */
        event.registerAddon(MOD_ID, ExampleAddon.class);
    }

    @Subscribe
    public void onForms(RegisterFormsEvent event)
    {
        event.forms.register(Link.create(MOD_ID + ":gadget"), GadgetForm.class);
    }

    /**
     * Puts a value of this addon's on every form, including the ones BBS wrote.
     *
     * <p>Nothing else is needed for it to be animated: it is saved and read like any other value,
     * the timeline finds a track for it because the catalogue walks the form's values, and its
     * keyframes work because the value carries its own factory.</p>
     */
    @Subscribe
    public void onFormModifiers(RegisterFormModifiersEvent event)
    {
        event.register((form) -> form.add(new ValueFloat(WOBBLE, 0F)));
    }

    @Subscribe
    public void onSettings(RegisterSettingsEvent event)
    {
        event.register(Icons.GEAR, MOD_ID, (builder) ->
        {
            builder.category("general", Icons.GEAR);
            builder.getFloat("wobble_scale", 1F, 0F, 10F);
        });
    }
}
