package mchorse.bbs_mod.ui.utility;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.utils.shader.UIShaderOptionsPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.iris.IrisShaderPacks;
import mchorse.bbs_mod.utils.iris.ShaderOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UIUtilityOverlayPanel extends UIOverlayPanel
{
    private static final int WIDTH = 460;
    private static final int PADDING = 6;

    /**
     * Height of the buttons at the bottom of both columns, so they line up: a button's own, since
     * a row places its children at the top in the height they ask for
     */
    private static final int BOTTOM_HEIGHT = UIConstants.CONTROL_HEIGHT;

    public Runnable callback;
    public UIScrollView view;
    private final UISliderTrackpad time;
    private final UIStringList shaders;
    private final UIText shaderStatus;
    private final UIButton shaderOptions;
    private final boolean iris = FabricLoader.getInstance().isModLoaded("iris");
    private boolean focused;
    private int pendingTime = -1;
    private long timeRequestDeadline;

    public UIUtilityOverlayPanel(IKey title, Runnable callback)
    {
        super(title);
        this.callback = callback;
        this.focused = MinecraftClient.getInstance().isWindowFocused();
        this.view = UI.scrollView(3, PADDING, 140);
        this.view.relative(this.content).w(0.4F).h(1F, -BOTTOM_HEIGHT - PADDING);

        UIIcon gameFolder = new UIIcon(Icons.FOLDER, b -> this.openFolder(BBSMod.getGameFolder()));
        gameFolder.tooltip(UIKeys.UTILITY_OPEN_GAME_FOLDER);
        this.view.add(this.header(UIKeys.UTILITY_ASSETS, gameFolder));
        this.view.add(this.assetRow(UIKeys.UTILITY_MODELS, Icons.POSE, "models"));
        this.view.add(this.assetRow(UIKeys.UTILITY_AUDIO, Icons.SOUND, "audio"));
        this.view.add(this.assetRow(UIKeys.UTILITY_VIDEO, Icons.VIDEO_CAMERA, "video"));
        this.view.add(this.assetRow(UIKeys.UTILITY_FONTS, Icons.FONT, "fonts"));
        this.view.add(this.assetRow(UIKeys.UTILITY_STRUCTURES, Icons.STRUCTURE, "structures").marginBottom(5));
        this.view.add(UI.label(UIKeys.UTILITY_RELOAD_LABEL));
        this.view.add(UI.row(
            this.reload(Icons.MATERIAL, UIKeys.UTILITY_RELOAD_TEXTURES, () -> BBSModClient.getTextures().delete()),
            this.reload(Icons.GLOBE, UIKeys.UTILITY_RELOAD_LANG, () -> BBSModClient.getL10n().reload()),
            this.reload(Icons.POSE, UIKeys.UTILITY_RELOAD_MODELS, () -> BBSModClient.getModels().reload()),
            this.reload(Icons.SOUND, UIKeys.UTILITY_RELOAD_SOUNDS, () -> BBSModClient.getSounds().deleteSounds()),
            this.reload(Icons.TREE, UIKeys.UTILITY_RELOAD_TERRAIN, BBSShaders::setup)
        ).marginBottom(5));

        this.time = new UISliderTrackpad(v -> this.setTime(v.intValue()));
        this.time.limit(0, 23999, true).values(100, 10, 1000).snap(10).delayedInput();
        MinecraftClient mc = MinecraftClient.getInstance();
        this.time.setValue(mc.world == null ? 6000 : Math.floorMod(mc.world.getTimeOfDay(), 24000));
        this.time.setEnabled(this.canChangeWorld());
        this.time.tooltip(UIKeys.UTILITY_TIME_PERMISSION);
        this.view.add(UI.label(UIKeys.UTILITY_TIME), this.time);
        UIButton commands = new UIButton(UIKeys.UTILITY_EXECUTE_DEFAULT_COMMANDS, b -> this.executeDefaultCommands());
        commands.tooltip(UIKeys.UTILITY_PREPARE_WORLD_DESCRIPTION);
        commands.setEnabled(this.canChangeWorld());
        UIIcon kill = new UIIcon(Icons.SKULL, b ->
        {
            if (this.canChangeWorld()) MinecraftClient.getInstance().player.networkHandler.sendCommand("kill @e[type=!player]");
        });
        kill.wh(16, 16);
        kill.tooltip(UIKeys.UTILITY_KILL_ENTITIES);
        kill.setEnabled(this.canChangeWorld());
        UIElement worldActions = new UIElement();
        worldActions.relative(this.content).x(PADDING).y(1F, -BOTTOM_HEIGHT - PADDING).w(0.4F, -PADDING * 2).h(BOTTOM_HEIGHT);
        worldActions.row(5).preferred(0).height(BOTTOM_HEIGHT);
        worldActions.add(commands, kill);

        UIElement right = new UIElement();
        right.relative(this.content).x(0.4F).w(0.6F).h(1F);
        UIIcon folder = new UIIcon(Icons.FOLDER, b -> this.openFolder(this.iris
            ? IrisShaderPacks.folder() : BBSMod.getGamePath("shaderpacks")));
        folder.tooltip(UIKeys.UTILITY_OPEN_SHADERS);
        UIElement header = this.header(UIKeys.UTILITY_SHADERS, folder);
        header.relative(right).xy(PADDING, PADDING).w(1F, -PADDING * 2).h(20);
        this.shaders = new UIStringList(list ->
        {
            if (!list.isEmpty()) this.selectShader(list.get(0));
        })
        {
            @Override
            protected String elementToString(UIContext context, int i, String element)
            {
                return element.isEmpty() ? UIKeys.UTILITY_SHADERS_OFF.get() : element;
            }
        };
        this.shaders.relative(right).xy(PADDING, PADDING + 20 + UIConstants.MARGIN).w(1F, -PADDING * 2).h(1F, -PADDING * 2 - 20 - BOTTOM_HEIGHT - UIConstants.MARGIN * 2);
        this.shaders.scroll.scrollItemSize = 18;
        this.shaders.background(BBSSettings.inputSurface());
        this.shaderStatus = new UIText().text(UIKeys.UTILITY_IRIS_REQUIRED);
        this.shaderStatus.relative(this.shaders).xy(5, 5).w(1F, -10).h(1F, -10);
        this.shaderStatus.setVisible(!this.iris);
        this.shaders.setEnabled(this.iris);
        this.shaderOptions = new UIButton(UIKeys.UTILITY_SHADER_OPTIONS, b -> UIShaderOptionsPanel.open(this.getContext()));
        this.shaderOptions.relative(right).x(PADDING).y(1F, -BOTTOM_HEIGHT - PADDING).w(1F, -PADDING * 2).h(BOTTOM_HEIGHT);
        this.shaderOptions.setEnabled(false);
        right.add(header, this.shaders, this.shaderStatus, this.shaderOptions);
        this.content.add(this.view, worldActions, right);
        this.refreshShaders();
    }

    @Override
    public int getPreferredWidth()
    {
        return WIDTH;
    }

    /**
     * As tall as the left column needs, so its buttons end where the shader settings do, and no
     * taller than the screen has room for; the shader list takes what's left on the right. Only
     * until the user sizes the panel.
     */
    @Override
    public int getContentHeight()
    {
        /* Before the first pass the column has no height yet */
        if (this.view.scroll.scrollSize <= 0)
        {
            return -1;
        }

        int height = this.content.getFlex().y.offset + this.view.scroll.scrollSize + BOTTOM_HEIGHT + PADDING;
        UIElement parent = this.getParent();

        return parent == null ? height : Math.min(height, (int) (parent.area.h * 0.9F));
    }

    @Override
    public void resize()
    {
        UIElement parent = this.getParent();

        /* No wider than the screen has room for, unless the user picked the width */
        if (parent != null && !this.wasResized())
        {
            this.flex.w.set(0F, Math.min(WIDTH, (int) (parent.area.w * 0.95F)));
        }

        super.resize();
    }

    private UIElement header(IKey label, UIIcon icon)
    {
        UIElement header = new UIElement();
        header.row(5).preferred(0).height(20);
        header.add(UI.label(label, 20).labelAnchor(0, 0.5F), icon);
        return header;
    }

    private UIElement assetRow(IKey label, Icon icon, String path)
    {
        UIElement row = new UIElement();
        row.row(5).preferred(0).height(20);
        AssetButton shared = new AssetButton(label, icon, () -> this.openFolder(new File(BBSMod.getOriginalSourcePack().getFolder(), path)));
        shared.tooltip(UIKeys.UTILITY_OPEN_SHARED);
        UIIcon world = new UIIcon(Icons.GLOBE, b ->
        {
            if (this.hasWorldAssets()) this.openFolder(new File(BBSMod.getWorldAssetsFolder(), path));
        });
        world.tooltip(this.hasWorldAssets() ? UIKeys.UTILITY_OPEN_WORLD : UIKeys.UTILITY_WORLD_UNAVAILABLE);
        world.setEnabled(this.hasWorldAssets());
        row.add(shared, world);
        return row;
    }

    private boolean hasWorldAssets()
    {
        return MinecraftClient.getInstance().getServer() != null
            && BBSMod.getWorldAssetsFolder() != null;
    }

    private UIIcon reload(Icon icon, IKey tooltip, Runnable action)
    {
        UIIcon button = new UIIcon(icon, b ->
        {
            action.run();
            this.close();
        });
        button.w(0).tooltip(tooltip);
        return button;
    }

    private boolean canChangeWorld()
    {
        var player = MinecraftClient.getInstance().player;
        return player != null && player.hasPermissionLevel(2);
    }

    private void setTime(int ticks)
    {
        if (!this.canChangeWorld()) return;
        this.pendingTime = ticks;
        this.timeRequestDeadline = System.nanoTime() + 3_000_000_000L;
        MinecraftClient.getInstance().player.networkHandler.sendCommand("time set " + ticks);
    }

    private void executeDefaultCommands()
    {
        if (!this.canChangeWorld()) return;
        for (String command : List.of("gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
            "gamerule doWardenSpawning false", "gamerule doMobSpawning false",
            "gamerule doTraderSpawning false", "gamerule randomTickSpeed 0"))
        {
            MinecraftClient.getInstance().player.networkHandler.sendCommand(command);
        }
    }

    private void openFolder(File folder)
    {
        folder.mkdirs();
        UIUtils.openFolder(folder);
    }

    private void refreshShaders()
    {
        if (!this.iris) return;
        try
        {
            List<String> packs = new ArrayList<>();
            packs.add("");
            packs.addAll(IrisShaderPacks.list());
            this.shaderStatus.setVisible(false);
            this.shaders.setList(packs);
            this.shaders.setCurrent(IrisShaderPacks.current());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            this.shaders.setList(new ArrayList<>());
            this.shaderStatus.text(UIKeys.UTILITY_SHADER_ERROR);
            this.shaderStatus.setVisible(true);
        }
    }

    private void selectShader(String name)
    {
        if (!this.iris || name.equals(IrisShaderPacks.current())) return;
        UIContext context = this.getContext();
        ShaderOptions.reload(() -> IrisShaderPacks.select(name), () ->
        {
            this.shaders.setCurrent(IrisShaderPacks.current());
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, UIKeys.UTILITY_SHADER_ERROR));
        });
    }

    @Override
    public void render(UIContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean focused = mc.isWindowFocused();
        if (focused && !this.focused) this.refreshShaders();
        this.shaderOptions.setEnabled(this.iris && !ShaderOptions.currentPack().isEmpty());
        this.focused = focused;
        if (!this.time.isDragging() && !this.time.isFocused() && mc.world != null)
        {
            int worldTime = (int) Math.floorMod(mc.world.getTimeOfDay(), 24000);
            /* Keep the requested position until the server's time catches up. Allow a
             * few advancing ticks, including the midnight wrap; don't wait forever
             * if the command was rejected or another source controls world time. */
            if (this.pendingTime >= 0 && (Math.floorMod(worldTime - this.pendingTime, 24000) <= 40
                || System.nanoTime() >= this.timeRequestDeadline))
            {
                this.pendingTime = -1;
            }
            if (this.pendingTime < 0) this.time.setValue(worldTime);
        }
        super.render(context);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);
        int x = this.content.area.x + (int) (this.content.area.w * 0.4F);
        context.batcher.box(this.content.area.x, this.content.area.y, x, this.content.area.ey(), BBSSettings.chromeSurface());
        context.batcher.box(x, this.content.area.y + PADDING, x + 1, this.content.area.ey() - PADDING, BBSSettings.dividerColor());
    }

    @Override
    public void onClose()
    {
        super.onClose();
        if (this.callback != null) this.callback.run();
    }

    private static class AssetButton extends UIClickable<AssetButton>
    {
        private final IKey label;
        private final Icon icon;

        AssetButton(IKey label, Icon icon, Runnable action)
        {
            super(b -> action.run());
            this.label = label;
            this.icon = icon;
        }

        @Override
        protected AssetButton get() { return this; }

        @Override
        protected void renderSkin(UIContext context)
        {
            RowStyle.row(context.batcher, this.area.x, this.area.y, this.area.w, this.area.h, 0, false, this.hover, false);
            context.batcher.icon(this.icon, RowStyle.iconColor(this.hover), this.area.x + 10, this.area.my(), 0.5F, 0.5F);
            var font = context.batcher.getFont();
            context.batcher.text(font.limitToWidth(this.label.get(), Math.max(0, this.area.w - 25)),
                this.area.x + 23, this.area.my() - font.getHeight() / 2, RowStyle.textColor(this.hover));
        }
    }
}
