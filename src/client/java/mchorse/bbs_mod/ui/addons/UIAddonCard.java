package mchorse.bbs_mod.ui.addons;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.addons.Addon;
import mchorse.bbs_mod.api.BBSAddonSwitch;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One addon in the addons window: its picture, name and version, authors, and a switch.
 *
 * <p>A click on the picture opens the addon's settings, a click on the name its page. A crashed
 * addon's card is red and has a "copy error" button where the switch would be.</p>
 */
public class UIAddonCard extends UIElement
{
    public static final int HEIGHT = 32;

    private static final int PADDING = 4;
    private static final int AVATAR = 24;
    private static final int TEXT_X = PADDING + AVATAR + 6;

    /** Mod pictures, read once per game: they don't change while it runs. A missing one maps to null. */
    private static final Map<String, Texture> AVATARS = new HashMap<>();

    public final Addon addon;

    public UIAddonCard(Addon addon)
    {
        this.addon = addon;

        this.h(HEIGHT);

        UIAvatar avatar = new UIAvatar();
        UIName name = new UIName();

        avatar.relative(this).x(PADDING).y(PADDING).w(AVATAR).h(AVATAR);
        name.relative(this).x(TEXT_X).y(PADDING).w(name.getTextWidth()).h(12);
        this.add(avatar, name);

        if (addon.hasError())
        {
            UIIcon copy = new UIIcon(Icons.EXCLAMATION, (b) -> Window.setClipboard(addon.getErrorReport()));

            copy.tooltip(UIKeys.ADDONS_COPY_ERROR, Direction.LEFT);
            copy.relative(this).x(1F, -PADDING - 16).y(PADDING - 1).wh(16, 16);
            this.add(copy);
        }
        else
        {
            BBSAddonSwitch addonSwitch = addon.getSwitch();
            UIToggle toggle = new UIToggle(IKey.EMPTY, true, (b) ->
            {
                if (addonSwitch == null)
                {
                    /* Nothing to switch: the switch springs back */
                    b.setValue(true);
                }
                else
                {
                    addonSwitch.setAddonEnabled(b.getValue());
                }
            })
            {
                @Override
                public void render(UIContext context)
                {
                    if (addonSwitch != null)
                    {
                        this.setValue(addonSwitch.isAddonEnabled());
                    }

                    super.render(context);
                }
            };

            toggle.tooltip(addonSwitch == null ? UIKeys.ADDONS_SWITCH_UNSUPPORTED : UIKeys.ADDONS_SWITCH, Direction.LEFT);
            toggle.relative(this).x(1F, -PADDING - 24).y(PADDING - 1).wh(24, 14);
            this.add(toggle);
        }
    }

    @Override
    public void render(UIContext context)
    {
        int deep = BBSSettings.deepSurface();

        if (this.addon.hasError())
        {
            int red = Colors.A100 | Colors.RED;

            this.area.render(context.batcher, Colors.lerp(deep, red, 0.12F));
            context.batcher.box(this.area.x, this.area.y, this.area.x + 2, this.area.ey(), red);
        }
        else
        {
            this.area.render(context.batcher, deep);
        }

        String authors = this.addon.getAuthors();

        if (!authors.isEmpty())
        {
            FontRenderer font = context.batcher.getFont();
            String line = font.limitToWidth(authors, this.area.w - TEXT_X - PADDING);

            context.batcher.textShadow(line, this.area.x + TEXT_X, this.area.y + PADDING + 15, Colors.GRAY);
        }

        super.render(context);
    }

    private void openSettings()
    {
        String module = this.addon.settings.get(0);
        UIOverlayPanel panel = this.getParent(UIOverlayPanel.class);
        UIDashboard dashboard = BBSModClient.getDashboard();

        if (panel != null)
        {
            panel.close();
        }

        dashboard.openSettings(module);
    }

    private Texture getAvatar()
    {
        String id = this.addon.getId();

        if (AVATARS.containsKey(id))
        {
            return AVATARS.get(id);
        }

        Texture texture = null;

        try
        {
            Optional<String> icon = this.addon.getIconPath(64);
            Optional<Path> path = icon.flatMap(this.addon.mod::findPath);

            if (path.isPresent())
            {
                try (InputStream stream = Files.newInputStream(path.get()))
                {
                    Pixels pixels = Pixels.fromPNGStream(stream);

                    /* Pixel art stays sharp; a big picture shrinks smoother */
                    texture = Texture.textureFromPixels(pixels, pixels.width <= 64 ? GL11.GL_NEAREST : GL11.GL_LINEAR);
                    pixels.delete();
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        AVATARS.put(id, texture);

        return texture;
    }

    /** The addon's picture; with settings, it turns into their button under the mouse. */
    private class UIAvatar extends UIElement
    {
        public UIAvatar()
        {
            if (!addon.settings.isEmpty())
            {
                this.tooltip(UIKeys.CONFIG_TITLE, Direction.BOTTOM);
            }
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context) && !addon.settings.isEmpty())
            {
                UIUtils.playClick();
                openSettings();

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            Texture texture = getAvatar();

            this.area.render(context.batcher, BBSSettings.baseSurface());

            if (texture != null)
            {
                context.batcher.fullTexturedBox(texture, this.area.x, this.area.y, this.area.w, this.area.h);
            }
            else
            {
                context.batcher.icon(Icons.ADDONS, Colors.A100 | Colors.GRAY, this.area.mx(), this.area.my(), 0.5F, 0.5F);
            }

            if (!addon.settings.isEmpty() && this.area.isInside(context))
            {
                this.area.render(context.batcher, Colors.A50);
                context.batcher.icon(Icons.GEAR, this.area.mx(), this.area.my(), 0.5F, 0.5F);
                context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            }

            super.render(context);
        }
    }

    /** Name and version; the description is its tooltip, and a click opens the addon's page. */
    private class UIName extends UIElement
    {
        private final String name = addon.getName();
        private final String version = addon.getVersion();

        public UIName()
        {
            String description = addon.getDescription();
            IKey tooltip = description == null || description.isBlank() ? UIKeys.ADDONS_NO_DESCRIPTION : IKey.constant(description);

            this.tooltip(tooltip, 200, Direction.BOTTOM);
        }

        public int getTextWidth()
        {
            FontRenderer font = Batcher2D.getDefaultTextRenderer();

            return font.getWidth(this.name) + 4 + font.getWidth(this.version);
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            Optional<String> homepage = addon.getHomepage();

            if (context.mouseButton == 0 && this.area.isInside(context) && homepage.isPresent())
            {
                UIUtils.playClick();
                UIUtils.openWebLink(homepage.get());

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();
            int y = this.area.y + 2;
            int color = addon.hasError() ? Colors.A100 | Colors.RED : Colors.WHITE;

            context.batcher.textShadow(this.name, this.area.x, y, color);
            context.batcher.textShadow(this.version, this.area.x + font.getWidth(this.name) + 4, y, Colors.setA(Colors.WHITE, 0.5F));

            if (addon.getHomepage().isPresent() && this.area.isInside(context))
            {
                context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            }

            super.render(context);
        }
    }
}
