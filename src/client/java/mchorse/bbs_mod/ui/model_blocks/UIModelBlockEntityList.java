package mchorse.bbs_mod.ui.model_blocks;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The model blocks of the world, as rows. A row is the form's icon, the form's name, the block's
 * coordinates, how far away it is and a preview of the form - the name in white, the rest dimmed,
 * the way the landing screen's list of recent documents reads.
 *
 * <p>The nearest block comes first: the question this list answers is nearly always "which of
 * these is the one in front of me". The distances are frozen when the list is filled rather than
 * counted every frame - order and numbers are read together, and a number that ticked while the
 * order stood still would read as a list sorted wrong.</p>
 *
 * <p>The same list serves the model block panel and the film's "From model block..." - one place
 * to look for the block one means, not two lists that show it differently.</p>
 */
public class UIModelBlockEntityList extends UIList<ModelBlockEntity>
{
    /** Tall enough for the form preview, like every other list that carries one. */
    public static final int ROW = 20;

    private static final int ICON_X = 2;
    private static final int TEXT_X = 22;
    private static final int GAP = 6;
    private static final int RIGHT_PADDING = 6;
    private static final int PREVIEW = 40;

    /** How far each block was when the list was last filled. See the class comment. */
    private final Map<ModelBlockEntity, Double> distances = new HashMap<>();

    public UIModelBlockEntityList(Consumer<List<ModelBlockEntity>> callback)
    {
        super(callback);

        this.scroll.scrollItemSize = ROW;
    }

    /**
     * Fill the list from the blocks of the world, nearest first, taking the distances afresh.
     */
    public void setBlocks(Collection<ModelBlockEntity> blocks)
    {
        this.clear();
        this.add(new ArrayList<>(blocks));
        this.sort();
    }

    /** Where "near" is measured from: the eye the user is looking through. */
    private static Vec3d eye()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();

        if (camera != null)
        {
            return camera.getPos();
        }

        return mc.player == null ? Vec3d.ZERO : mc.player.getPos();
    }

    private double distance(ModelBlockEntity element)
    {
        Double distance = this.distances.get(element);

        return distance == null ? Double.MAX_VALUE : distance;
    }

    @Override
    protected boolean sortElements()
    {
        Vec3d eye = eye();

        this.distances.clear();

        for (ModelBlockEntity element : this.list)
        {
            this.distances.put(element, element.getPos().toCenterPos().distanceTo(eye));
        }

        this.list.sort(Comparator.comparingDouble(this::distance));

        return true;
    }

    @Override
    protected String elementToString(UIContext context, int i, ModelBlockEntity element)
    {
        /* Also what the search box matches against, so both the form's name and the
         * coordinates of the row are typeable. */
        return element.getName();
    }

    @Override
    protected void renderElementPart(UIContext context, ModelBlockEntity element, int i, int x, int y, boolean hover, boolean selected)
    {
        FontRenderer font = context.batcher.getFont();
        Form form = element.getProperties().getForm();
        BlockPos pos = element.getPos();
        String coords = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();

        /* Without a form there is nothing to name the block by, so the coordinates take
         * the white column instead of leaving it empty. */
        String name = form == null ? coords : form.getDisplayName();
        String second = form == null ? "" : coords;
        Double distance = this.distances.get(element);
        String far = distance == null ? "" : UIKeys.MODEL_BLOCKS_DISTANCE.format(Math.round(distance)).get();

        boolean preview = form != null && BBSSettings.listModelPreview.get();
        Icon icon = form == null ? Icons.BLOCK : form.getIcon();
        int muted = Colors.setA(Colors.WHITE, 0.5F);
        int textY = y + (ROW - font.getHeight()) / 2 + 1;
        int right = x + this.area.w - RIGHT_PADDING
            - (this.scroll.hasScrollbar() ? this.scroll.getScrollbarWidth() : 0)
            - (preview ? PREVIEW : 0);
        int farW = font.getWidth(far);
        int textX = x + TEXT_X;

        /* A block that is off is greyed the way a disabled replay is in the film's list. */
        int nameColor = element.getProperties().isEnabled()
            ? (hover ? Colors.HIGHLIGHT : Colors.WHITE)
            : (hover ? Colors.mulRGB(Colors.HIGHLIGHT, 0.75F) : Colors.GRAY);

        context.batcher.icon(icon, x + ICON_X, y + ROW / 2, 0F, 0.5F);
        context.batcher.text(far, right - farW, textY, muted, false);

        String limited = font.limitToWidth(name, right - farW - GAP - textX);

        context.batcher.text(limited, textX, textY, nameColor, false);

        if (!second.isEmpty())
        {
            int secondX = textX + font.getWidth(limited) + GAP;
            int secondW = right - farW - GAP - secondX;

            if (secondW > font.getWidth("..."))
            {
                context.batcher.text(font.limitToWidth(second, secondW), secondX, textY, muted, false);
            }
        }

        if (preview)
        {
            int previewX = x + this.area.w - PREVIEW
                - (this.scroll.hasScrollbar() ? this.scroll.getScrollbarWidth() : 0);

            context.batcher.clip(previewX, y, PREVIEW, ROW, context);

            FormUtilsClient.renderUI(form, context, previewX, y - 10, previewX + PREVIEW, y + 30);

            context.batcher.unclip(context);
        }
    }
}
