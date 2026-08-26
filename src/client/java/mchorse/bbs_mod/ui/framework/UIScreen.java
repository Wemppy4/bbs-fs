package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.PixelArt;
import mchorse.bbs_mod.importers.IImportPathProvider;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.Importers;
import mchorse.bbs_mod.importers.types.IImporter;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.IFileDropListener;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.FFMpegUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UIScreen extends Screen implements IFileDropListener
{
    private UIBaseMenu menu;
    private UIRenderingContext context;

    public static void open(UIBaseMenu menu)
    {
        MinecraftClient.getInstance().setScreen(new UIScreen(Text.empty(), menu));
    }

    public static UIBaseMenu getCurrentMenu()
    {
        Screen currentScreen = MinecraftClient.getInstance().currentScreen;

        if (currentScreen instanceof UIScreen uiScreen)
        {
            return uiScreen.menu;
        }

        return null;
    }

    public UIScreen(Text title, UIBaseMenu menu)
    {
        super(title);

        MinecraftClient mc = MinecraftClient.getInstance();

        this.menu = menu;
        this.context = new UIRenderingContext(new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers()));

        this.menu.context.setup(this.context);
    }

    public UIBaseMenu getMenu()
    {
        return this.menu;
    }

    public void update()
    {
        this.menu.update();
    }

    public void renderInWorld(WorldRenderContext context)
    {
        this.menu.renderInWorld(context);
    }

    @Override
    public void filesDragged(List<Path> paths)
    {
        super.filesDragged(paths);

        String[] filePaths = new String[paths.size()];
        int i = 0;

        for (Path path : paths)
        {
            filePaths[i] = path.toAbsolutePath().toString();

            i += 1;
        }

        this.acceptFilePaths(filePaths);
    }

    @Override
    public void removed()
    {
        BBSModClient.setCustomGUIScale(false);
        MinecraftClient.getInstance().onResolutionChanged();

        super.removed();

        this.menu.onClose(null);

        if (this.menu.canHideHUD())
        {
            MinecraftClient.getInstance().options.hudHidden = false;
        }
    }

    @Override
    public void onDisplayed()
    {
        BBSModClient.setCustomGUIScale(true);
        MinecraftClient.getInstance().onResolutionChanged();

        super.onDisplayed();

        this.menu.onOpen(null);

        if (this.menu.canHideHUD())
        {
            MinecraftClient.getInstance().options.hudHidden = true;
        }
    }

    @Override
    public boolean shouldPause()
    {
        return this.menu.canPause();
    }

    @Override
    protected void init()
    {
        super.init();

        this.menu.resize(this.width, this.height);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height)
    {
        super.resize(client, width, height);

        this.menu.resize(width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        try
        {
            return this.menu.mouseClicked((int) mouseX, (int) mouseY, button);
        }
        catch (RuntimeException | Error e)
        {
            return this.report("mouse click", e);
        }
    }

    /**
     * Log a failure of an input handler before Minecraft wraps it into a crash report: building
     * that report can itself fail (a mixin of another mod loading a class mid-transformation),
     * and then the original stack is gone with it.
     */
    private boolean report(String action, Throwable e)
    {
        System.err.println("[BBS UI] Unhandled exception on " + action + " in " + this.menu.getClass().getSimpleName());
        e.printStackTrace();

        if (e instanceof RuntimeException runtime)
        {
            throw runtime;
        }

        throw (Error) e;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        return this.menu.mouseScrolled((int) mouseX, (int) mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        try
        {
            return this.menu.mouseReleased((int) mouseX, (int) mouseY, button);
        }
        catch (RuntimeException | Error e)
        {
            return this.report("mouse release", e);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        try
        {
            return this.menu.handleKey(keyCode, scanCode, BBSRendering.lastAction, modifiers);
        }
        catch (RuntimeException | Error e)
        {
            return this.report("key press", e);
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers)
    {
        return this.menu.handleKey(keyCode, scanCode, GLFW.GLFW_RELEASE, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers)
    {
        this.menu.handleTextInput(chr);

        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta)
    {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        /* Text is drawn with vanilla's programs, which are shared with the
         * world's text, so the pixel art ones are only allowed for the span
         * where BBS's UI is what's being drawn */
        PixelArt.setDrawingUI(true);

        try
        {
            super.render(context, mouseX, mouseY, delta);

            this.menu.context.setTransition(this.client.getTickDelta());
            this.menu.renderMenu(this.context, mouseX, mouseY);
            this.menu.context.render.executeRunnables();
        }
        finally
        {
            PixelArt.setDrawingUI(false);
        }
    }

    @Override
    public void acceptFilePaths(String[] paths)
    {
        if (this.menu != null)
        {
            if (!FFMpegUtils.checkFFMPEG())
            {
                this.menu.context.notifyError(UIKeys.IMPORTER_FFMPEG_NOTIFICATION);

                return;
            }

            File directory = null;
            boolean open = true;

            for (IImportPathProvider provider : this.menu.getRoot().getChildren(IImportPathProvider.class))
            {
                directory = provider.getImporterPath();

                if (directory != null)
                {
                    open = false;

                    break;
                }
            }

            List<File> files = new ArrayList<>();

            for (String path : paths)
            {
                File file = new File(path);

                if (file.exists())
                {
                    files.add(file);
                }
            }

            ImporterContext context = new ImporterContext(files, directory);

            for (IImporter importer : Importers.getImporters())
            {
                if (importer.canImport(context))
                {
                    importer.importFiles(context);

                    if (open)
                    {
                        UIUtils.openFolder(context.getDestination(importer));
                    }

                    this.menu.context.notifySuccess(UIKeys.IMPORTER_SUCCESS_NOTIFICATION.format(importer.getName()));

                    return;
                }
            }
        }
    }
}