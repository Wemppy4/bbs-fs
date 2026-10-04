package mchorse.bbs_mod.ui.utility;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
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
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.client.Minecraft;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UIUtilityOverlayPanel extends UIOverlayPanel
{
    public Runnable callback;
    public UIScrollView view;
    private final UISliderTrackpad time;
    private final UIText shaderStatus;
    private int pendingTime = -1;
    private long timeRequestDeadline;

    public UIUtilityOverlayPanel(IKey title, Runnable callback)
    {
        super(title);
        this.callback = callback;
        this.view = UI.scrollView(3, 10, 140);
        this.view.relative(this.content).w(0.4F).h(1F);

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
        Minecraft mc = Minecraft.getMinecraft();
        this.time.setValue(mc.world == null ? 6000 : Math.floorMod(mc.world.getWorldTime(), 24000));
        this.time.setEnabled(this.canChangeWorld());
        this.time.tooltip(UIKeys.UTILITY_TIME_PERMISSION);
        this.view.add(UI.label(UIKeys.UTILITY_TIME), this.time);
        UIButton commands = new UIButton(UIKeys.UTILITY_EXECUTE_DEFAULT_COMMANDS, b -> this.executeDefaultCommands());
        commands.tooltip(UIKeys.UTILITY_PREPARE_WORLD_DESCRIPTION);
        commands.setEnabled(this.canChangeWorld());
        UIIcon kill = new UIIcon(Icons.SKULL, b ->
        {
            if (this.canChangeWorld()) Minecraft.getMinecraft().player.sendChatMessage("/" + "kill @e[type=!player]");
        });
        kill.wh(16, 16);
        kill.tooltip(UIKeys.UTILITY_KILL_ENTITIES);
        kill.setEnabled(this.canChangeWorld());
        UIElement worldActions = new UIElement();
        worldActions.row(5).preferred(0).height(20);
        worldActions.add(commands, kill);
        this.view.add(worldActions);

        UIElement right = new UIElement();
        right.relative(this.content).x(0.4F).w(0.6F).h(1F);
        UIIcon folder = new UIIcon(Icons.FOLDER, b -> this.openFolder(BBSMod.getGamePath("shaderpacks")));
        folder.tooltip(UIKeys.UTILITY_OPEN_SHADERS);
        UIElement header = this.header(UIKeys.UTILITY_SHADERS, folder);
        header.relative(right).xy(10, 10).w(1F, -20).h(20);
        /* Iris integration is explicitly deferred for this native Forge port. Keep the
         * original unavailable-provider message and layout; expose no dummy selector. */
        this.shaderStatus = new UIText().text(UIKeys.UTILITY_IRIS_REQUIRED);
        this.shaderStatus.relative(right).xy(15, 40).w(1F, -30).h(1F, -50);
        right.add(header, this.shaderStatus);
        this.content.add(this.view, right);
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
        AssetButton shared = new AssetButton(label, icon, () -> this.openFolder(new File(BBSMod.getAssetsFolder(), path)));
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
        return Minecraft.getMinecraft().getIntegratedServer() != null
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
        var player = Minecraft.getMinecraft().player;
        return player != null && player.canUseCommand(2, "time");
    }

    private void setTime(int ticks)
    {
        if (!this.canChangeWorld()) return;
        this.pendingTime = ticks;
        this.timeRequestDeadline = System.nanoTime() + 3_000_000_000L;
        Minecraft.getMinecraft().player.sendChatMessage("/" + "time set " + ticks);
    }

    private void executeDefaultCommands()
    {
        if (!this.canChangeWorld()) return;
        for (String command : java.util.Arrays.asList("gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
            "gamerule doMobSpawning false", "gamerule randomTickSpeed 0"))
        {
            Minecraft.getMinecraft().player.sendChatMessage("/" + command);
        }
    }

    private void openFolder(File folder)
    {
        folder.mkdirs();
        UIUtils.openFolder(folder);
    }

    @Override
    public void render(UIContext context)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (!this.time.isDragging() && !this.time.isFocused() && mc.world != null)
        {
            int worldTime = (int) Math.floorMod(mc.world.getWorldTime(), 24000);
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
        context.batcher.box(x, this.content.area.y + 10, x + 1, this.content.area.ey() - 10, BBSSettings.dividerColor());
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
