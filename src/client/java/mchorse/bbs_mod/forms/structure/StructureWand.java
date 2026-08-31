package mchorse.bbs_mod.forms.structure;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.structures.UIStructureSaveMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.InputRenderer;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * The structure wand: a plain item whose whole behaviour lives here, on the client. Holding it
 * turns the mouse into a region tool, and the vanilla break/place/attack the clicks would have
 * done is swallowed before the game sees them.
 *
 * <p>The left button places corner A, the right one corner B — on the block under the crosshair,
 * or on the block at arm's length when there is none, so a region can start in the air. A corner
 * is re-placed by clicking again, nothing has to be undone first. Once both are down the box can
 * be reshaped without touching them: with the crosshair on one of its faces the wheel pushes that
 * face in and out, and with sneak held it slides the whole box that way instead (Ctrl takes five
 * blocks per notch). Sneak + left drops the selection, sneak + right opens the save dialog. The
 * hint above the hotbar always names what the buttons do right now.</p>
 *
 * <p>Only useful in singleplayer — the save it leads to runs against the integrated server's
 * template manager, and the structure form reads that same save folder.</p>
 */
public class StructureWand
{
    public static final int COLOR_A = 0x4fb4ff;
    public static final int COLOR_B = 0xffb04a;

    /** Alpha of the box's faces, and of the one under the crosshair. */
    private static final float FACE = 0.07F;
    private static final float FACE_HOVER = 0.28F;
    private static final float CORNER_FILL = 0.3F;
    private static final float GHOST = 0.35F;

    /** Blocks one notch of the wheel is worth, plain and with Ctrl. */
    private static final int STEP = 1;
    private static final int STEP_FAST = 5;

    private static final Color COLOR = new Color();

    /** The inventory tooltip, line by line, out of the item's own language file. */
    private static final String[] TOOLTIP = {
        "item.bbs.structure_wand.tooltip.corners",
        "item.bbs.structure_wand.tooltip.faces",
        "item.bbs.structure_wand.tooltip.save"
    };

    /* HUD */

    /** Mouse glyph footprint from {@link InputRenderer#renderMouseButtons}. */
    private static final int MOUSE_WIDTH = 14;
    private static final int MOUSE_HEIGHT = 18;
    private static final int ROW = 20;
    private static final int GAP = 12;
    private static final int CARD = 0x77000000;

    /** Prefilled into the next save dialog, so re-saving the same structure is sneak + right and Enter. */
    private static String lastName = "";

    /** Structure id whose form goes to "Recent" once the server confirms the file is written. */
    private static String pendingRecent;

    /** The face of the box under the crosshair this frame, null when there is none: what the wheel acts on. */
    private static Direction face;

    /** The block the next click lands on, refreshed every frame for the ghost. */
    private static BlockPos pick;

    public static void register()
    {
        /* The clicks themselves are taken at MinecraftClient.doAttack/doItemUse (see
         * MinecraftClientMixin), before the game decides what a click on a block means. What that
         * leaves is the attack key HELD on a block: the game keeps trying to break it every tick,
         * and FAIL here is what stops that without a swing or crack particles. The server side of
         * the same callbacks is a net under all of it. */
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> isHolding(player, hand) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> isHolding(player, hand) ? ActionResult.FAIL : ActionResult.PASS);

        /* The item stays a plain Item: the tooltip is the one thing it would need a class for, and
         * it belongs on the client with the rest of the wand anyway */
        ItemTooltipCallback.EVENT.register((stack, context, lines) ->
        {
            if (stack.isOf(BBSMod.STRUCTURE_WAND_ITEM))
            {
                for (String line : TOOLTIP)
                {
                    lines.add(Text.translatable(line).formatted(Formatting.GRAY));
                }
            }
        });
    }

    /* Input */

    /** Left button: corner A, or with sneak the whole selection dropped. True when the click was the wand's. */
    public static boolean onAttack()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isActive(mc))
        {
            return false;
        }

        if (mc.player.isSneaking())
        {
            StructureSelection.clear();
        }
        else
        {
            StructureSelection.setA(pick(mc));
        }

        mc.player.swingHand(getHand(mc.player));

        return true;
    }

    /** Right button: corner B, or with sneak the finished box off to the save dialog. */
    public static boolean onUse()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isActive(mc))
        {
            return false;
        }

        if (mc.player.isSneaking())
        {
            if (StructureSelection.isReady())
            {
                openSave();
            }
        }
        else
        {
            StructureSelection.setB(pick(mc));
            mc.player.swingHand(getHand(mc.player));
        }

        return true;
    }

    /**
     * The wheel over a face of the box: pushes it, or slides the box with sneak held. True when the
     * notch was taken, in which case the hotbar must not get it.
     */
    public static boolean onScroll(double vertical)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!isActive(mc) || face == null || vertical == 0 || !StructureSelection.isReady())
        {
            return false;
        }

        int amount = (vertical > 0 ? 1 : -1) * (Window.isCtrlPressed() ? STEP_FAST : STEP);

        if (mc.player.isSneaking())
        {
            StructureSelection.move(face, amount);
        }
        else
        {
            StructureSelection.push(face, amount);
        }

        return true;
    }

    /** Whether the wand is in the player's hands with the world in front of them, not a screen. */
    private static boolean isActive(MinecraftClient mc)
    {
        return mc.player != null && mc.currentScreen == null && isHolding(mc.player);
    }

    /**
     * Where a click lands: the block under the crosshair, or the block at arm's length when the
     * crosshair is on nothing — a region can start in the air, above a build.
     */
    private static BlockPos pick(MinecraftClient mc)
    {
        HitResult hit = mc.crosshairTarget;

        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK)
        {
            return blockHit.getBlockPos();
        }

        float tickDelta = mc.getTickDelta();
        Vec3d eye = mc.player.getCameraPosVec(tickDelta);
        Vec3d look = mc.player.getRotationVec(tickDelta);
        double reach = mc.interactionManager == null ? 4.5 : mc.interactionManager.getReachDistance();

        return BlockPos.ofFloored(eye.add(look.multiply(reach)));
    }

    private static boolean isHolding(PlayerEntity player, Hand hand)
    {
        return player.getStackInHand(hand).isOf(BBSMod.STRUCTURE_WAND_ITEM);
    }

    private static boolean isHolding(PlayerEntity player)
    {
        return isHolding(player, Hand.MAIN_HAND) || isHolding(player, Hand.OFF_HAND);
    }

    /** Whether the local player is holding the wand in either hand. */
    public static boolean isHolding()
    {
        PlayerEntity player = MinecraftClient.getInstance().player;

        return player != null && isHolding(player);
    }

    private static Hand getHand(PlayerEntity player)
    {
        return isHolding(player, Hand.MAIN_HAND) ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    /* Saving */

    private static void openSave()
    {
        UIScreen.open(new UIStructureSaveMenu(lastName, StructureWand::save));
    }

    /**
     * From the dialog: hand the box to the server. The selection stays — the same structure can be
     * re-saved after a tweak — and the reply ends the job, see {@link #onSaved}.
     */
    public static void save(String name, boolean toRecent)
    {
        Identifier id = Identifier.tryParse(name);

        if (id == null || !StructureSelection.isReady())
        {
            return;
        }

        lastName = name;
        pendingRecent = toRecent ? id.toString() : null;

        ClientNetwork.sendSaveStructure(id.toString(), StructureSelection.getMin(), StructureSelection.getMax());
    }

    /**
     * The server's word on the save. The structure cache is dropped either way: a re-saved file
     * must reach every form already pointing at that name. The form for "Recent" is only made now,
     * once the file is really there — made earlier it would show the old structure, or nothing.
     */
    public static void onSaved(boolean ok, String name)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        StructureManager.invalidate();

        if (mc.player != null)
        {
            mc.player.sendMessage(Text.literal((ok ? UIKeys.STRUCTURE_WAND_SAVED : UIKeys.STRUCTURE_WAND_SAVE_FAILED).format(name).get()), true);
        }

        if (ok && name.equals(pendingRecent))
        {
            addRecentForm(name);
        }

        pendingRecent = null;
    }

    private static void addRecentForm(String id)
    {
        StructureForm form = new StructureForm();
        String path = id.substring(id.indexOf(':') + 1);

        form.structure.set(id);
        form.name.set(path.substring(path.lastIndexOf('/') + 1));

        BBSModClient.getFormCategories().getRecentForms().getCategories().get(0).addForm(form);
    }

    /* World */

    /**
     * Draw the box, its two corners and a ghost of the block the next click would take. Depth
     * testing is off on purpose: a region is picked around a build, so its far edge is behind the
     * build almost every time. The face under the crosshair is found here too — it is a property
     * of this frame's view, and the wheel reads it.
     */
    public static void renderWorld(WorldRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || !isHolding(mc.player))
        {
            face = null;
            pick = null;

            return;
        }

        float tickDelta = context.tickDelta();
        Vec3d camera = context.camera().getPos();
        Box box = StructureSelection.getBox();

        pick = pick(mc);
        face = box == null ? null : findFace(mc.player.getCameraPosVec(tickDelta), mc.player.getRotationVec(tickDelta), box);

        MatrixStack stack = context.matrixStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        if (box != null)
        {
            renderBox(stack, camera, box);
        }

        BlockPos a = StructureSelection.getA();
        BlockPos b = StructureSelection.getB();

        if (a != null)
        {
            renderCorner(stack, camera, a, COLOR_A);
        }

        if (b != null)
        {
            renderCorner(stack, camera, b, COLOR_B);
        }

        if (!pick.equals(a) && !pick.equals(b))
        {
            renderGhost(stack, camera, pick);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * The face of the box the ray goes through first — or, from inside the box, the one it leaves
     * through, which is the face the user is looking at either way. Null when the ray misses.
     */
    private static Direction findFace(Vec3d origin, Vec3d direction, Box box)
    {
        double tNear = Double.NEGATIVE_INFINITY;
        double tFar = Double.POSITIVE_INFINITY;
        Direction near = null;
        Direction far = null;

        for (Direction.Axis axis : Direction.Axis.values())
        {
            double o = origin.getComponentAlongAxis(axis);
            double d = direction.getComponentAlongAxis(axis);
            double lo = box.getMin(axis);
            double hi = box.getMax(axis);

            if (Math.abs(d) < 1E-9)
            {
                if (o < lo || o > hi)
                {
                    return null;
                }

                continue;
            }

            double tLo = (lo - o) / d;
            double tHi = (hi - o) / d;
            Direction loFace = Direction.from(axis, Direction.AxisDirection.NEGATIVE);
            Direction hiFace = Direction.from(axis, Direction.AxisDirection.POSITIVE);
            double tEnter = Math.min(tLo, tHi);
            double tExit = Math.max(tLo, tHi);

            if (tEnter > tNear)
            {
                tNear = tEnter;
                near = tLo < tHi ? loFace : hiFace;
            }

            if (tExit < tFar)
            {
                tFar = tExit;
                far = tLo < tHi ? hiFace : loFace;
            }
        }

        if (tNear > tFar || tFar < 0)
        {
            return null;
        }

        return tNear > 0 ? near : far;
    }

    private static void renderBox(MatrixStack stack, Vec3d camera, Box box)
    {
        float w = (float) box.getLengthX();
        float h = (float) box.getLengthY();
        float d = (float) box.getLengthZ();

        COLOR.set(BBSSettings.primaryColor.get());

        stack.push();
        stack.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (Direction side : Direction.values())
        {
            fillFace(builder, stack, side, w, h, d, COLOR.r, COLOR.g, COLOR.b, side == face ? FACE_HOVER : FACE);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        Draw.renderBox(stack, 0, 0, 0, w, h, d, COLOR.r, COLOR.g, COLOR.b, 1F);

        /* The face the wheel would push gets a white rim: a flat box, its thickness along the normal zero */
        if (face != null)
        {
            boolean positive = face.getDirection() == Direction.AxisDirection.POSITIVE;
            Direction.Axis axis = face.getAxis();
            float x = axis == Direction.Axis.X && positive ? w : 0;
            float y = axis == Direction.Axis.Y && positive ? h : 0;
            float z = axis == Direction.Axis.Z && positive ? d : 0;

            Draw.renderBox(stack, x, y, z, axis == Direction.Axis.X ? 0 : w, axis == Direction.Axis.Y ? 0 : h, axis == Direction.Axis.Z ? 0 : d, 1F, 1F, 1F, 0.9F);
        }

        stack.pop();
    }

    /** One face of a box standing at the origin, as two triangles. */
    private static void fillFace(BufferBuilder builder, MatrixStack stack, Direction side, float w, float h, float d, float r, float g, float b, float a)
    {
        switch (side)
        {
            case WEST -> Draw.fillQuad(builder, stack, 0, 0, 0, 0, 0, d, 0, h, d, 0, h, 0, r, g, b, a);
            case EAST -> Draw.fillQuad(builder, stack, w, 0, 0, w, 0, d, w, h, d, w, h, 0, r, g, b, a);
            case DOWN -> Draw.fillQuad(builder, stack, 0, 0, 0, w, 0, 0, w, 0, d, 0, 0, d, r, g, b, a);
            case UP -> Draw.fillQuad(builder, stack, 0, h, 0, w, h, 0, w, h, d, 0, h, d, r, g, b, a);
            case NORTH -> Draw.fillQuad(builder, stack, 0, 0, 0, w, 0, 0, w, h, 0, 0, h, 0, r, g, b, a);
            case SOUTH -> Draw.fillQuad(builder, stack, 0, 0, d, w, 0, d, w, h, d, 0, h, d, r, g, b, a);
        }
    }

    /** A corner block: filled in its own color, so A and B are told apart at a glance. */
    private static void renderCorner(MatrixStack stack, Vec3d camera, BlockPos pos, int color)
    {
        float r = Colors.getR(color);
        float g = Colors.getG(color);
        float b = Colors.getB(color);

        stack.push();
        stack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        Draw.fillBox(builder, stack, 0, 0, 0, 1, 1, 1, r, g, b, CORNER_FILL);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        Draw.renderBox(stack, 0, 0, 0, 1, 1, 1, r, g, b, 1F);

        stack.pop();
    }

    /** The block the next click would take, as a faint white frame. */
    private static void renderGhost(MatrixStack stack, Vec3d camera, BlockPos pos)
    {
        stack.push();
        stack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        Draw.renderBox(stack, 0, 0, 0, 1, 1, 1, 1F, 1F, 1F, GHOST);
        stack.pop();
    }

    /* HUD */

    private enum Glyph
    {
        LMB, RMB, WHEEL, SHIFT
    }

    /** One entry of the hint row: what to press, and what it does. */
    private record Hint(String label, int color, Glyph... glyphs)
    {
        private static final int JOIN = 9;
        private static final int AFTER = 5;

        public int width(FontRenderer font)
        {
            int width = 0;

            for (int i = 0; i < this.glyphs.length; i++)
            {
                width += glyphWidth(font, this.glyphs[i]) + (i > 0 ? JOIN : 0);
            }

            return width + AFTER + font.getWidth(this.label);
        }

        public void render(Batcher2D batcher, int x, int y)
        {
            FontRenderer font = batcher.getFont();

            for (int i = 0; i < this.glyphs.length; i++)
            {
                if (i > 0)
                {
                    batcher.textShadow("+", x + 2, y + (ROW - font.getHeight()) / 2F, Colors.LIGHTER_GRAY);
                    x += JOIN;
                }

                x += renderGlyph(batcher, this.glyphs[i], x, y);
            }

            batcher.textShadow(this.label, x + AFTER, y + (ROW - font.getHeight()) / 2F, this.color);
        }

        private static int glyphWidth(FontRenderer font, Glyph glyph)
        {
            return glyph == Glyph.SHIFT ? 16 + font.getWidth("Shift") : MOUSE_WIDTH;
        }

        private static int renderGlyph(Batcher2D batcher, Glyph glyph, int x, int y)
        {
            if (glyph == Glyph.SHIFT)
            {
                int width = glyphWidth(batcher.getFont(), glyph);

                batcher.icon(Icons.KEY_CAP_LEFT, x, y);
                batcher.iconArea(Icons.KEY_CAP_REPEATABLE, x + 4, y, width - 8, ROW);
                batcher.icon(Icons.KEY_CAP_RIGHT, x + width, y, 1F, 0F);
                batcher.text("Shift", x + 8, y + 5, Colors.A100);

                return width;
            }

            InputRenderer.renderMouseButtons(batcher, x, y + (ROW - MOUSE_HEIGHT) / 2, 0, glyph == Glyph.LMB, glyph == Glyph.RMB, glyph == Glyph.WHEEL, false);

            return MOUSE_WIDTH;
        }
    }

    /**
     * The hint above the hotbar: what each button does right now, and the box's size once there is
     * one. Wheel entries appear only while a face is under the crosshair, which is also what tells
     * the user the wheel is about to reshape the box rather than switch the slot.
     */
    public static void renderHud(Batcher2D batcher)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.currentScreen != null || mc.options.hudHidden || !isHolding(mc.player))
        {
            return;
        }

        FontRenderer font = batcher.getFont();
        boolean ready = StructureSelection.isReady();
        List<Hint> hints = new ArrayList<>();

        hints.add(new Hint(UIKeys.STRUCTURE_WAND_CORNER_A.get(), COLOR_A, Glyph.LMB));
        hints.add(new Hint(UIKeys.STRUCTURE_WAND_CORNER_B.get(), COLOR_B, Glyph.RMB));

        if (ready)
        {
            hints.add(new Hint(UIKeys.STRUCTURE_WAND_CLEAR.get(), Colors.WHITE, Glyph.SHIFT, Glyph.LMB));
            hints.add(new Hint(UIKeys.STRUCTURE_WAND_SAVE.get(), Colors.WHITE, Glyph.SHIFT, Glyph.RMB));

            if (face != null)
            {
                hints.add(new Hint(UIKeys.STRUCTURE_WAND_PUSH.get(), Colors.WHITE, Glyph.WHEEL));
                hints.add(new Hint(UIKeys.STRUCTURE_WAND_MOVE.get(), Colors.WHITE, Glyph.SHIFT, Glyph.WHEEL));
            }
        }

        int total = -GAP;

        for (Hint hint : hints)
        {
            total += hint.width(font) + GAP;
        }

        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        /* Above the hotbar, clear of the health and hunger rows */
        int y = height - 74;
        int x = (width - total) / 2;
        String status = null;
        int statusColor = Colors.WHITE;
        String detail = null;

        if (ready)
        {
            Vec3i size = StructureSelection.getSize();

            status = size.getX() + " × " + size.getY() + " × " + size.getZ();
            detail = UIKeys.STRUCTURE_WAND_BLOCKS.format(String.valueOf(StructureSelection.getVolume())).get();
        }
        else if (!StructureSelection.isEmpty())
        {
            BlockPos corner = StructureSelection.getA() != null ? StructureSelection.getA() : StructureSelection.getB();

            status = (StructureSelection.getA() != null ? "A" : "B") + "  " + corner.getX() + "  " + corner.getY() + "  " + corner.getZ();
            statusColor = StructureSelection.getA() != null ? COLOR_A : COLOR_B;
        }

        int top = y - (status == null ? 4 : 18);

        batcher.box(x - 8, top, x + total + 8, y + ROW + 4, CARD);

        if (status != null)
        {
            String line = detail == null ? status : status + "   ·   " + detail;
            int lineX = (width - font.getWidth(line)) / 2;

            batcher.textShadow(status, lineX, y - 13, statusColor);

            if (detail != null)
            {
                batcher.textShadow("   ·   " + detail, lineX + font.getWidth(status), y - 13, Colors.LIGHTER_GRAY);
            }
        }

        for (Hint hint : hints)
        {
            hint.render(batcher, x, y);

            x += hint.width(font) + GAP;
        }
    }
}
