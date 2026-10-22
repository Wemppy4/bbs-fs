package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * FREX "flawless frames" signal, the same one Replay Mod gives while it renders
 * a video. Mods that skip or cut frames to save resources render every frame fully
 * while it's on: Dynamic FPS stops skipping frames when the window is in the
 * background (those frames were missing from the export), Sodium builds all
 * chunks before a frame instead of over several frames.
 *
 * Those mods find {@link #register(Function)} through the "frex_flawless_frames"
 * entrypoint, so FREX itself isn't needed.
 */
public class FlawlessFrames
{
    private static final List<Consumer<Boolean>> handles = new ArrayList<>();
    private static boolean active;

    public static void register(Function<String, Consumer<Boolean>> factory)
    {
        Consumer<Boolean> handle = factory.apply(BBSMod.MOD_ID);

        handles.add(handle);

        if (active)
        {
            handle.accept(true);
        }
    }

    public static void setActive(boolean active)
    {
        if (FlawlessFrames.active == active)
        {
            return;
        }

        FlawlessFrames.active = active;

        for (Consumer<Boolean> handle : handles)
        {
            handle.accept(active);
        }
    }
}
