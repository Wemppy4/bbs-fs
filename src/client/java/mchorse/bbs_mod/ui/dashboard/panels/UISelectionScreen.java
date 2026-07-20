package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UISelectionScreen<T extends ValueGroup> extends UIElement
{
    private static final int CARD_W = 420;
    private static final int CARD_H = 420;
    private static final int BANNER_H = 150;
    private static final int HEADER_H = 16;
    private static final int PADDING = 10;
    private static final int HEADER_MARGIN = 6;
    private static final int ICON_GAP = 4;
    private static final int ICON_SIZE = 20;

    /** The banner every selection screen shows; a subclass can swap it for one of its own. */
    private static final Link BANNER = Link.assets("textures/banners/bg.png");

    protected final UIDataDashboardPanel<T> panel;
    protected final UIElement card;
    protected final UIElement banner;
    protected final UIElement header;
    protected final UIElement listWrap;
    protected final UILabel title;
    protected final UIIcon add;
    protected final UIIcon dupe;
    protected final UIIcon rename;
    protected final UIIcon remove;
    protected final UISearchList<DataPath> names;
    protected final UIDataPathList namesList;

    public UISelectionScreen(UIDataDashboardPanel<T> panel)
    {
        this.panel = panel;

        this.card = new UIElement();
        this.card.relative(this).xy(0.5F, 0.5F).wh(CARD_W, CARD_H).anchor(0.5F);

        this.banner = new UIElement();
        this.banner.relative(this.card).xy(0, 0).w(1F).h(BANNER_H);

        this.title = UI.label(this.getHeaderTitle());
        this.title.labelAnchor(0, 0.5F);

        this.header = new UIElement();
        this.header.relative(this.card).xy(PADDING, BANNER_H + HEADER_MARGIN).w(1F, -PADDING * 2).h(HEADER_H);

        this.listWrap = new UIElement();
        this.listWrap.relative(this.card)
            .xy(PADDING, BANNER_H + HEADER_H + HEADER_MARGIN * 2)
            .w(1F, -PADDING * 2)
            .h(1F, -(BANNER_H + HEADER_H + HEADER_MARGIN * 2 + PADDING));

        this.add = new UIIcon(Icons.ADD, (b) -> this.addData());
        this.add.wh(20, 20);
        this.add.context((menu) -> menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, this::addNewFolder));

        this.dupe = new UIIcon(this.getDuplicateButtonIcon(), (b) -> this.dupeSelected());
        this.rename = new UIIcon(Icons.EDIT, (b) -> this.renameSelected());
        this.remove = new UIIcon(Icons.REMOVE, (b) -> this.removeSelected());

        List<UIIcon> headerIcons = this.getHeaderIcons();
        int iconRowW = headerIcons.size() * ICON_SIZE + Math.max(0, headerIcons.size() - 1) * ICON_GAP;

        this.title.relative(this.header).xy(0, 0.5F).w(1F, -iconRowW).h(HEADER_H).anchor(0, 0.5F);

        UIElement iconRow = UI.row(ICON_GAP, headerIcons.toArray(new UIElement[0]));
        iconRow.row(0).resize().height(20);
        iconRow.relative(this.header).x(1F).y(0.5F).anchor(1F, 0.5F);

        this.names = new UISearchList<>(new UIDataPathList((list) -> this.panel.pickData(list.get(0).toString())));
        this.names.full(this.listWrap);
        this.namesList = (UIDataPathList) this.names.list;
        this.namesList.multi();
        this.namesList.openOnSingleClick(false);
        this.namesList.setFileIcon(this.getFileIcon());
        this.names.label(UIKeys.GENERAL_SEARCH);
        this.namesList.context((menu) ->
        {
            if (this.showActionButtons())
            {
                try
                {
                    MapType data = Window.getClipboardMap("_ContentType_" + this.panel.getType().getId());

                    if (data != null)
                    {
                        menu.action(Icons.PASTE, UIKeys.PANELS_CONTEXT_PASTE, () -> this.paste(data));
                    }
                }
                catch (Exception e)
                {}

                menu.action(Icons.ADD, UIKeys.GENERAL_ADD, this::addData);
                menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, this::addNewFolder);

                menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, this::renameSelected);
                menu.action(Icons.DUPE, UIKeys.GENERAL_DUPE, this::dupeSelected);
                menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, this::removeSelected);

                if (this.canCopySelected())
                {
                    menu.action(Icons.COPY, UIKeys.PANELS_CONTEXT_COPY, this::copy);
                }
            }

            File folder = this.panel.getType().getRepository().getFolder();

            if (folder != null)
            {
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () -> UIUtils.openFolder(new File(folder, this.namesList.getPath().toString())));
            }
        });

        this.namesList.keys().register(Keys.DELETE, this::removeSelected).active(this::canUseListKeybinds);
        this.namesList.keys().register(new KeyCombo(UIKeys.GENERAL_RENAME, GLFW.GLFW_KEY_F2), this::renameSelected).active(this::canUseListKeybinds);
        this.namesList.keys().register(new KeyCombo(UIKeys.PANELS_CONTEXT_OPEN, GLFW.GLFW_KEY_ENTER), this.namesList::activateSelection).active(this::canUseListKeybinds);
        this.namesList.keys().register(new KeyCombo(UIKeys.KEYFRAMES_CONTEXT_SELECT_ALL, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT_CONTROL), this.namesList::selectAll).active(this::canUseListKeybinds);

        this.header.add(this.title, iconRow);
        this.listWrap.add(new UIRenderable((ctx) -> this.renderListBackground(ctx, this.listWrap.area)), this.names);

        this.banner.add(new UIRenderable((ctx) -> this.renderBanner(ctx, this.banner.area)));
        this.card.add(new UIRenderable((ctx) -> this.renderCard(ctx, this.card.area)), this.banner, this.header, this.listWrap);
        this.add(new UIRenderable(this::renderBackdrop), this.card);
    }

    @Override
    public void setVisible(boolean visible)
    {
        boolean wasVisible = this.isVisible();

        super.setVisible(visible);

        if (visible && !wasVisible)
        {
            this.names.filter("", true);
            this.namesList.deselect();
            this.panel.requestNames();
        }
    }

    public void fillNames(Collection<String> names)
    {
        this.namesList.fill(names);
        this.namesList.deselect();
    }

    private List<UIIcon> getHeaderIcons()
    {
        ArrayList<UIIcon> icons = new ArrayList<>();

        if (this.showActionButtons())
        {
            icons.add(this.add);
            icons.add(this.dupe);
            icons.add(this.rename);
            icons.add(this.remove);
        }

        this.appendHeaderIcons(icons);

        return icons;
    }

    protected IKey getHeaderTitle()
    {
        return this.panel.getTitle();
    }

    protected Icon getFileIcon()
    {
        return Icons.FOLDER;
    }

    /** Whether create/duplicate/rename/remove are offered (header buttons + context menu). Asset-backed
     *  panels (e.g. the model editor) turn this off, leaving the screen as a pure picker. Default true. */
    protected boolean showActionButtons()
    {
        return true;
    }

    protected Icon getDuplicateButtonIcon()
    {
        return Icons.DUPE;
    }

    protected void appendHeaderIcons(List<UIIcon> icons)
    {}

    protected void updateCustomActionButtons(List<DataPath> selected)
    {}

    protected Link getBannerTexture()
    {
        return BANNER;
    }

    protected void onDuplicateData(T data)
    {}

    private boolean canUseListKeybinds()
    {
        UIContext context = this.getContext();

        if (!this.isVisible() || context == null)
        {
            return false;
        }

        return this.namesList.area.isInside(context) || this.namesList.isSelected();
    }

    private List<DataPath> getSelected()
    {
        List<DataPath> selected = this.namesList.getCurrent();

        if (selected.isEmpty())
        {
            return selected;
        }

        ArrayList<DataPath> filtered = new ArrayList<>();

        for (DataPath dataPath : selected)
        {
            if (dataPath != null && (!dataPath.folder || !dataPath.getLast().equals("..")))
            {
                filtered.add(dataPath);
            }
        }

        return filtered;
    }

    private List<DataPath> getSelectedFiles()
    {
        ArrayList<DataPath> files = new ArrayList<>();

        for (DataPath dataPath : this.getSelected())
        {
            if (!dataPath.folder)
            {
                files.add(dataPath);
            }
        }

        return files;
    }

    private void updateActionButtons()
    {
        List<DataPath> selected = this.getSelected();
        int selectedCount = selected.size();
        boolean single = selectedCount == 1;

        boolean canRename = single;
        boolean canDupe = false;

        for (DataPath dataPath : selected)
        {
            if (!dataPath.folder)
            {
                canDupe = true;
                break;
            }
        }

        this.dupe.setEnabled(canDupe);
        this.rename.setEnabled(canRename);
        this.remove.setEnabled(selectedCount > 0);

        this.updateCustomActionButtons(selected);
    }

    private void addData()
    {
        if (Window.isShiftPressed())
        {
            this.addNewData(this.getNextAutoId(), null);
        }
        else
        {
            this.addNewData(null);
        }
    }

    private String getNextAutoId()
    {
        int i = 1;

        while (true)
        {
            DataPath copy = this.namesList.getPath().copy();

            copy.combine(new DataPath(String.valueOf(i)));

            if (!this.namesList.getList().contains(copy))
            {
                return copy.toString();
            }

            i += 1;

            if (i >= 10000)
            {
                DataPath last = this.namesList.getPath().copy();

                last.combine(new DataPath("afk"));

                return last.toString();
            }
        }
    }

    private void addNewData(MapType data)
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_ADD,
            UIKeys.PANELS_MODALS_ADD,
            (str) -> this.addNewData(this.namesList.getPath(str).toString(), data)
        );

        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void addNewData(String name, MapType mapType)
    {
        if (name.trim().isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        if (this.namesList.hasInHierarchy(name))
        {
            return;
        }

        this.panel.save();

        T data;

        if (mapType == null)
        {
            data = (T) this.panel.getType().getRepository().create(name);
            this.panel.fillDefaultData(data);
        }
        else
        {
            data = (T) this.panel.getType().getRepository().create(name, mapType);
        }

        this.panel.fill(data);
        this.panel.save();
        this.panel.requestNames();
    }

    private void addNewFolder()
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_ADD_FOLDER,
            (str) -> this.addNewFolder(this.namesList.getPath(str).toString())
        );

        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void addNewFolder(String path)
    {
        if (path.trim().isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        this.panel.getType().getRepository().addFolder(path, (bool) ->
        {
            if (bool)
            {
                this.panel.requestNames();
            }
        });
    }

    private boolean canCopySelected()
    {
        return this.getSelectedFiles().size() == 1;
    }

    private void copy()
    {
        List<DataPath> files = this.getSelectedFiles();

        if (files.size() != 1)
        {
            return;
        }

        String id = files.get(0).toString();

        this.panel.getType().getRepository().load(id, (data) ->
        {
            if (data != null)
            {
                Window.setClipboard(data.toData().asMap(), "_ContentType_" + this.panel.getType().getId());
            }
        });
    }

    private void paste(MapType data)
    {
        this.addNewData(data);
    }

    private void dupeSelected()
    {
        List<DataPath> files = this.getSelectedFiles();

        if (files.isEmpty())
        {
            return;
        }

        if (files.size() == 1)
        {
            DataPath first = files.get(0);

            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_DUPE,
                UIKeys.PANELS_MODALS_DUPE,
                (str) -> this.dupeSelected(first.toString(), this.namesList.getPath(str).toString())
            );

            panel.text.setText(first.getLast());
            panel.text.filename();

            UIOverlay.addOverlay(this.getContext(), panel);

            return;
        }

        int[] remaining = {files.size()};

        for (DataPath src : files)
        {
            String from = src.toString();
            String to = this.getNextDupeId(src);

            this.panel.getType().getRepository().load(from, (data) ->
            {
                T loaded = (T) data;

                if (loaded != null)
                {
                    this.onDuplicateData(loaded);
                    this.panel.getType().getRepository().save(to, loaded.toData().asMap());
                }

                remaining[0] -= 1;

                if (remaining[0] <= 0)
                {
                    this.panel.requestNames();
                }
            });
        }
    }

    private String getNextDupeId(DataPath source)
    {
        String base = source.getLast();
        DataPath parent = source.getParent();
        String prefix = parent.strings.isEmpty() ? "" : parent.toString() + "/";

        String candidate = prefix + base + "_copy";
        int i = 2;

        while (this.namesList.hasInHierarchy(candidate))
        {
            candidate = prefix + base + "_copy" + i;
            i += 1;
        }

        return candidate;
    }

    private void dupeSelected(String from, String to)
    {
        if (to.trim().isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        if (this.namesList.hasInHierarchy(to))
        {
            return;
        }

        this.panel.getType().getRepository().load(from, (data) ->
        {
            T loaded = (T) data;

            if (loaded != null)
            {
                this.onDuplicateData(loaded);
                this.panel.getType().getRepository().save(to, loaded.toData().asMap());
            }

            this.panel.requestNames();
        });
    }

    private void renameSelected()
    {
        List<DataPath> selected = this.getSelected();

        if (selected.size() != 1)
        {
            return;
        }

        DataPath first = selected.get(0);

        if (first.folder)
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE,
                UIKeys.PANELS_MODALS_RENAME_FOLDER,
                (str) -> this.renameFolder(first.toString(), str)
            );

            panel.text.setText(first.getLast());
            panel.text.filename();

            UIOverlay.addOverlay(this.getContext(), panel);
        }
        else
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_RENAME,
                UIKeys.PANELS_MODALS_RENAME,
                (str) -> this.renameData(first.toString(), this.namesList.getPath(str).toString())
            );

            panel.text.setText(first.getLast());
            panel.text.filename();

            UIOverlay.addOverlay(this.getContext(), panel);
        }
    }

    private void renameFolder(String from, String name)
    {
        if (name.trim().isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        this.panel.getType().getRepository().renameFolder(from, name, (bool) ->
        {
            if (bool)
            {
                if (this.panel.getData() != null)
                {
                    String id = this.panel.getData().getId();

                    this.panel.getData().setId(name + "/" + id.substring(from.length()));
                }

                this.panel.onDataFolderRenamed(from, name);
                this.panel.requestNames();
            }
        });
    }

    private void renameData(String from, String to)
    {
        if (to.trim().isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        if (this.namesList.hasInHierarchy(to))
        {
            return;
        }

        this.panel.getType().getRepository().rename(from, to);

        if (this.panel.getData() != null && from.equals(this.panel.getData().getId()))
        {
            this.panel.getData().setId(to);
        }

        this.panel.onDataRenamed(from, to);
        this.panel.requestNames();
    }

    private void removeSelected()
    {
        List<DataPath> selected = this.getSelected();

        if (selected.isEmpty())
        {
            return;
        }

        boolean folderOnly = selected.size() == 1 && selected.get(0).folder && !selected.get(0).getLast().equals("..");
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
            folderOnly ? UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE : UIKeys.GENERAL_REMOVE,
            folderOnly ? UIKeys.PANELS_MODALS_REMOVE_FOLDER : UIKeys.PANELS_MODALS_REMOVE,
            (confirm) ->
            {
                if (!confirm)
                {
                    return;
                }

                this.removeSelectedNow(selected);
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void removeSelectedNow(List<DataPath> selected)
    {
        Set<String> removedData = new HashSet<>();
        Set<String> removedFolders = new HashSet<>();

        for (DataPath dataPath : selected)
        {
            if (dataPath.folder)
            {
                if (!dataPath.getLast().equals(".."))
                {
                    removedFolders.add(dataPath.toString());
                    this.panel.getType().getRepository().deleteFolder(dataPath.toString(), (b) -> {});
                }
            }
            else
            {
                String id = dataPath.toString();

                removedData.add(id);
                this.panel.getType().getRepository().delete(id);
            }
        }

        for (String id : removedData)
        {
            this.panel.onDataRemoved(id);
        }

        for (String folder : removedFolders)
        {
            this.panel.onDataFolderRemoved(folder);
        }

        this.namesList.deselect();
        this.panel.requestNames();
    }

    private void renderBackdrop(UIContext context)
    {
        this.updateActionButtons();

        int base = BBSSettings.baseSurface();
        int deep = BBSSettings.deepSurface();

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), base);
        context.batcher.gradientVBox(this.area.x, this.area.ey() - this.area.h / 2, this.area.ex(), this.area.ey(), Colors.setA(deep, 0F), deep);
    }

    private void renderCard(UIContext context, Area area)
    {
        int bg = BBSSettings.raisedSurface();
        int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A12);
        int accent = BBSSettings.primaryColor.get();

        context.batcher.dropShadow(area.x, area.y, area.ex(), area.ey(), 14, Colors.A50, 0);
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), bg);
        context.batcher.outline(area.x, area.y, area.ex(), area.ey(), border);

        int sepY = area.y + BANNER_H;
        int mid = area.mx();

        /* Bold accent seam — brightest at the center, fading toward the card edges */
        context.batcher.gradientHBox(area.x, sepY, mid, sepY + 2, Colors.setA(accent, 0F), Colors.A100 | accent);
        context.batcher.gradientHBox(mid, sepY, area.ex(), sepY + 2, Colors.A100 | accent, Colors.setA(accent, 0F));
        context.batcher.gradientVBox(area.x, sepY + 2, area.ex(), sepY + 48, Colors.A25 | accent, 0);
        context.batcher.gradientVBox(area.x, sepY + 2, area.ex(), sepY + 18, Colors.A50 | accent, 0);
    }

    private void renderListBackground(UIContext context, Area area)
    {
        int bg = BBSSettings.baseSurface();

        context.batcher.box(area.x, area.y, area.ex(), area.ey(), bg);
    }

    private void renderBanner(UIContext context, Area area)
    {
        Link bannerLink = this.getBannerTexture();

        if (bannerLink != null)
        {
            Texture texture = BBSModClient.getTextures().getTexture(bannerLink);

            if (texture != null)
            {
                float texW = texture.width;
                float texH = texture.height;
                float areaW = area.w;
                float areaH = area.h;

                float texAspect = texW / texH;
                float areaAspect = areaW / areaH;

                float u1;
                float u2;
                float v1;
                float v2;

                if (areaAspect > texAspect)
                {
                    float cropH = texW / areaAspect;

                    u1 = 0;
                    u2 = texW;
                    v1 = (texH - cropH) * 0.5F;
                    v2 = v1 + cropH;
                }
                else
                {
                    float cropW = texH * areaAspect;

                    u1 = (texW - cropW) * 0.5F;
                    u2 = u1 + cropW;
                    v1 = 0;
                    v2 = texH;
                }

                context.batcher.texturedBox(texture, Colors.WHITE, area.x, area.y, area.w, area.h, u1, v1, u2, v2, texture.width, texture.height);
            }
        }
    }
}
