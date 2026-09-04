package mchorse.bbs_mod.ui.dashboard;

import mchorse.bbs_mod.BBSModClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;

/**
 * Builds the dashboard ahead of the user asking for it.
 *
 * <p>The dashboard is built the first time it is opened, and the whole of it lands in that
 * one frame — the key is pressed, and the game stops for as long as every panel takes. The
 * work itself is unavoidable, but the moment isn't: this moves it to where the player is
 * already waiting, one {@link UIDashboard#buildNextStep() step} at a time.</p>
 *
 * <p>The moment worth having is the world still loading: nobody is looking at anything but a
 * progress bar, so building takes as much of the tick as it is given. Whatever is left over
 * once the world is up — a quick join leaves little of that window — is picked up in the
 * world instead, a single step at a time, where dropped frames would be noticed.</p>
 *
 * <p>Panels touch the UI and OpenGL, so every step runs on the render thread, the same as it
 * always did — this only moves it earlier, never off the thread.</p>
 */
public class DashboardWarmup
{
    /** How much of a tick building may take while the world is still loading. */
    private static final long BUDGET = 20L * 1_000_000L;

    /** Ticks in the world before the leftovers are picked up — joining is busy enough on its own. */
    private static final int DELAY = 40;

    /** Ticks between leftover steps. A step costs tens of milliseconds, and two shouldn't land in one tick. */
    private static final int EVERY = 5;

    private static int ticks;
    private static boolean done;

    /** The dashboard doesn't survive leaving the world, so neither does its warming up. */
    public static void reset()
    {
        ticks = 0;
        done = false;
    }

    public static void tick(MinecraftClient mc)
    {
        /* The world and the player being there is what makes building a panel safe — the
         * earliest loading screens come up before either of them exists */
        if (done || mc.world == null || mc.player == null || mc.getCameraEntity() == null)
        {
            return;
        }

        if (isLoading(mc.currentScreen))
        {
            long deadline = System.nanoTime() + BUDGET;

            do
            {
                done = !buildStep();
            }
            while (!done && System.nanoTime() < deadline);

            return;
        }

        /* Out in the world: a screen of any kind is either the user waiting on something or the
         * dashboard itself being used — neither is a moment to spend on building */
        if (mc.currentScreen != null || mc.isPaused())
        {
            return;
        }

        ticks += 1;

        if (ticks < DELAY || (ticks - DELAY) % EVERY != 0)
        {
            return;
        }

        done = !buildStep();
    }

    /** Returns whether anything is left to build after this step. */
    private static boolean buildStep()
    {
        return BBSModClient.getUnfinishedDashboard().buildNextStep();
    }

    /** Whether the screen up is one of the game's own loading screens, i.e. the world isn't ready yet. */
    private static boolean isLoading(Screen screen)
    {
        return screen instanceof DownloadingTerrainScreen
            || screen instanceof LevelLoadingScreen
            || screen instanceof ProgressScreen;
    }
}
