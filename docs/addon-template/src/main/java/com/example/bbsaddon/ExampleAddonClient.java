package com.example.bbsaddon;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.BBSClientReadyEvent;
import mchorse.bbs_mod.api.client.events.FilmEvents;
import mchorse.bbs_mod.api.client.events.RegisterTrackStylesEvent;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client half of the example addon, registered under {@code bbs-client-addon}.
 */
public class ExampleAddonClient implements BBSAddonMod
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleAddon.MOD_ID);

    /** Gives this addon's track its own colour and icon on the timeline. */
    @Subscribe
    public void onTrackStyles(RegisterTrackStylesEvent event)
    {
        event.register(ExampleAddon.WOBBLE, Icons.CURVES, Colors.CYAN);
    }

    /**
     * The film's life. These are plain Fabric events rather than the addon bus, because two of
     * them run every tick and every frame — subscribe to them once, from here.
     */
    @Subscribe
    public void onClientReady(BBSClientReadyEvent event)
    {
        FilmEvents.CREATED.register((controller) ->
        {
            LOGGER.info("A film's scene was built with {} entities.", controller.getEntities().size());
        });

        FilmEvents.SHUTDOWN.register((controller) ->
        {
            /* Whatever CREATED built is let go here. A listener that skips this leaks for the
             * whole session. */
            LOGGER.info("A film ended.");
        });
    }
}
