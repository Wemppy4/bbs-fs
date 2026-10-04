package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.graphics.render.RenderSystem;
import mchorse.bbs_mod.graphics.WorldRenderContext;
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
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.ui.framework.elements.utils.UIVertexBuffer;
import mchorse.bbs_mod.graphics.NativeColorProgram;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
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
 * face in and out, and with Alt held it slides the whole box that way instead. Alt + left drops the
 * selection, Alt + right opens the save dialog. Alt is the modifier throughout on purpose: sneak
 * would drop a flying player out of the air and Ctrl would break their sprint, and both happen
 * exactly while lining a region up. The hint above the hotbar always names what the buttons do
 * right now.</p>
 *
 * <p>The save it leads to writes into BBS's {@code structures} assets folder, so a build captured
 * here is a form in every world, not only in this one. Only useful in singleplayer all the same:
 * the region is read by the server, and a dedicated one would write the file onto its own disk,
 * where this client cannot see it.</p>
 */
public class StructureWand
{
    public static final int COLOR_A = 0x4fb4ff;
    public static final int COLOR_B = 0xffb04a;

    /** Alpha of the face under the crosshair. The others are left empty so it reads as the one. */
    private static final float FACE_HOVER = 0.28F;
    private static final float CORNER_FILL = 0.3F;
    private static final float GHOST = 0.35F;

    /** Blocks one notch of the wheel is worth. A bigger jump is a click: re-placing a corner. */
    private static final int STEP = 1;

    private static final Color COLOR = new Color();

    /** The inventory tooltip, line by line, out of the item's own language file. */
    private static final String[] TOOLTIP = {
        "item.bbs.structure_wand.tooltip.corners",
        "item.bbs.structure_wand.tooltip.faces",
        "item.bbs.structure_wand.tooltip.save"
    };

    /* HUD */

    private static final int ROW = 20;

    /** Between the two columns. Wide enough that a row reads as a pair, not as four loose things. */
    private static final int COLUMN_GAP = 18;

    /** Prefilled into the next save dialog, so re-saving the same structure is Alt + right and Enter. */
    private static String lastName = "";

    /** Structure id whose form goes to "Recent" once the server confirms the file is written. */
    private static String pendingRecent;

    /** The face of the box under the crosshair this frame, null when there is none: what the wheel acts on. */
    private static EnumFacing face;

    /** The block the next click lands on, refreshed every frame for the ghost. */
    private static BlockPos pick;

    public static void register() { net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new StructureWand()); }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void mouse(net.minecraftforge.client.event.MouseEvent event)
    {
        if(!isActive(Minecraft.getMinecraft()))return;
        if(event.getDwheel()!=0 && onScroll(event.getDwheel()))event.setCanceled(true);
        if(event.getButton()==0||event.getButton()==1)
        {
            if(event.isButtonstate()){if(event.getButton()==0)onAttack();else onUse();}
            event.setCanceled(true);
        }
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void interaction(net.minecraftforge.event.entity.player.PlayerInteractEvent event)
    {
        if(event.isCancelable() && isHolding(event.getEntityPlayer()))event.setCanceled(true);
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event)
    {
        if(event.getItemStack().getItem()==net.minecraft.item.Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("bbs:structure_wand")))
            for(String line:TOOLTIP)event.getToolTip().add(net.minecraft.util.text.TextFormatting.GRAY+net.minecraft.client.resources.I18n.format(line));
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void world(net.minecraftforge.client.event.RenderWorldLastEvent event)
    {
        try(mchorse.bbs_mod.forms.renderers.NativeFormDraw.State state=new mchorse.bbs_mod.forms.renderers.NativeFormDraw.State();
            mchorse.bbs_mod.graphics.OptiFineShaders.LocalPass local=mchorse.bbs_mod.graphics.OptiFineShaders.localPass())
        {renderWorld(WorldRenderContext.capture(event.getPartialTicks()));}
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void hud(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event)
    {
        if(event.getType()!=net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL)return;
        Batcher2D batcher=new Batcher2D(new mchorse.bbs_mod.ui.framework.UIDrawContext(event.getResolution().getScaleFactor()));
        batcher.beginBatch();try{renderHud(batcher);}finally{batcher.endBatch();}
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote){StructureSelection.clear();StructureManager.invalidate();}}

    /* Input */

    /** Left button: corner A, or with Alt the whole selection dropped. True when the click was the wand's. */
    public static boolean onAttack()
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (!isActive(mc))
        {
            return false;
        }

        if (Window.isAltPressed())
        {
            StructureSelection.clear();
        }
        else
        {
            StructureSelection.setA(pick(mc));
        }

        mc.player.swingArm(getHand(mc.player));

        return true;
    }

    /** Right button: corner B, or with Alt the finished box off to the save dialog. */
    public static boolean onUse()
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (!isActive(mc))
        {
            return false;
        }

        if (Window.isAltPressed())
        {
            if (StructureSelection.isReady())
            {
                openSave();
            }
        }
        else
        {
            StructureSelection.setB(pick(mc));
            mc.player.swingArm(getHand(mc.player));
        }

        return true;
    }

    /**
     * The wheel over a face of the box: pushes it, or slides the box with Alt held. True when the
     * notch was taken, in which case the hotbar must not get it.
     */
    public static boolean onScroll(double vertical)
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (!isActive(mc) || face == null || vertical == 0 || !StructureSelection.isReady())
        {
            return false;
        }

        /* Wheel away from the user pulls the face towards them: the box follows the hand, not
         * the normal it happens to be drawn on */
        int amount = vertical > 0 ? -STEP : STEP;

        if (Window.isAltPressed())
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
    private static boolean isActive(Minecraft mc)
    {
        return mc.player != null && mc.currentScreen == null && isHolding(mc.player);
    }

    /**
     * Where a click lands: the block under the crosshair, or the block at arm's length when the
     * crosshair is on nothing — a region can start in the air, above a build.
     */
    private static BlockPos pick(Minecraft mc)
    {
        RayTraceResult hit=mc.objectMouseOver;
        if(hit!=null&&hit.typeOfHit==RayTraceResult.Type.BLOCK)return hit.getBlockPos();
        float partial=mc.getRenderPartialTicks();
        return new BlockPos(mc.player.getPositionEyes(partial).add(mc.player.getLook(partial).scale(mc.playerController==null?4.5:mc.playerController.getBlockReachDistance())));
    }

    private static boolean isHolding(EntityPlayer player, EnumHand hand)
    {
        return player.getHeldItem(hand).getItem() == net.minecraft.item.Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("bbs:structure_wand"));
    }

    private static boolean isHolding(EntityPlayer player)
    {
        return isHolding(player, EnumHand.MAIN_HAND) || isHolding(player, EnumHand.OFF_HAND);
    }

    /** Whether the local player is holding the wand in either hand. */
    public static boolean isHolding()
    {
        EntityPlayer player = Minecraft.getMinecraft().player;

        return player != null && isHolding(player);
    }

    private static EnumHand getHand(EntityPlayer player)
    {
        return isHolding(player, EnumHand.MAIN_HAND) ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
    }

    /* Saving */

    private static void openSave()
    {
        UIScreen.open(new UIStructureSaveMenu(lastName, StructureWand::save));
    }

    /**
     * From the dialog: hand the box to the server, which writes it into BBS's structures folder.
     * The selection stays — the same structure can be re-saved after a tweak — and the reply ends
     * the job, see {@link #onSaved}.
     *
     * @param name path under the structures folder, without the extension
     */
    public static void save(String name, boolean toRecent)
    {
        if (name.isEmpty() || !StructureSelection.isReady())
        {
            return;
        }

        lastName = name;
        pendingRecent = toRecent ? name : null;

        ClientNetwork.sendSaveStructure(name, StructureSelection.getMin(), StructureSelection.getMax());
    }

    /**
     * The server's word on the save. The structure cache is dropped either way: a re-saved file
     * must reach every form already pointing at that name. The form for "Recent" is only made now,
     * once the file is really there — made earlier it would show the old structure, or nothing.
     */
    public static void onSaved(boolean ok, String name)
    {
        Minecraft mc = Minecraft.getMinecraft();

        StructureManager.invalidate();

        if (mc.player != null)
        {
            mc.player.sendStatusMessage(new TextComponentString((ok ? UIKeys.STRUCTURE_WAND_SAVED : UIKeys.STRUCTURE_WAND_SAVE_FAILED).format(name).get()), true);
        }

        if (ok && name.equals(pendingRecent))
        {
            addRecentForm(name);
        }

        pendingRecent = null;
    }

    private static void addRecentForm(String path)
    {
        StructureForm form = new StructureForm();

        /* No name of its own: the form is named after the structure it holds, and a name set here
         * would stick to it even after the structure was swapped for another. */
        form.structure.set(StructureManager.assetId(path));

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
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.player == null || !isHolding(mc.player))
        {
            face = null;
            pick = null;

            return;
        }

        float tickDelta = context.tickDelta();
        Vec3d camera = new Vec3d(context.camera().position.x, context.camera().position.y, context.camera().position.z);
        AxisAlignedBB box = StructureSelection.getBox();

        pick = pick(mc);
        face = box == null ? null : findFace(mc.player.getPositionEyes(tickDelta), mc.player.getLook(tickDelta), box);

        MatrixStack stack = context.matrixStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        if (box != null)
        {
            renderAxisAlignedBB(stack, camera, box);
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
    private static double component(Vec3d v,EnumFacing.Axis axis){return axis==EnumFacing.Axis.X?v.x:axis==EnumFacing.Axis.Y?v.y:v.z;}

    private static EnumFacing findFace(Vec3d origin, Vec3d direction, AxisAlignedBB box)
    {
        double tNear = Double.NEGATIVE_INFINITY;
        double tFar = Double.POSITIVE_INFINITY;
        EnumFacing near = null;
        EnumFacing far = null;

        for (EnumFacing.Axis axis : EnumFacing.Axis.values())
        {
            double o = component(origin,axis);
            double d = component(direction,axis);
            double lo = component(new Vec3d(box.minX,box.minY,box.minZ),axis);
            double hi = component(new Vec3d(box.maxX,box.maxY,box.maxZ),axis);

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
            EnumFacing loFace = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.NEGATIVE,axis);
            EnumFacing hiFace = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE,axis);
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

    private static void renderAxisAlignedBB(MatrixStack stack, Vec3d camera, AxisAlignedBB box)
    {
        float w = (float) (box.maxX-box.minX);
        float h = (float) (box.maxY-box.minY);
        float d = (float) (box.maxZ-box.minZ);

        COLOR.set(BBSSettings.primaryColor.get());

        stack.push();
        stack.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);

        /* Only the hovered face is filled — an empty box lets the build inside it be seen, and
         * makes the one filled face unmistakable */
        if (face != null)
        {
            UIVertexBuffer builder = UIVertexBuffer.immediate();


            builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            fillFace(builder, stack, face, w, h, d, COLOR.r, COLOR.g, COLOR.b, FACE_HOVER);
            NativeColorProgram.draw(builder);
        }

        Draw.renderBox(stack, 0, 0, 0, w, h, d, COLOR.r, COLOR.g, COLOR.b, 1F);

        /* The face the wheel would push gets a white rim: a flat box, its thickness along the normal zero */
        if (face != null)
        {
            boolean positive = face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
            EnumFacing.Axis axis = face.getAxis();
            float x = axis == EnumFacing.Axis.X && positive ? w : 0;
            float y = axis == EnumFacing.Axis.Y && positive ? h : 0;
            float z = axis == EnumFacing.Axis.Z && positive ? d : 0;

            Draw.renderBox(stack, x, y, z, axis == EnumFacing.Axis.X ? 0 : w, axis == EnumFacing.Axis.Y ? 0 : h, axis == EnumFacing.Axis.Z ? 0 : d, 1F, 1F, 1F, 0.9F);
        }

        stack.pop();
    }

    /** One face of a box standing at the origin, as two triangles. */
    private static void fillFace(UIVertexBuffer builder, MatrixStack stack, EnumFacing side, float w, float h, float d, float r, float g, float b, float a)
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

        UIVertexBuffer builder = UIVertexBuffer.immediate();


        builder.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        Draw.fillBox(builder, stack, 0, 0, 0, 1, 1, 1, r, g, b, CORNER_FILL);
        NativeColorProgram.draw(builder);

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
        LMB, RMB, WHEEL, ALT
    }

    /** One entry of the hint row: what to press, and what it does. */
    @com.github.bsideup.jabel.Desugar
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
            return glyph == Glyph.ALT ? 16 + font.getWidth("Alt") : InputRenderer.MOUSE_WIDTH;
        }

        private static int renderGlyph(Batcher2D batcher, Glyph glyph, int x, int y)
        {
            if (glyph == Glyph.ALT)
            {
                int width = glyphWidth(batcher.getFont(), glyph);

                batcher.icon(Icons.KEY_CAP_LEFT, x, y);
                batcher.iconArea(Icons.KEY_CAP_REPEATABLE, x + 4, y, width - 8, ROW);
                batcher.icon(Icons.KEY_CAP_RIGHT, x + width, y, 1F, 0F);
                batcher.text("Alt", x + 8, y + 5, Colors.A100);

                return width;
            }

            InputRenderer.renderMouseButtons(batcher, x, y + (ROW - InputRenderer.MOUSE_HEIGHT) / 2, 0, glyph == Glyph.LMB, glyph == Glyph.RMB, glyph == Glyph.WHEEL, false);

            return InputRenderer.MOUSE_WIDTH;
        }
    }

    /**
     * The hint above the hotbar. Two columns: the bare gesture on the left, the same gesture with
     * Alt on the right, so a row reads across as "this button, and this button with Alt" and the
     * whole thing is half as wide as one long line. No plate behind it — the shadow on the text
     * carries it, and a slab of black over the world was worse than what it was protecting.
     *
     * <p>Wheel entries appear only while a face is under the crosshair, which is also what tells
     * the user the wheel is about to reshape the box rather than switch the slot.</p>
     */
    public static void renderHud(Batcher2D batcher)
    {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.player == null || mc.currentScreen != null || mc.gameSettings.hideGUI || !isHolding(mc.player))
        {
            return;
        }

        FontRenderer font = batcher.getFont();
        boolean ready = StructureSelection.isReady();
        List<Hint> plain = new ArrayList<>();
        List<Hint> alt = new ArrayList<>();

        plain.add(new Hint(UIKeys.STRUCTURE_WAND_CORNER_A.get(), COLOR_A, Glyph.LMB));
        plain.add(new Hint(UIKeys.STRUCTURE_WAND_CORNER_B.get(), COLOR_B, Glyph.RMB));

        if (ready)
        {
            alt.add(new Hint(UIKeys.STRUCTURE_WAND_CLEAR.get(), Colors.WHITE, Glyph.ALT, Glyph.LMB));
            alt.add(new Hint(UIKeys.STRUCTURE_WAND_SAVE.get(), Colors.WHITE, Glyph.ALT, Glyph.RMB));

            if (face != null)
            {
                plain.add(new Hint(UIKeys.STRUCTURE_WAND_PUSH.get(), Colors.WHITE, Glyph.WHEEL));
                alt.add(new Hint(UIKeys.STRUCTURE_WAND_MOVE.get(), Colors.WHITE, Glyph.ALT, Glyph.WHEEL));
            }
        }

        int leftWidth = columnWidth(font, plain);
        int rightWidth = columnWidth(font, alt);
        int total = leftWidth + (rightWidth == 0 ? 0 : COLUMN_GAP + rightWidth);
        int rows = Math.max(plain.size(), alt.size());

        int width = new net.minecraft.client.gui.ScaledResolution(mc).getScaledWidth();
        int height = new net.minecraft.client.gui.ScaledResolution(mc).getScaledHeight();

        /* Bottom edge clear of the hotbar and the health rows; the block grows upwards */
        int y = height - 54 - rows * ROW;
        int x = (width - total) / 2;

        renderStatus(batcher, font, ready, width, y - 13);

        for (int i = 0; i < rows; i++)
        {
            if (i < plain.size())
            {
                plain.get(i).render(batcher, x, y + i * ROW);
            }

            if (i < alt.size())
            {
                alt.get(i).render(batcher, x + leftWidth + COLUMN_GAP, y + i * ROW);
            }
        }
    }

    private static int columnWidth(FontRenderer font, List<Hint> hints)
    {
        int width = 0;

        for (Hint hint : hints)
        {
            width = Math.max(width, hint.width(font));
        }

        return width;
    }

    /** The line above the columns: the box's size once there is one, the lone corner before that. */
    private static void renderStatus(Batcher2D batcher, FontRenderer font, boolean ready, int width, int y)
    {
        String status;
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
        else
        {
            return;
        }

        String line = detail == null ? status : status + "   ·   " + detail;
        int lineX = (width - font.getWidth(line)) / 2;

        batcher.textShadow(status, lineX, y, statusColor);

        if (detail != null)
        {
            batcher.textShadow("   ·   " + detail, lineX + font.getWidth(status), y, Colors.LIGHTER_GRAY);
        }
    }

}
