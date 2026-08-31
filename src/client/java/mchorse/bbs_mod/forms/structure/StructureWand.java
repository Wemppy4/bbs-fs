package mchorse.bbs_mod.forms.structure;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.structures.UIStructureSaveMenu;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.renderers.InputRenderer;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

/**
 * The structure wand: a plain item whose whole behaviour lives here, on the client. Holding it
 * turns the two mouse buttons into region picking, and the vanilla break/place they would have
 * done is swallowed.
 *
 * <p>The buttons change meaning once {@link StructureSelection#isReady()}: left and right place
 * the corners while the region is incomplete, then become cancel and save. The HUD hint always
 * names the current pair, so the item needs no manual and no modifier keys.</p>
 *
 * <p>Only useful in singleplayer — the save it leads to runs against the integrated server's
 * template manager, and the structure form reads that same save folder.</p>
 */
public class StructureWand
{
    private static final Color COLOR = new Color();

    /** Mouse glyph footprint from {@link InputRenderer#renderMouseButtons}. */
    private static final int MOUSE_WIDTH = 14;
    private static final int MOUSE_HEIGHT = 18;
    private static final int GAP = 6;

    /** Prefilled into the next prompt, so re-saving the same structure is a single Enter. */
    private static String lastName = "";

    public static void register()
    {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
        {
            if (!isHolding(player, hand))
            {
                return ActionResult.PASS;
            }

            if (world.isClient)
            {
                if (StructureSelection.isReady())
                {
                    StructureSelection.clear();
                }
                else
                {
                    StructureSelection.setFirst(pos);
                }
            }

            /* Swallowed on both sides: the client stops before sending the break, and the
             * integrated server refuses it too if anything slipped through */
            return ActionResult.SUCCESS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
        {
            if (!isHolding(player, hand))
            {
                return ActionResult.PASS;
            }

            if (world.isClient)
            {
                if (StructureSelection.isReady())
                {
                    openSavePrompt();
                }
                else
                {
                    StructureSelection.setSecond(hitResult.getBlockPos());
                }
            }

            return ActionResult.SUCCESS;
        });
    }

    /**
     * Ask for a name, then hand the corners to the server. The selection is only dropped once the
     * name is confirmed — dismissing the prompt leaves the region alone to try again.
     */
    private static void openSavePrompt()
    {
        BlockPos min = StructureSelection.getMin();
        BlockPos max = StructureSelection.getMax();

        UIScreen.open(new UIStructureSaveMenu(lastName, (name) ->
        {
            lastName = name;

            ClientNetwork.sendSaveStructure(name, min, max);
            StructureSelection.clear();
        }));
    }

    private static boolean isHolding(PlayerEntity player, Hand hand)
    {
        return player.getStackInHand(hand).isOf(BBSMod.STRUCTURE_WAND_ITEM);
    }

    /** Whether the local player is holding the wand in either hand. */
    public static boolean isHolding()
    {
        PlayerEntity player = MinecraftClient.getInstance().player;

        return player != null && (isHolding(player, Hand.MAIN_HAND) || isHolding(player, Hand.OFF_HAND));
    }

    /**
     * Draw the region, or a single cube for a lone first corner. Depth testing is off on purpose:
     * a region is picked around a build, so its far edge is behind the build almost every time.
     */
    public static void renderWorld(WorldRenderContext context)
    {
        if (!isHolding() || StructureSelection.isEmpty())
        {
            return;
        }

        BlockPos min = StructureSelection.getMin();
        Vec3i size = StructureSelection.getSize();

        if (min == null)
        {
            /* Only the first corner is down — show it as the single block it currently covers */
            min = StructureSelection.getFirst();
            size = new Vec3i(1, 1, 1);
        }

        Vec3d camera = context.camera().getPos();
        MatrixStack stack = context.matrixStack();

        COLOR.set(BBSSettings.primaryColor.get());

        stack.push();
        stack.translate(min.getX() - camera.x, min.getY() - camera.y, min.getZ() - camera.z);

        RenderSystem.disableDepthTest();
        Draw.renderBox(stack, 0, 0, 0, size.getX(), size.getY(), size.getZ(), COLOR.r, COLOR.g, COLOR.b);
        RenderSystem.enableDepthTest();

        stack.pop();
    }

    /** The hint above the hotbar: which button does what right now, plus the size once there is one. */
    public static void renderHud(Batcher2D batcher)
    {
        if (!isHolding())
        {
            return;
        }

        boolean ready = StructureSelection.isReady();
        String left = L10n.lang(ready ? "bbs.ui.structure_wand.cancel" : "bbs.ui.structure_wand.first").get();
        String right = L10n.lang(ready ? "bbs.ui.structure_wand.save" : "bbs.ui.structure_wand.second").get();

        int width = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int height = MinecraftClient.getInstance().getWindow().getScaledHeight();

        int leftWidth = MOUSE_WIDTH + 4 + batcher.getFont().getWidth(left);
        int rightWidth = MOUSE_WIDTH + 4 + batcher.getFont().getWidth(right);
        int total = leftWidth + GAP * 2 + rightWidth;

        /* Above the hotbar, clear of the health and hunger rows */
        int y = height - 62;
        int x = (width - total) / 2;

        drawHint(batcher, x, y, true, false, left);
        drawHint(batcher, x + leftWidth + GAP * 2, y, false, true, right);

        Vec3i size = StructureSelection.getSize();

        if (size != null)
        {
            String label = size.getX() + " × " + size.getY() + " × " + size.getZ();

            batcher.textShadow(label, (width - batcher.getFont().getWidth(label)) / 2F, y - 12, Colors.WHITE);
        }
    }

    private static void drawHint(Batcher2D batcher, int x, int y, boolean left, boolean right, String label)
    {
        InputRenderer.renderMouseButtons(batcher, x, y, 0, left, right, false, false);
        batcher.textShadow(label, x + MOUSE_WIDTH + 4, y + (MOUSE_HEIGHT - batcher.getFont().getHeight()) / 2F, Colors.WHITE);
    }
}
