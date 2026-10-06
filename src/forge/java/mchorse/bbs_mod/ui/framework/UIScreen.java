package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.window.InputCodes;
import mchorse.bbs_mod.graphics.window.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import mchorse.bbs_mod.graphics.WorldRenderContext;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.io.IOException;

/** Forge screen lifecycle and input boundary for the unchanged BBS menu tree. */
public class UIScreen extends GuiScreen implements mchorse.bbs_mod.ui.utils.IFileDropListener
{
    private final UIBaseMenu menu;
    private UIRenderingContext context;
    private float scale;
    private boolean opened;
    private boolean previousHiddenHUD;

    public UIScreen(UIBaseMenu menu) { this.menu = menu; }
    public static void open(UIBaseMenu menu) { Minecraft.getMinecraft().displayGuiScreen(new UIScreen(menu)); }
    public static UIBaseMenu getCurrentMenu()
    {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        return screen instanceof UIScreen ? ((UIScreen) screen).menu : null;
    }
    public UIBaseMenu getMenu() { return menu; }

    @Override public void initGui()
    {
        mchorse.bbs_mod.graphics.window.NativeFileDrop.attach();
        scale = BBSModClient.getGUIScale();
        width = (int) Math.ceil(mc.displayWidth / scale);
        height = (int) Math.ceil(mc.displayHeight / scale);
        context = new UIRenderingContext(new UIDrawContext(scale));
        menu.context.setup(context);
        menu.resize(width, height);
        if (!opened)
        {
            opened = true;
            previousHiddenHUD = mc.gameSettings.hideGUI;
            Keyboard.enableRepeatEvents(true);
            menu.onOpen(null);
            if (menu.canHideHUD()) mc.gameSettings.hideGUI = true;
        }
    }

    @Override public void onGuiClosed()
    {
        mchorse.bbs_mod.graphics.window.NativeFileDrop.detach();
        menu.onClose(null);
        mc.gameSettings.hideGUI = previousHiddenHUD;
        Keyboard.enableRepeatEvents(false);
        Window.resetCursor();
        opened = false;
    }
    @Override public boolean doesGuiPauseGame() { return menu.canPause(); }
    @Override public void updateScreen() { mchorse.bbs_mod.graphics.window.NativeFileDrop.poll(); menu.update(); }
    public void renderInWorld(WorldRenderContext context) { menu.renderInWorld(context); }

    @Override protected void mouseClicked(int x, int y, int button) { menu.mouseClicked(x, y, button); }
    @Override protected void mouseReleased(int x, int y, int button) { menu.mouseReleased(x, y, button); }
    public void mouseScrolled(int x, int y, double horizontal, double vertical) { menu.mouseScrolled(x, y, horizontal, vertical); }
    @Override public void handleMouseInput() throws IOException
    {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0)
        {
            Window.setVerticalScroll(wheel);
            menu.mouseScrolled((int) (Mouse.getEventX() / scale), (int) ((mc.displayHeight - Mouse.getEventY() - 1) / scale), 0, wheel / 120D);
        }
    }

    @Override public void handleKeyboardInput()
    {
        int nativeKey = Keyboard.getEventKey();
        boolean pressed = Keyboard.getEventKeyState();
        menu.handleKey(InputCodes.fromNative(nativeKey), nativeKey,
            pressed ? (Keyboard.isRepeatEvent() ? InputCodes.REPEAT : InputCodes.PRESS) : InputCodes.RELEASE, 0);
        if (pressed && !Character.isISOControl(Keyboard.getEventCharacter())) menu.handleTextInput(Keyboard.getEventCharacter());
        mc.dispatchKeypresses();
    }
    /** Used by synthetic Forge input as well as callers of GuiScreen.keyTyped. */
    @Override protected void keyTyped(char character, int nativeKey)
    {
        if (nativeKey != Keyboard.KEY_NONE) menu.handleKey(InputCodes.fromNative(nativeKey), nativeKey, InputCodes.PRESS, 0);
        if (!Character.isISOControl(character)) menu.handleTextInput(character);
    }

    /** Poll at render-tick start, while the preceding gizmo placement is still available.
     * Polling inside drawScreen is too late: world rendering has already forgotten that
     * placement, so between-tick G/S/R presses would incorrectly use additive dragging. */
    public void pollFrameInput()
    {
        /* Vanilla polls GuiScreen input only on its 20 Hz tick. BBS canvases,
         * like their original GLFW backend, need wheel/drag events each frame.
         * Reading the queue here consumes events once; the tick sees only new ones. */
        if (mc.currentScreen == this)
        {
            try { this.handleInput(); }
            catch (IOException error) { throw new RuntimeException("BBS UI input", error); }
        }
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        if (mc.currentScreen != this) return;
        try (mchorse.bbs_mod.graphics.OptiFineShaders.LocalPass pass = mchorse.bbs_mod.graphics.OptiFineShaders.localPass())
        {
        if (scale != BBSModClient.getGUIScale()) initGui();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, mc.displayWidth / (double) scale, mc.displayHeight / (double) scale, 0, 1000, 3000);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0, 0, -2000);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableFog();
        GlStateManager.disableCull();
        GlStateManager.color(1, 1, 1, 1);
        try
        {
            mchorse.bbs_mod.client.PixelArt.setDrawingUI(true);
            /* Forge 1.12 passes elapsedPartialTicks (the last frame duration) to
             * GuiScreen, whereas BBS interpolation needs the current tick fraction. */
            menu.context.setTransition(mchorse.bbs_mod.client.BBSRendering.worldTransition(mc.getRenderPartialTicks()));
            menu.renderMenu(context, (int) (Mouse.getX() / scale), (int) ((mc.displayHeight - Mouse.getY() - 1) / scale));
            context.executeRunnables();
            context.batcher.flush();
        }
        finally
        {
            mchorse.bbs_mod.client.PixelArt.setDrawingUI(false);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1, 1, 1, 1);
        }
        }
    }

    @Override public void acceptFilePaths(String[] paths)
    {
        if (paths == null || paths.length == 0) return;
        mchorse.bbs_mod.importers.Importers.setup();
        java.io.File directory = null;
        boolean open = true;
        for (mchorse.bbs_mod.importers.IImportPathProvider provider : this.menu.getRoot().getChildren(mchorse.bbs_mod.importers.IImportPathProvider.class))
        {
            directory = provider.getImporterPath();
            if (directory != null) { open = false; break; }
        }
        java.util.List<java.io.File> files = new java.util.ArrayList<>();
        for (String path : paths) { java.io.File file = new java.io.File(path); if (file.isFile()) files.add(file); }
        if (files.isEmpty()) return;
        mchorse.bbs_mod.importers.ImporterContext context = new mchorse.bbs_mod.importers.ImporterContext(files, directory);
        for (mchorse.bbs_mod.importers.types.IImporter importer : mchorse.bbs_mod.importers.Importers.getImporters())
        {
            if (!importer.canImport(context)) continue;
            boolean needsFFmpeg = !(importer instanceof mchorse.bbs_mod.importers.types.PNGImporter)
                && !(importer instanceof mchorse.bbs_mod.importers.types.OldSkinImporter);
            if (needsFFmpeg && !mchorse.bbs_mod.utils.FFMpegUtils.checkFFMPEG())
            { this.menu.context.notifyError(mchorse.bbs_mod.ui.UIKeys.IMPORTER_FFMPEG_NOTIFICATION); return; }
            try
            {
                importer.importFiles(context);
                if (open) mchorse.bbs_mod.ui.utils.UIUtils.openFolder(context.getDestination(importer));
                this.menu.context.notifySuccess(mchorse.bbs_mod.ui.UIKeys.IMPORTER_SUCCESS_NOTIFICATION.format(importer.getName()));
            }
            catch (RuntimeException error)
            {
                mchorse.bbs_mod.BBSMod.LOGGER.error("Could not import dropped files", error);
                this.menu.context.notifyError(mchorse.bbs_mod.l10n.keys.IKey.raw(error.getMessage()));
            }
            return;
        }
    }
}
