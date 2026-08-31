package mchorse.bbs_mod.ui.structures;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.structure.StructureManager;
import mchorse.bbs_mod.forms.structure.StructurePreview;
import mchorse.bbs_mod.forms.structure.StructureRenderData;
import mchorse.bbs_mod.forms.structure.StructureSelection;
import mchorse.bbs_mod.forms.structure.StructureWand;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * The structure wand's save dialog: the region turning in a preview on the left, exactly as the
 * file will hold it, and on the right its name, its two corners as numbers — a last chance to
 * nudge them, and the preview follows every change — and whether a form of it goes to "Recent".
 * Enter saves, Escape leaves the selection as it was.
 */
public class UIStructureSavePanel extends UIOverlayPanel
{
    private static final int SIDE = 200;
    private static final int PAD = 10;

    /** Every rebuild gets a fresh id: the renderer notices a new name, where it would keep the old data under a reused one. */
    private static int previews;

    public UIFormRenderer renderer;
    public UITextbox name;
    public UITrackpad ax;
    public UITrackpad ay;
    public UITrackpad az;
    public UITrackpad bx;
    public UITrackpad by;
    public UITrackpad bz;
    public UIToggle recent;
    public UIButton save;

    private final UIElement column;
    private final StructureForm form = new StructureForm();
    private final BiConsumer<String, Boolean> callback;

    private boolean tooBig;
    private boolean filling;

    /**
     * @param callback the name and whether a form goes to "Recent", once the user confirmed
     */
    public UIStructureSavePanel(String name, BiConsumer<String, Boolean> callback)
    {
        super(UIKeys.STRUCTURE_WAND_SAVE_TITLE);

        this.callback = callback;

        this.renderer = new UIFormRenderer();
        this.renderer.form = this.form;
        this.renderer.setRotation(-35, 22);

        this.name = new UITextbox(100, (t) -> {});
        this.name.placeholder(UIKeys.STRUCTURE_WAND_SAVE_NAME);
        this.name.setText(name);

        this.ax = this.corner(StructureWand.COLOR_A);
        this.ay = this.corner(StructureWand.COLOR_A);
        this.az = this.corner(StructureWand.COLOR_A);
        this.bx = this.corner(StructureWand.COLOR_B);
        this.by = this.corner(StructureWand.COLOR_B);
        this.bz = this.corner(StructureWand.COLOR_B);

        this.recent = new UIToggle(UIKeys.STRUCTURE_WAND_SAVE_RECENT, true, null);
        this.save = new UIButton(UIKeys.GENERAL_SAVE, (b) -> this.save());

        UIElement a = UI.row(this.ax, this.ay, this.az);
        UIElement b = UI.row(this.bx, this.by, this.bz);
        int label = this.getFont().getHeight();

        a.h(UIConstants.CONTROL_HEIGHT);
        b.h(UIConstants.CONTROL_HEIGHT);

        this.column = UI.column(5,
            this.name,
            UI.label(UIKeys.STRUCTURE_WAND_CORNER_A, label, StructureWand.COLOR_A).marginTop(6), a,
            UI.label(UIKeys.STRUCTURE_WAND_CORNER_B, label, StructureWand.COLOR_B).marginTop(6), b,
            this.recent.marginTop(6)
        );

        this.renderer.relative(this.content).xy(PAD, PAD).w(1F, -SIDE - PAD * 3).h(1F, -PAD * 2);
        this.column.relative(this.content).x(1F, -PAD).y(PAD).w(SIDE).anchorX(1F);
        this.save.relative(this.content).x(1F, -PAD).y(1F, -PAD).w(SIDE).anchor(1F, 1F);

        this.content.add(this.renderer, this.column, this.save);

        this.fill();
        this.rebuildPreview();
    }

    private FontRenderer getFont()
    {
        return Batcher2D.getDefaultTextRenderer();
    }

    /** One coordinate of a corner, colored like the corner it belongs to. */
    private UITrackpad corner(int color)
    {
        UITrackpad trackpad = new UITrackpad((v) -> this.applyCorners());

        trackpad.integer();
        trackpad.textbox.setColor(color);

        return trackpad;
    }

    /** Show the selection's corners; the trackpads' callbacks stay quiet meanwhile. */
    private void fill()
    {
        BlockPos a = StructureSelection.getA();
        BlockPos b = StructureSelection.getB();

        this.filling = true;

        this.ax.setValue(a.getX());
        this.ay.setValue(a.getY());
        this.az.setValue(a.getZ());
        this.bx.setValue(b.getX());
        this.by.setValue(b.getY());
        this.bz.setValue(b.getZ());

        this.filling = false;
    }

    /** A corner typed in: the selection in the world moves with it, and so does the preview. */
    private void applyCorners()
    {
        if (this.filling)
        {
            return;
        }

        StructureSelection.setA(new BlockPos((int) this.ax.getValue(), (int) this.ay.getValue(), (int) this.az.getValue()));
        StructureSelection.setB(new BlockPos((int) this.bx.getValue(), (int) this.by.getValue(), (int) this.bz.getValue()));

        this.rebuildPreview();
    }

    private void rebuildPreview()
    {
        BlockPos min = StructureSelection.getMin();
        Vec3i size = StructureSelection.getSize();

        if (min == null)
        {
            return;
        }

        this.tooBig = StructureSelection.getVolume() > StructurePreview.LIMIT;

        if (this.tooBig)
        {
            StructureManager.setPreview(null);
            this.form.structure.set("");

            return;
        }

        String id = "bbs:preview/" + (++previews);
        StructureRenderData data = StructurePreview.capture(id, min, size);

        StructureManager.setPreview(data);
        this.form.structure.set(data == null ? "" : id);

        /* The whole thing in view: the pivot sits under the middle of its footprint, so the
         * camera looks at half its height from far enough to fit its longest side */
        float extent = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));

        this.renderer.setPosition(0, size.getY() / 2F, 0);
        this.renderer.setDistance(Math.max(3F, extent * 1.1F + 1.5F));
    }

    /**
     * What the user typed, as a structure id: lower case, spaces as underscores, anything an
     * {@link Identifier} refuses dropped — a name like "My House" shouldn't be an error.
     */
    private static String sanitize(String name)
    {
        return name.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9/._:-]", "");
    }

    private void save()
    {
        String name = sanitize(this.name.getText());

        this.name.setText(name);

        if (name.isEmpty() || Identifier.tryParse(name) == null)
        {
            this.getContext().notifyError(UIKeys.STRUCTURE_WAND_SAVE_INVALID);
            this.getContext().focus(this.name);

            return;
        }

        this.callback.accept(name, this.recent.getValue());
        this.close();
    }

    @Override
    public void confirm()
    {
        this.save();
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ENTER) && this.name.isFocused())
        {
            this.save();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        this.name.textbox.moveCursorToEnd();
        parent.getContext().focus(this.name);
    }

    @Override
    public void onClose()
    {
        StructureManager.setPreview(null);

        super.onClose();
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        /* The preview's own ground, darker than the panel, so the box reads against it */
        this.renderer.area.render(context.batcher, BBSSettings.chromeSurface());
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        FontRenderer font = context.batcher.getFont();
        Vec3i size = StructureSelection.getSize();

        if (size != null)
        {
            String dimensions = size.getX() + " × " + size.getY() + " × " + size.getZ();
            String blocks = "   ·   " + UIKeys.STRUCTURE_WAND_BLOCKS.format(String.valueOf(StructureSelection.getVolume())).get();
            int x = this.column.area.x;
            int y = this.recent.area.ey() + 10;

            context.batcher.textShadow(dimensions, x, y, Colors.WHITE);
            context.batcher.textShadow(blocks, x + font.getWidth(dimensions), y, Colors.LIGHTER_GRAY);
        }

        if (this.tooBig)
        {
            String label = UIKeys.STRUCTURE_WAND_SAVE_TOO_BIG.get();

            context.batcher.textShadow(label, this.renderer.area.mx() - font.getWidth(label) / 2F, this.renderer.area.ey() - 6 - font.getHeight(), Colors.LIGHTER_GRAY);
        }
    }
}
