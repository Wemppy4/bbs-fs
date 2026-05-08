package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
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
import net.minecraft.client.util.Window;
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

    private int lastGuiScale;
    private int logicalWidth;
    private int logicalHeight;
    private float renderScaleX = 1F;
    private float renderScaleY = 1F;

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
        MinecraftClient.getInstance().options.getGuiScale().setValue(this.lastGuiScale);
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
        this.lastGuiScale = MinecraftClient.getInstance().options.getGuiScale().getValue();

        MinecraftClient.getInstance().options.getGuiScale().setValue(1);
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

        this.updateScaleMetrics(this.width, this.height);
        this.menu.resize(this.logicalWidth, this.logicalHeight);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height)
    {
        super.resize(client, width, height);

        this.updateScaleMetrics(width, height);
        this.menu.resize(this.logicalWidth, this.logicalHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        return this.menu.mouseClicked(this.toLogicalX(mouseX), this.toLogicalY(mouseY), button);
    }

    public void setHorizontal(double horizontal)
    {
        this.menu.context.mouseWheelHorizontal = horizontal;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount)
    {
        return this.menu.mouseScrolled(this.toLogicalX(mouseX), this.toLogicalY(mouseY), verticalAmount);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        return this.menu.mouseReleased(this.toLogicalX(mouseX), this.toLogicalY(mouseY), button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (this.handleScaleHotkey(keyCode, modifiers))
        {
            return true;
        }

        return this.menu.handleKey(keyCode, scanCode, BBSRendering.lastAction, modifiers);
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
    public void renderBackground(DrawContext context)
    {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        super.render(context, mouseX, mouseY, delta);

        this.menu.context.setTransition(this.client.getTickDelta());
        this.menu.context.setScreenScale(this.renderScaleX, this.renderScaleY);
        this.menu.renderMenu(this.context, this.toLogicalX(mouseX), this.toLogicalY(mouseY));
        this.menu.context.render.executeRunnables();
    }

    public void refreshScale()
    {
        if (this.client == null)
        {
            return;
        }

        this.updateScaleMetrics(this.width, this.height);
        this.menu.resize(this.logicalWidth, this.logicalHeight);
    }

    private void updateScaleMetrics(int width, int height)
    {
        Window window = MinecraftClient.getInstance().getWindow();
        float scale = BBSModClient.getGUIScale();

        this.logicalWidth = Math.max(1, Math.round(window.getWidth() / scale));
        this.logicalHeight = Math.max(1, Math.round(window.getHeight() / scale));
        this.renderScaleX = width / (float) this.logicalWidth;
        this.renderScaleY = height / (float) this.logicalHeight;
        this.menu.context.setScreenScale(this.renderScaleX, this.renderScaleY);
    }

    private int toLogicalX(double mouseX)
    {
        return Math.max(0, Math.min(this.logicalWidth - 1, (int) Math.floor(mouseX / this.renderScaleX)));
    }

    private int toLogicalY(double mouseY)
    {
        return Math.max(0, Math.min(this.logicalHeight - 1, (int) Math.floor(mouseY / this.renderScaleY)));
    }

    private boolean handleScaleHotkey(int keyCode, int modifiers)
    {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0 || BBSRendering.lastAction == GLFW.GLFW_RELEASE)
        {
            return false;
        }

        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD)
        {
            BBSModClient.adjustGUIScale(1);

            return true;
        }
        else if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT)
        {
            BBSModClient.adjustGUIScale(-1);

            return true;
        }

        return false;
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
