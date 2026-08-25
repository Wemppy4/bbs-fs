package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.textures.IFolderTreeHost;
import mchorse.bbs_mod.ui.textures.TextureEntry;
import mchorse.bbs_mod.ui.textures.TextureFiles;
import mchorse.bbs_mod.ui.textures.UIFolderTree;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * "Save as" for a texture: pick the folder in the tree on the left, the name on the right —
 * with the folder's own textures listed above it so an existing one can be picked to
 * overwrite (after a word of warning), and a thumbnail of what's about to be written.
 * Enter saves, Escape cancels.
 */
public class UISaveTextureOverlayPanel extends UIOverlayPanel implements IFolderTreeHost
{
    private static final int TREE_WIDTH = 170;
    private static final int PREVIEW = 40;

    public UIFolderTree tree;
    public UIStringList files;
    public UITextbox name;
    public UIButton save;

    private final UITextureEditor editor;
    private final Predicate<Link> writer;
    private final UIElement right;

    private Link folder = new Link("", "");

    /**
     * @param writer writes the texture to the link; true when it did (the dialog closes then)
     */
    public UISaveTextureOverlayPanel(UITextureEditor editor, Predicate<Link> writer)
    {
        super(UIKeys.TEXTURES_SAVE_AS);

        this.editor = editor;
        this.writer = writer;

        this.tree = new UIFolderTree(this);
        this.files = new UIStringList((list) -> this.name.setText(list.get(0)));
        this.files.background();
        this.name = new UITextbox(1000, (text) -> {}).filename();
        this.name.placeholder(UIKeys.TEXTURES_SAVE_DIALOG_NAME);
        this.save = new UIButton(UIKeys.GENERAL_SAVE, (b) -> this.trySave());

        this.right = new UIElement();

        this.tree.relative(this.content).xy(0, 0).w(TREE_WIDTH).h(1F);
        this.right.relative(this.content).x(TREE_WIDTH + 10).y(0).w(1F, -TREE_WIDTH - 10).h(1F);

        /* Top: the folder's textures; bottom: the name and the save button beside the thumbnail */
        this.files.relative(this.right).xy(0, 20).w(1F).h(1F, -20 - 30 - PREVIEW - 10);
        this.name.relative(this.right).x(0).y(1F, -PREVIEW - 5).w(1F).h(20).anchorY(1F);
        this.save.relative(this.right).x(1F).y(1F).w(100).h(20).anchor(1F, 1F);

        this.right.add(this.files, this.name, this.save);
        this.content.add(this.tree, this.right);

        Link current = editor.getTexture();

        this.navigate(TextureEntry.folderLink(current.parent()));
        this.name.setText(StringUtils.fileName(current.path));
    }

    /* IFolderTreeHost */

    @Override
    public void navigate(Link folder)
    {
        this.folder = TextureEntry.folderLink(folder);
        this.tree.reveal(this.folder);
        this.refreshFiles();
    }

    @Override
    public boolean isCurrentFolder(Link folder)
    {
        return TextureEntry.folderLink(folder).equals(this.folder);
    }

    private void refreshFiles()
    {
        List<String> names = new ArrayList<>();

        if (!this.folder.source.isEmpty())
        {
            for (Link link : BBSMod.getProvider().getLinksFromPath(this.folder, false))
            {
                if (link.path.endsWith(".png"))
                {
                    names.add(StringUtils.fileName(link.path));
                }
            }
        }

        names.sort((a, b) -> NaturalOrderComparator.compare(true, a, b));

        this.files.setList(names);
        this.save.setEnabled(TextureFiles.isFolder(this.folder));
    }

    /* Saving */

    private Link target()
    {
        String name = this.name.getText().trim();

        if (name.isEmpty() || !TextureFiles.isFolder(this.folder))
        {
            return null;
        }

        return this.folder.combine(name.endsWith(".png") ? name : name + ".png");
    }

    private void trySave()
    {
        Link target = this.target();

        if (target == null)
        {
            return;
        }

        File file = TextureFiles.file(target);
        boolean taken = file != null && file.exists() && !target.equals(this.editor.getTexture());

        if (taken)
        {
            UIConfirmOverlayPanel confirm = new UIConfirmOverlayPanel(UIKeys.TEXTURES_SAVE_AS, UIKeys.TEXTURES_SAVE_DIALOG_OVERWRITE.format(file.getName()), (yes) ->
            {
                if (yes)
                {
                    this.write(target);
                }
            });

            UIOverlay.addOverlay(this.getContext(), confirm);
        }
        else
        {
            this.write(target);
        }
    }

    private void write(Link target)
    {
        if (this.writer.test(target))
        {
            this.close();
        }
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ENTER) && this.name.isFocused())
        {
            this.trySave();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        FontRenderer font = context.batcher.getFont();
        int x = this.right.area.x;
        int y = this.right.area.y;

        /* Where the file goes, in grey, with the gear of a read-only place when it can't */
        String where = this.folder.source.isEmpty() ? UIKeys.TEXTURES_SAVE_DIALOG_PICK_FOLDER.get() : this.folder.toString();
        boolean writable = TextureFiles.isFolder(this.folder);

        if (!writable && !this.folder.source.isEmpty())
        {
            context.batcher.icon(Icons.GEAR, Colors.LIGHTER_GRAY, x, y - 2);
            x += 18;
        }

        context.batcher.text(font.limitToWidth(where, this.right.area.w - (x - this.right.area.x)), x, y + 4, writable ? Colors.LIGHTER_GRAY : Colors.GRAY);

        /* What's about to be written */
        Texture texture = this.editor.getTemporaryTexture();

        if (texture != null && texture.width > 0)
        {
            int px = this.right.area.x;
            int py = this.right.area.ey() - PREVIEW;
            float scale = Math.min(PREVIEW / (float) texture.width, PREVIEW / (float) texture.height);
            int w = Math.max(1, Math.round(texture.width * scale));
            int h = Math.max(1, Math.round(texture.height * scale));

            context.batcher.iconArea(Icons.CHECKBOARD, px, py + (PREVIEW - h) / 2, w, h);
            context.batcher.fullTexturedBox(texture, px, py + (PREVIEW - h) / 2, w, h);
            context.batcher.text(texture.width + " × " + texture.height, px + w + 8, py + (PREVIEW - font.getHeight()) / 2, Colors.GRAY);
        }
    }
}
