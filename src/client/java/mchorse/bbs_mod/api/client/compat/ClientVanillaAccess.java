package mchorse.bbs_mod.api.client.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

/**
 * The client half of {@link mchorse.bbs_mod.api.compat.VanillaAccess}.
 *
 * <p>Swapping the game's framebuffer is how BBS renders a film at a size the window is not, and
 * an addon rendering into one of its own needs the same handle. Put back what you took: the game
 * draws into whatever is here when the frame ends.</p>
 */
public final class ClientVanillaAccess
{
    private ClientVanillaAccess()
    {}

    public static Framebuffer getFramebuffer(MinecraftClient client)
    {
        return client.framebuffer;
    }

    public static void setFramebuffer(MinecraftClient client, Framebuffer framebuffer)
    {
        client.framebuffer = framebuffer;
    }
}
