package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.AnimatedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.File;

/**
 * The column beside the grid about the chosen texture: a preview big enough to read, its
 * size, whether it animates, its path, the sampling toggles, and the way into the editor.
 * Unlike a form, a texture has facts worth reading, so they get a place of their own instead
 * of a card floating in a corner.
 */
public class UITextureInfoPanel extends UIElement
{
    public static final int WIDTH = 150;
    private static final int PADDING = 8;

    public UIToggle linear;
    public UIToggle mipmap;
    public UIButton edit;
    public UIIcon copy;

    private final UITextureBrowser browser;
    private final UIElement controls;

    private Link link;
    private Texture texture;
    private boolean animated;

    /* What the game made of the animation: how many frames it plays and how long a run is, in ticks */
    private int frames;
    private int length;

    private int files = -1;

    public UITextureInfoPanel(UITextureBrowser browser)
    {
        this.browser = browser;

        this.linear = new UIToggle(UIKeys.TEXTURES_LINEAR, (b) ->
        {
            if (this.texture != null)
            {
                int filter = b.getValue() ? GL11.GL_LINEAR : GL11.GL_NEAREST;

                if (this.texture.isReallyMipmap())
                {
                    filter = b.getValue() ? GL30.GL_LINEAR_MIPMAP_NEAREST : GL30.GL_NEAREST_MIPMAP_NEAREST;
                }

                this.texture.bind();
                this.texture.setFilter(filter);
            }
        });

        this.mipmap = new UIToggle(UIKeys.TEXTURES_MIPMAP, (b) ->
        {
            if (this.texture != null)
            {
                this.texture.bind();

                if (!this.texture.isMipmap())
                {
                    this.texture.generateMipmap();
                }

                this.texture.setParameter(GL30.GL_TEXTURE_MAX_LEVEL, b.getValue() ? 4 : 0);
            }
        });

        this.edit = new UIButton(UIKeys.GENERAL_EDIT, (b) -> this.browser.openInEditor(this.link));
        this.copy = new UIIcon(Icons.COPY, (b) ->
        {
            if (this.link != null)
            {
                Window.setClipboard(this.link.toString());
            }
        });
        this.copy.tooltip(UIKeys.TEXTURES_COPY, Direction.LEFT);

        this.controls = UI.column(5, 0, this.linear, this.mipmap, this.edit);
        this.controls.relative(this).x(PADDING).y(1F, -PADDING).w(1F, -PADDING * 2).anchorY(1F);

        this.add(this.controls, this.copy);
    }

    /** Show a texture, a folder, or nothing. */
    public void set(Link link)
    {
        this.link = link;
        this.texture = null;
        this.animated = false;
        this.frames = 0;
        this.length = 0;
        this.files = -1;

        boolean file = link != null && !link.path.endsWith("/") && !link.path.isEmpty();

        if (file)
        {
            Texture texture = BBSModClient.getTextures().getTexture(link);

            if (texture != null && texture != BBSModClient.getTextures().getError())
            {
                this.texture = texture;
                this.texture.bind();
                this.linear.setValue(this.texture.isLinear());
                this.mipmap.setValue(this.texture.isReallyMipmap());
            }

            File sidecar = TextureFiles.file(new Link(link.source, link.path + ".mcmeta"));

            this.animated = sidecar != null && sidecar.isFile();

            /* Loaded by getTexture above, when the sidecar could be read */
            AnimatedTexture animation = BBSModClient.getTextures().animatedTextures.get(link);

            if (animation != null)
            {
                this.frames = animation.index.getKeyframes().size();
                this.length = animation.length;
            }
        }
        else if (link != null)
        {
            int count = 0;

            for (Link l : BBSMod.getProvider().getLinksFromPath(link, false))
            {
                if (l.path.endsWith(".png"))
                {
                    count += 1;
                }
            }

            this.files = count;
        }

        this.controls.setVisible(this.texture != null);
        this.copy.setVisible(link != null);
    }

    @Override
    public void resize()
    {
        super.resize();

        this.copy.area.set(this.area.ex() - PADDING - 16, this.area.y + PADDING, 16, 16);
    }

    @Override
    public void render(UIContext context)
    {
        this.area.render(context.batcher, BBSSettings.color(BBSSettings.chromeSurface(), Colors.A50));

        FontRenderer font = context.batcher.getFont();
        int x = this.area.x + PADDING;
        int y = this.area.y + PADDING;
        int w = this.area.w - PADDING * 2;

        if (this.link == null)
        {
            String label = UIKeys.TEXTURES_BROWSER_INFO_EMPTY.get();

            context.batcher.text(label, this.area.mx(font.getWidth(label)), this.area.my() - 4, Colors.GRAY);
            super.render(context);

            return;
        }

        /* Name, with room for the copy icon */
        String name = this.link.path.isEmpty() ? this.link.source : this.link.path.endsWith("/") ? this.link.path.substring(0, this.link.path.length() - 1) : this.link.path;

        name = name.substring(name.lastIndexOf('/') + 1);
        context.batcher.textShadow(font.limitToWidth(name, w - 20), x, y + 4);
        y += 22;

        if (this.texture != null)
        {
            int tw = Math.max(1, this.texture.width);
            int th = Math.max(1, this.texture.height);
            float scale = Math.min(w / (float) tw, w / (float) th);
            int fw = Math.max(1, Math.round(tw * scale));
            int fh = Math.max(1, Math.round(th * scale));
            int fx = x + (w - fw) / 2;

            context.batcher.iconArea(Icons.CHECKBOARD, fx, y, fw, fh);
            context.batcher.fullTexturedBox(this.texture, fx, y, fw, fh);
            y += fh + 8;

            context.batcher.text(this.texture.width + " × " + this.texture.height, x, y, Colors.LIGHTER_GRAY);
            y += 12;

            if (this.animated)
            {
                /* What the game made of the .mcmeta when it could read it; the bare fact otherwise */
                String label = this.frames > 0
                    ? UIKeys.TEXTURES_BROWSER_INFO_ANIMATION.format(String.valueOf(this.frames), String.valueOf(this.length)).get()
                    : UIKeys.TEXTURES_BROWSER_INFO_ANIMATED.get();

                context.batcher.text(font.limitToWidth(label, w), x, y, Colors.LIGHTER_GRAY);
                y += 12;
            }

            if (!TextureFiles.canModify(this.link))
            {
                context.batcher.icon(Icons.GEAR, Colors.LIGHTER_GRAY, x, y - 4);
                context.batcher.text(UIKeys.TEXTURES_BROWSER_INFO_READ_ONLY.get(), x + 18, y, Colors.LIGHTER_GRAY);
                y += 12;
            }
        }
        else if (this.files >= 0)
        {
            context.batcher.icon(Icons.FOLDER, x + w / 2, y + 24, 0.5F, 0.5F);
            y += 56;

            context.batcher.text(UIKeys.TEXTURES_BROWSER_INFO_FILES.format(String.valueOf(this.files)).get(), x, y, Colors.LIGHTER_GRAY);
            y += 12;
        }

        /* The path, wrapped, in grey */
        y += 4;

        for (String line : font.wrap(this.link.toString(), w))
        {
            if (y > this.controls.area.y - 16)
            {
                break;
            }

            context.batcher.text(line, x, y, Colors.GRAY);
            y += font.getHeight() + 2;
        }

        super.render(context);
    }
}
