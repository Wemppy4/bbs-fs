package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UIKeyframeDopeSheet implements IUIKeyframeGraph
{
    private static final int FOLD_BASE_INDENT = 4;
    private static final int FOLD_DEPTH_STEP = 4;
    private static final float TRACK_BAR_ALPHA = 0.3F;

    /** Width of the fold arrow's slot in the name column, left of the icon. */
    private static final int LABEL_ARROW_SIZE = 10;

    /** Track-name column layout: left text indent, right padding, right-side icon slot, text/icon gap. */
    private static final int LABEL_TEXT_LEFT = 5;
    private static final int LABEL_RIGHT_PAD = 2;
    private static final int LABEL_ICON_SIZE = 16;
    private static final int LABEL_TEXT_ICON_GAP = 3;
    private static final int LABEL_COMPACT_WIDTH = 60;

    /** Horizontal cull slack: wider than any keyframe shape's radius, so edge keyframes draw whole. */
    private static final int CULL_MARGIN = 20;

    private UIKeyframes keyframes;

    /** Every row, parents before their children — the order the catalog handed them over in. */
    private List<UIKeyframeSheet> sheets = new ArrayList<>();
    private Map<UIKeyframeSheet, Integer> sheetYCache = new HashMap<>();

    /** Which row each sheet is, counted down the visible list — what the striped background alternates on. */
    private Map<UIKeyframeSheet, Integer> sheetRowCache = new HashMap<>();
    private UIKeyframeSheet lastSheet;
    private final Map<UIKeyframeSheet.Section, Integer> sectionYCache = new LinkedHashMap<>();

    /**
     * Which rows the user has unfolded, by address. Owned by whoever built this timeline (so it
     * outlives a rebuild) and folded in place here — one state, not a copy on each side that has to
     * be kept in step. Every row starts folded.
     */
    private FoldState<String> folds = new FoldState<>();

    /** What to draw when there are no tracks at all - see {@link #setEmptyState(IKey, IKey)}. */
    private IKey emptyLabel;
    private IKey emptyHint;

    private Scroll dopeSheet;
    private double trackHeight;

    public static IKeyframeShapeRenderer renderShape(Keyframe frame, UIContext context, BufferBuilder builder, Matrix4f matrix, int x, int y, int offset, int c)
    {
        KeyframeShape keyframeShape = frame.getStyle().getShape();
        IKeyframeShapeRenderer shape = KeyframeShapeRenderers.SHAPES.get(keyframeShape);

        shape.renderKeyframe(context, builder, matrix, x, y, offset, c);

        return shape;
    }

    /** What a keyframe is coloured by when nothing is happening to it: its own colour, or its track's. */
    public static int keyframeColor(Keyframe frame, UIKeyframeSheet sheet)
    {
        Color color = frame.getStyle().getColor();

        return color != null ? color.getRGBColor() | Colors.A100 : sheet.color;
    }

    /**
     * A keyframe is drawn twice - a wide shape in its colour, then a narrower one on top - so what
     * that second pass is painted with decides whether the keyframe reads as a solid blob or as a
     * ring: repeating the colour fills it, black leaves a core. Selection outranks both, because
     * seeing what is selected matters more than seeing how it is styled.
     */
    public static int keyframeCoreColor(Keyframe frame, UIKeyframeSheet sheet, boolean selected)
    {
        if (selected)
        {
            return Colors.ACTIVE | Colors.A100;
        }

        return (frame.getStyle().isFilled() ? keyframeColor(frame, sheet) : 0) | Colors.A100;
    }

    public UIKeyframeDopeSheet(UIKeyframes keyframes)
    {
        this.keyframes = keyframes;
        this.dopeSheet = new Scroll(this.keyframes.area);
        this.dopeSheet.smoothScrolling(() -> !BBSSettings.scrollingDisableSmoothnessInEditors.get());
        this.dopeSheet.wheelScrollStep(() -> (int) this.trackHeight);

        this.setTrackHeight(16);
    }

    @Override
    public UIKeyframes getKeyframes()
    {
        return this.keyframes;
    }

    public double getTrackHeight()
    {
        return this.trackHeight;
    }

    public void setTrackHeight(double height)
    {
        this.trackHeight = MathUtils.clamp(height, 8D, 100D);
        this.updateScrollSize();
    }

    private void updateScrollSize()
    {
        this.sheetYCache.clear();
        this.sheetRowCache.clear();
        this.sectionYCache.clear();

        int y = 0;
        int row = 0;

        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (sheet.section != null && !this.sectionYCache.containsKey(sheet.section))
            {
                this.sectionYCache.put(sheet.section, y);
                y += (int) this.trackHeight;
                row += 1;
            }

            if (this.isVisible(sheet))
            {
                this.sheetYCache.put(sheet, y);
                this.sheetRowCache.put(sheet, row);

                y += this.getTrackHeight(sheet);
                row += 1;
            }
        }

        this.dopeSheet.scrollSize = y + TOP_MARGIN;

        /* The content just changed height, so where the view sits may no longer exist — folding the
         * rows away while scrolled to the bottom used to leave the timeline parked below every
         * row that was left. Every caller changes the height, so this belongs here and not in each
         * of them. */
        this.dopeSheet.clamp();
    }

    /** All rows use the same configured track height. */
    public int getTrackHeight(UIKeyframeSheet sheet)
    {
        return (int) this.trackHeight;
    }

    /** A row is drawn while every row it folds under is unfolded. */
    private boolean isVisible(UIKeyframeSheet sheet)
    {
        if (sheet.section != null && !this.folds.isExpanded(sheet.section.id()))
        {
            return false;
        }

        for (UIKeyframeSheet parent = sheet.parent; parent != null; parent = parent.parent)
        {
            if (!this.isUnfolded(parent))
            {
                return false;
            }
        }

        return true;
    }

    /** Whether a row shows what folds under it. */
    private boolean isUnfolded(UIKeyframeSheet sheet)
    {
        return this.folds.isExpanded(sheet.id);
    }

    private int getSheetIndent(UIKeyframeSheet sheet)
    {
        int depth = sheet.getDepth() + (sheet.section == null ? 0 : 1);

        if (depth == 0)
        {
            return 0;
        }

        int labelWidth = Math.max(1, this.keyframes.getLabelWidth());
        float scale = MathUtils.clamp(labelWidth / 120F, 0.75F, 1.5F);
        int baseIndent = Math.max(1, Math.round(FOLD_BASE_INDENT * scale));
        int depthStep = Math.max(1, Math.round(FOLD_DEPTH_STEP * scale));

        return baseIndent + (depth - 1) * depthStep;
    }

    private boolean hasChildren(UIKeyframeSheet sheet)
    {
        return !sheet.children.isEmpty();
    }

    private List<UIKeyframeSheet> getInteractiveSheets()
    {
        List<UIKeyframeSheet> sheets = new ArrayList<>();

        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (this.isVisible(sheet))
            {
                sheets.add(sheet);
            }
        }

        return sheets;
    }

    /* Graphing */

    public Scroll getYAxis()
    {
        return this.dopeSheet;
    }

    public int getDopeSheetY()
    {
        return this.keyframes.area.y + TOP_MARGIN - (int) this.dopeSheet.getScroll();
    }

    public int getDopeSheetY(int sheet)
    {
        return this.getDopeSheetY(this.sheets.get(sheet));
    }

    public int getDopeSheetY(UIKeyframeSheet sheet)
    {
        Integer y = this.sheetYCache.get(sheet);

        return this.getDopeSheetY() + (y == null ? 0 : y);
    }

    /**
     * Whether given mouse coordinates are near the given point?
     */
    public static boolean isNear(double x, double y, int mouseX, int mouseY, boolean checkOnlyX)
    {
        if (checkOnlyX)
        {
            return Math.pow(mouseX - x, 2) < 25D;
        }

        return Math.pow(mouseX - x, 2) + Math.pow(mouseY - y, 2) < 25D;
    }

    /* Sheet management */

    @Override
    public void resetView()
    {
        this.keyframes.resetViewX();
    }

    @Override
    public UIKeyframeSheet getLastSheet()
    {
        List<UIKeyframeSheet> sheets = this.getInteractiveSheets();

        if (this.lastSheet != null && sheets.contains(this.lastSheet))
        {
            return this.lastSheet;
        }

        return CollectionUtils.getSafe(sheets, 0);
    }

    @Override
    public List<UIKeyframeSheet> getSheets()
    {
        return this.sheets;
    }

    /**
     * Take the fold state the timeline's owner keeps. It is used in place, not copied, so folding a
     * row here is remembered across rebuilds without anything reading the state back out afterwards
     * — which is what the old save-and-restore dance existed for.
     */
    public void setExpanded(FoldState<String> folds)
    {
        this.folds = folds == null ? new FoldState<>() : folds;

        this.updateScrollSize();
    }

    public boolean hasSections()
    {
        return !this.sectionYCache.isEmpty();
    }

    public boolean hasExpandedSections()
    {
        for (UIKeyframeSheet.Section section : this.sectionYCache.keySet())
        {
            if (this.folds.isExpanded(section.id()))
            {
                return true;
            }
        }

        return false;
    }

    public void setAllSectionsExpanded(boolean expanded)
    {
        for (UIKeyframeSheet.Section section : this.sectionYCache.keySet())
        {
            this.folds.set(section.id(), expanded);
        }

        if (!expanded)
        {
            for (UIKeyframeSheet sheet : this.sheets)
            {
                if (sheet.section != null)
                {
                    sheet.selection.clear();
                }
            }
        }

        this.updateScrollSize();
        this.pickSelected();
    }

    public void removeAllSheets()
    {
        this.sheets.clear();
        this.updateScrollSize();
    }

    public void addSheet(UIKeyframeSheet sheet)
    {
        this.sheets.add(sheet);
        this.updateScrollSize();
    }

    @Override
    public void clearSelection()
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            sheet.selection.clear();
        }

        this.pickKeyframe(null);
    }

    @Override
    public void selectAll()
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            sheet.selection.all();
        }

        this.pickSelected();
    }

    @Override
    public void selectAfter(float tick, int direction)
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            sheet.selection.after(tick, direction);
        }

        this.pickSelected();
    }

    @Override
    public Keyframe getSelected()
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            Keyframe first = sheet.selection.getFirst();

            if (first != null)
            {
                return first;
            }
        }

        return null;
    }

    @Override
    public UIKeyframeSheet getSheet(String id)
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (sheet.id.equals(id))
            {
                return sheet;
            }
        }

        return null;
    }

    @Override
    public void removeSelected()
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            sheet.selection.removeSelected();
        }

        this.pickKeyframe(null);
    }

    /* Selection */

    @Override
    public void selectByX(int mouseX)
    {
        for (int i = 0; i < sheets.size(); i++)
        {
            UIKeyframeSheet sheet = sheets.get(i);
            List keyframes = sheet.channel.getKeyframes();

            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe keyframe = (Keyframe) keyframes.get(j);
                int x = this.keyframes.toGraphX(keyframe.getTick());
                int y = this.getDopeSheetY(sheet) + this.getTrackHeight(sheet) / 2;

                if (this.isNear(x, y, mouseX, 0, true))
                {
                    sheet.selection.add(j);
                }
            }
        }

        this.pickSelected();
    }

    @Override
    public void selectInArea(Area area)
    {
        List<UIKeyframeSheet> sheets = this.getInteractiveSheets();

        for (int i = 0; i < sheets.size(); i++)
        {
            UIKeyframeSheet sheet = sheets.get(i);
            List keyframes = sheet.channel.getKeyframes();

            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe keyframe = (Keyframe) keyframes.get(j);
                int x = this.keyframes.toGraphX(keyframe.getTick());
                int y = this.getDopeSheetY(sheet) + this.getTrackHeight(sheet) / 2;

                if (area.isInside(x, y))
                {
                    sheet.selection.add(j);
                }
            }
        }

        this.pickSelected();
    }

    @Override
    public UIKeyframeSheet getSheet(int mouseY)
    {
        int relY = mouseY - this.getDopeSheetY();

        for (Map.Entry<UIKeyframeSheet, Integer> entry : this.sheetYCache.entrySet())
        {
            int y = entry.getValue();

            if (relY >= y && relY < y + this.getTrackHeight(entry.getKey()))
            {
                return entry.getKey();
            }
        }

        return null;
    }

    @Override
    public boolean addKeyframe(int mouseX, int mouseY)
    {
        return this.addKeyframeAt(this.keyframes.fromGraphCursor(mouseX), mouseY);
    }

    @Override
    public boolean addKeyframeAt(float tick, int mouseY)
    {
        UIKeyframeSheet sheet = this.getSheet(mouseY);

        if (sheet != null)
        {
            this.addKeyframeManually(sheet, tick, null);
        }

        return sheet != null;
    }

    @Override
    public Pair<Keyframe, KeyframeType> findKeyframe(int mouseX, int mouseY)
    {
        UIKeyframeSheet sheet = this.getSheet(mouseY);

        if (sheet == null)
        {
            return null;
        }

        List keyframes = sheet.channel.getKeyframes();
        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe keyframe = (Keyframe) keyframes.get(j);
            int x = this.keyframes.toGraphX(keyframe.getTick());
            int y = this.getDopeSheetY(sheet) + this.getTrackHeight(sheet) / 2;

            if (this.isNear(x, y, mouseX, mouseY, false))
            {
                return new Pair<>(keyframe, KeyframeType.REGULAR);
            }
        }

        return null;
    }

    @Override
    public void onCallback(Keyframe keyframe)
    {
        UIKeyframeSheet sheet = this.getSheet(keyframe);

        if (sheet != null)
        {
            this.lastSheet = sheet;
        }
    }

    @Override
    public void selectKeyframe(Keyframe keyframe)
    {
        this.clearSelection();

        UIKeyframeSheet sheet = this.getSheet(keyframe);

        if (sheet != null)
        {
            sheet.selection.add(keyframe);
            this.pickKeyframe(keyframe);

            double x = keyframe.getTick();
            Integer sheetY = this.sheetYCache.get(sheet);
            int y = (sheetY == null ? 0 : sheetY) + TOP_MARGIN;

            this.keyframes.getXAxis().shiftIntoMiddle(x);
            this.dopeSheet.scrollTo((int) (y - (this.dopeSheet.area.h - this.trackHeight) / 2));
        }
    }

    @Override
    public void resize()
    {
        this.dopeSheet.clamp();
    }

    /* Input handling */

    @Override
    public boolean mouseClicked(UIContext context)
    {
        if (this.dopeSheet.mouseClicked(context))
        {
            return true;
        }

        if (context.mouseButton == 0 && this.keyframes.area.isInside(context))
        {
            if (context.mouseX > this.keyframes.area.x + this.keyframes.getLabelWidth())
            {
                return false;
            }

            return this.clickSheets(context);
        }

        return false;
    }

    private boolean clickSheets(UIContext context)
    {
        int labelWidth = this.keyframes.getLabelWidth();

        for (Map.Entry<UIKeyframeSheet.Section, Integer> entry : this.sectionYCache.entrySet())
        {
            int sectionY = this.getDopeSheetY() + entry.getValue();

            if (context.mouseY >= sectionY && context.mouseY < sectionY + (int) this.trackHeight)
            {
                UIKeyframeSheet.Section section = entry.getKey();
                boolean expanded = !this.folds.isExpanded(section.id());
                this.folds.set(section.id(), expanded);

                for (UIKeyframeSheet sheet : this.sheets)
                {
                    if (section.equals(sheet.section) && !expanded)
                    {
                        sheet.selection.clear();
                    }
                }

                this.updateScrollSize();
                this.pickSelected();
                return true;
            }
        }

        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (!this.isVisible(sheet))
            {
                continue;
            }

            int height = this.getTrackHeight(sheet);
            int y = this.getDopeSheetY(sheet);

            if (context.mouseY >= y && context.mouseY < y + height)
            {
                if (this.hasChildren(sheet) && this.isFoldToggleHit(context, sheet, y, labelWidth))
                {
                    this.toggleFold(sheet, Window.isShiftPressed());

                    return true;
                }

                this.addKeyframeManually(sheet, this.keyframes.getTick(), null);

                return true;
            }
        }

        return false;
    }

    /**
     * Fold or unfold a row. With shift the whole branch below it goes too — a skeleton is nested as
     * deep as the model is, and opening a hand one joint at a time is not what anyone means by
     * "show me the fingers".
     */
    private void toggleFold(UIKeyframeSheet sheet, boolean branch)
    {
        boolean unfold = !this.isUnfolded(sheet);

        this.setFolded(sheet, unfold, branch);
        this.updateScrollSize();
    }

    private void setFolded(UIKeyframeSheet sheet, boolean unfold, boolean branch)
    {
        this.folds.set(sheet.id, unfold);

        if (!unfold)
        {
            /* Rows that just went out of sight must not keep keyframes selected — a selection nobody
             * can see still answers to every edit. */
            sheet.selection.clear();
        }

        if (!branch)
        {
            if (!unfold)
            {
                clearSelectionBelow(sheet);
            }

            return;
        }

        for (UIKeyframeSheet child : sheet.children)
        {
            this.setFolded(child, unfold, true);
        }
    }

    private static void clearSelectionBelow(UIKeyframeSheet sheet)
    {
        for (UIKeyframeSheet child : sheet.children)
        {
            child.selection.clear();

            clearSelectionBelow(child);
        }
    }

    @Override
    public void mouseReleased(UIContext context)
    {
        this.dopeSheet.mouseReleased(context);
    }

    @Override
    public void mouseScrolled(UIContext context)
    {
        if (context.mouseWheelHorizontal != 0)
        {
            this.keyframes.panTime(context.mouseWheelHorizontal);
        }
        else if (Window.isShiftPressed() && !Window.isCtrlPressed())
        {
            this.dopeSheet.mouseScroll(context);
        }
        else if (Window.isAltPressed() && context.mouseWheel != 0D)
        {
            if (this.getSelected() != null)
            {
                float delta = (float) (context.mouseWheel * 1F);
                this.moveSelectedBy(delta, true);
            }
            else
            {
                this.setTrackHeight(this.trackHeight - context.mouseWheel);
            }
        }
        else if (context.mouseWheel != 0D)
        {
            this.keyframes.zoomTimeAt(context, context.mouseWheel);
        }
    }

    @Override
    public void handleMouse(UIContext context, int lastX, int lastY)
    {
        this.dopeSheet.drag(context);

        if (this.keyframes.isNavigating())
        {
            this.keyframes.dragTimeBy(context.mouseX - lastX);
            this.dopeSheet.scrollBy(-(context.mouseY - lastY));
        }
    }

    @Override
    public void dragKeyframes(UIContext context, Pair<Keyframe, KeyframeType> type, int originalX, int originalY, float originalT, Object originalV)
    {
        float offset = (float) (this.keyframes.fromGraphX(originalX) - originalT);
        float tick = (float) this.keyframes.fromGraphX(context.mouseX) - offset;

        if (this.keyframes.isSnappingToTicks())
        {
            tick = Math.round(this.keyframes.fromGraphX(context.mouseX) - offset);
        }

        this.setTick(tick, false);
        this.keyframes.triggerChange();
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        this.renderGrid(context);
        this.renderGraph(context);
        this.renderTimelineGrid(context);
        this.renderPreviewHints(context);
        this.renderEmptyState(context);
    }

    /**
     * Say why the sheet is blank. Only the owner knows that - an empty curve clip is empty because it
     * has no channels, while the film timeline is empty because the track filter hid every last row.
     */
    public void setEmptyState(IKey label, IKey hint)
    {
        this.emptyLabel = label;
        this.emptyHint = hint;
    }

    private void renderEmptyState(UIContext context)
    {
        if (this.emptyLabel == null || !this.sheets.isEmpty())
        {
            return;
        }

        Area area = this.keyframes.graphArea;
        FontRenderer font = context.batcher.getFont();
        int w = (int) (area.w / 1.5F);
        int x = area.mx() - w / 2;
        int y = area.my() - font.getHeight();

        context.batcher.wallText(this.emptyLabel.get(), x, y, Colors.setA(Colors.WHITE, 0.5F), w, 12, 0.5F, 0F, true);

        if (this.emptyHint != null)
        {
            context.batcher.wallText(this.emptyHint.get(), x, y + font.getHeight() + 6, Colors.setA(Colors.WHITE, 0.25F), w, 12, 0.5F, 0F, true);
        }
    }

    /**
     * Render the ruler's vertical lines over the tracks at full height (opt-in setting).
     */
    private void renderTimelineGrid(UIContext context)
    {
        if (!BBSSettings.editorTimelineGrid.get())
        {
            return;
        }

        Area area = this.keyframes.graphArea;
        int ht = (int) this.keyframes.fromGraphX(area.x);

        TimelineRulerRenderer.renderGrid(
            context,
            area,
            TimelineRulerRenderer.getRulerBottom(area),
            Math.max(ht, 0),
            this.keyframes.getDuration(),
            this.keyframes::toGraphX,
            TimeUtils::formatTime
        );
    }

    /**
     * Render grid that allows easier to see where are specific ticks
     */
    protected void renderGrid(UIContext context)
    {
        Area area = this.keyframes.graphArea;
        int ht = (int) this.keyframes.fromGraphX(area.x);
        int duration = this.keyframes.getDuration();

        TimelineRulerRenderer.render(
            context,
            area,
            Math.max(ht, 0),
            duration,
            this.keyframes::toGraphX,
            TimeUtils::formatTime,
            this.keyframes::renderRuler
        );

    }

    private void renderPreviewHints(UIContext context)
    {
        Area area = this.keyframes.graphArea;

        if (!area.isInside(context))
        {
            return;
        }

        if (this.keyframes.isStacking())
        {
            List<UIKeyframeSheet> sheets = new ArrayList<>();
            float currentTick = (float) this.keyframes.fromGraphX(context.mouseX);

            for (UIKeyframeSheet sheet : this.getInteractiveSheets())
            {
                if (sheet.selection.hasAny())
                {
                    sheets.add(sheet);
                }
            }

            for (UIKeyframeSheet current : sheets)
            {
                List<Keyframe> selected = current.selection.getSelected();
                float mmin = Integer.MAX_VALUE;
                float mmax = Integer.MIN_VALUE;

                for (Keyframe keyframe : selected)
                {
                    mmin = Math.min(keyframe.getTick(), mmin);
                    mmax = Math.max(keyframe.getTick(), mmax);
                }

                float length = mmax - mmin + this.keyframes.getStackOffset();
                int times = (int) Math.max(1, Math.ceil((currentTick - mmax) / length));
                float x = 0;

                for (int i = 0; i < times; i++)
                {
                    for (Keyframe keyframe : selected)
                    {
                        float tick = mmax + this.keyframes.getStackOffset() + (keyframe.getTick() - mmin) + x;

                        this.renderPreviewKeyframe(context, current, tick, Colors.YELLOW);
                    }

                    x += length;
                }
            }
        }
        else if (Window.isCtrlPressed() && !this.keyframes.isDuplicatingAtPlayhead())
        {
            UIKeyframeSheet sheet = this.getSheet(context.mouseY);

            if (sheet != null)
            {
                float tick = this.keyframes.getCreationTick(context);

                this.renderPreviewKeyframe(context, sheet, tick, Colors.WHITE);
            }
        }
        else if (Window.isAltPressed() && !Window.isShiftPressed())
        {
            List<UIKeyframeSheet> sheets = new ArrayList<>();
            boolean atPlayhead = this.keyframes.isDuplicatingAtPlayhead();
            float tick = this.keyframes.getDuplicationTick(context);

            for (UIKeyframeSheet sheet : atPlayhead ? this.getSheets() : this.getInteractiveSheets())
            {
                if (sheet.selection.hasAny())
                {
                    sheets.add(sheet);
                }
            }

            if (sheets.size() == 1 && !atPlayhead)
            {
                UIKeyframeSheet current = sheets.get(0);
                UIKeyframeSheet hovered = this.getSheet(context.mouseY);

                if (hovered == null || current.channel.getFactory() != hovered.channel.getFactory())
                {
                    return;
                }

                List<Keyframe> selected = current.selection.getSelected();

                for (int i = 0; i < selected.size(); i++)
                {
                    Keyframe first = selected.get(0);
                    Keyframe keyframe = selected.get(i);

                    this.renderPreviewKeyframe(context, hovered, tick + (keyframe.getTick() - first.getTick()), Colors.YELLOW);
                }
            }
            else
            {
                float min = Float.MAX_VALUE;

                for (UIKeyframeSheet sheet : sheets)
                {
                    List<Keyframe> selected = sheet.selection.getSelected();

                    for (Keyframe keyframe : selected)
                    {
                        min = Math.min(min, keyframe.getTick());
                    }
                }

                for (UIKeyframeSheet sheet : sheets)
                {
                    if (!this.isVisible(sheet)) continue;
                    List<Keyframe> selected = sheet.selection.getSelected();

                    for (int i = 0; i < selected.size(); i++)
                    {
                        Keyframe keyframe = selected.get(i);

                        this.renderPreviewKeyframe(context, sheet, tick + (keyframe.getTick() - min), Colors.YELLOW);
                    }
                }
            }
        }
    }

    private void renderPreviewKeyframe(UIContext context, UIKeyframeSheet sheet, double tick, int color)
    {
        int x = this.keyframes.toGraphX(tick);
        int y = this.getDopeSheetY(sheet) + this.getTrackHeight(sheet) / 2;
        float a = (float) Math.sin(context.getTickTransition() / 2D) * 0.1F + 0.5F;
        int r = 4;

        context.batcher.box(x - r, y - r, x + r, y + r, Colors.setA(color, a));
    }

    /**
     * Render the graph
     */
    @SuppressWarnings({"rawtypes", "IntegerDivisionInFloatingPointContext"})
    protected void renderGraph(UIContext context)
    {
        if (this.sheets.isEmpty())
        {
            return;
        }

        /* No recomputing row offsets per frame: every mutation that can change them
         * (addSheet, removeAllSheets, setExpanded, fold toggles, setTrackHeight) already
         * calls updateScrollSize() itself, and resize() re-clamps the scroll. */

        Area area = this.keyframes.graphArea;
        int rulerBottom = TimelineRulerRenderer.getRulerBottom(area);
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

        context.batcher.clipBox(area.x, rulerBottom, area.ex(), area.ey(), context);
        this.renderSheets(context, builder, matrix, area);
        this.renderOutOfRangeShading(context, builder, matrix, area);
        context.batcher.unclip(context);
    }

    /**
     * Paint over the tracks before the first tick and after the last one.
     *
     * <p>The rows run the full width of the view, so the field outside the film is what is left
     * once they are covered back up — and it drops to the floor of the tonal ladder, below every
     * surface a keyframe can sit on.</p>
     */
    private void renderOutOfRangeShading(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area)
    {
        int timelineBottom = TimelineRulerRenderer.getTimelineBottom(area);
        int contentY = Math.min(area.ey(), timelineBottom);

        if (contentY >= area.ey())
        {
            return;
        }

        int startX = this.keyframes.toGraphX(0);
        if (startX > area.x)
        {
            int leftEx = Math.min(startX, area.ex());

            context.batcher.box(area.x, contentY, leftEx, area.ey(), BBSSettings.sunkenSurface());
        }

        int endX = this.keyframes.toGraphX(this.keyframes.getDuration());
        if (endX < area.ex())
        {
            int rightX = Math.max(endX, area.x);

            context.batcher.box(rightX, contentY, area.ex(), area.ey(), BBSSettings.sunkenSurface());
        }
    }

    private void renderLabels(UIContext context)
    {
        Area area = this.keyframes.area;
        int w = this.keyframes.getLabelWidth();

        context.batcher.clipBox(area.x, area.y, area.x + w, area.ey(), context);

        for (Map.Entry<UIKeyframeSheet.Section, Integer> entry : this.sectionYCache.entrySet())
        {
            UIKeyframeSheet.Section section = entry.getKey();
            int sy = this.getDopeSheetY() + entry.getValue();
            int height = (int) this.trackHeight;

            if (sy + height < area.y || sy > area.ey())
            {
                continue;
            }

            boolean hover = area.isInside(context) && context.mouseY >= sy && context.mouseY < sy + height;
            RowStyle.row(context.batcher, area.x, sy, w, height, section.color(), true, hover, false);
            Icon icon = section.icon();
            boolean hasIcon = icon != null && height >= 12;
            int iconX = area.x + w - LABEL_RIGHT_PAD - LABEL_ICON_SIZE;
            this.renderFoldArrow(context, this.getArrowX(area.x, w, hasIcon) + LABEL_ARROW_SIZE / 2F,
                sy + height / 2, this.folds.isExpanded(section.id()));

            if (hasIcon)
            {
                context.batcher.icon(icon, iconX, sy + (height - icon.h) / 2);
            }

            if (w > LABEL_COMPACT_WIDTH)
            {
                FontRenderer font = context.batcher.getFont();
                int right = (hasIcon ? iconX - LABEL_TEXT_ICON_GAP : area.x + w - LABEL_RIGHT_PAD) - LABEL_ARROW_SIZE;
                String title = font.limitToWidth(section.title().get(), Math.max(0, right - area.x - LABEL_TEXT_LEFT));
                context.batcher.textShadow(title, area.x + LABEL_TEXT_LEFT, sy + (height - font.getHeight()) / 2, Colors.WHITE);
            }
        }

        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (!this.isVisible(sheet))
            {
                continue;
            }

            this.renderSheetLabel(context, area, sheet, this.getDopeSheetY(sheet), w);
        }

        context.batcher.unclip(context);
    }

    private void renderSheetLabel(UIContext context, Area area, UIKeyframeSheet sheet, int y, int w)
    {
        int height = this.getTrackHeight(sheet);

        if (y + height < area.y || y > area.ey())
        {
            return;
        }

        /* Hover: whole row (label + track area) */
        boolean hover = area.isInside(context) && context.mouseY >= y && context.mouseY < y + height;
        int my = y + height / 2;
        int lx = area.x;

        RowStyle.row(context.batcher, lx, y, w, height, sheet.color, false, hover, false);

        /* A row that has children keeps its own icon and gets a fold arrow next to it. */
        Icon icon = sheet.getIcon();
        boolean hasIcon = icon != null && height >= 12D;
        boolean foldable = this.hasChildren(sheet);

        int iconX = lx + w - LABEL_RIGHT_PAD - LABEL_ICON_SIZE;

        if (foldable)
        {
            this.renderFoldArrow(context, this.getArrowX(lx, w, hasIcon) + LABEL_ARROW_SIZE / 2F, my, this.isUnfolded(sheet));
        }

        /* Hide every title together when the column is reduced to its icon controls. */
        if (w > LABEL_COMPACT_WIDTH)
        {
            FontRenderer font = context.batcher.getFont();
            int textColor = hover ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.75F);
            int textX = lx + LABEL_TEXT_LEFT + this.getSheetIndent(sheet);
            int textRight = hasIcon ? iconX - LABEL_TEXT_ICON_GAP : lx + w - LABEL_RIGHT_PAD;

            if (foldable)
            {
                textRight -= LABEL_ARROW_SIZE;
            }

            String title = font.limitToWidth(sheet.title.get(), Math.max(0, textRight - textX));

            context.batcher.textShadow(title, textX, my - font.getHeight() / 2, textColor);
        }

        if (hasIcon)
        {
            context.batcher.icon(icon, iconX, my - icon.h / 2);
        }
    }

    private void renderSheets(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area)
    {
        if (area.isInside(context))
        {
            for (Map.Entry<UIKeyframeSheet.Section, Integer> entry : this.sectionYCache.entrySet())
            {
                int y = this.getDopeSheetY() + entry.getValue();
                int height = (int) this.trackHeight;

                if (context.mouseY >= y && context.mouseY < y + height)
                {
                    context.batcher.box(area.x, y, area.ex(), y + height,
                        Colors.setA(entry.getKey().color(), 0.12F));
                    break;
                }
            }
        }

        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (!this.isVisible(sheet))
            {
                continue;
            }

            this.renderSheet(context, builder, matrix, area, sheet, this.getDopeSheetY(sheet));
        }
    }

    private void renderSheet(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int y)
    {
        if (!this.isVisible(sheet))
        {
            return;
        }

        int height = this.getTrackHeight(sheet);

        if (y + height < area.y || y > area.ey())
        {
            return;
        }

        List keyframes = sheet.channel.getKeyframes();

        boolean hover = area.isInside(context) && context.mouseY >= y && context.mouseY < y + height;
        int my = y + height / 2;
        int bh = Math.max(2, height);
        int row = this.sheetRowCache.getOrDefault(sheet, 0);

        int trackWidth = BBSSettings.editorTrackWidth.get();

        int surface = row % 2 == 0 ? BBSSettings.deepSurface() : BBSSettings.baseSurface();

        context.batcher.box(area.x, y, area.ex(), y + bh, surface);

        if (hover)
        {
            context.batcher.box(area.x, y, area.ex(), y + bh, Colors.setA(sheet.color, 0.12F));
        }

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        /* Keyframes sorted by tick map to ascending X, so everything left of the view is
         * skipped and the first frame past the right edge ends the loop. The margin covers a
         * shape's visual radius, so a keyframe half over the edge still draws whole. */
        int cullMinX = area.x - CULL_MARGIN;
        int cullMaxX = area.ex() + CULL_MARGIN;

        /* Render bars indicating same values */
        for (int j = 1; j < keyframes.size(); j++)
        {
            Keyframe previous = (Keyframe) keyframes.get(j - 1);
            Keyframe frame = (Keyframe) keyframes.get(j);
            int c = Colors.setA(sheet.color, TRACK_BAR_ALPHA);
            int xx = this.keyframes.toGraphX(previous.getTick());
            int xxx = this.keyframes.toGraphX(frame.getTick());

            if (xxx < cullMinX)
            {
                continue;
            }

            if (xx > cullMaxX)
            {
                break;
            }

            if (previous.getFactory().compare(previous.getValue(), frame.getValue()))
            {
                int w = trackWidth + 2;

                context.batcher.fillRect(builder, matrix, xx, my - w / 2, this.keyframes.toGraphX(frame.getTick()) - xx, w, c, c, c, c);
            }

            if (Math.abs(xxx - xx) < 5)
            {
                c = Colors.setA(sheet.color, 0.5F);

                context.batcher.fillRect(builder, matrix, xx - 2, my + trackWidth / 2 + 4, xxx - xx + 4, 2, c, c, c, c);
            }
        }

        this.renderMotionShiftBars(context, builder, matrix, area, sheet, my, trackWidth + 2);

        /* Render custom duration markers. The keyframe shapes themselves belong to the topmost
         * pass ({@link #renderSheetKeyframeShapes}), which draws over the out-of-range shading. */
        int forcedIndex = 0;

        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe frame = (Keyframe) keyframes.get(j);
            float tick = frame.getTick();
            int x1 = this.keyframes.toGraphX(tick);
            int x2 = this.keyframes.toGraphX(tick + frame.getDuration());

            if (x1 > cullMaxX)
            {
                break;
            }

            if (x1 == x2)
            {
                continue;
            }

            /* Keep the duration markers' zigzag parity stable as offscreen ones scroll out. */
            if (Math.max(x1, x2) >= cullMinX)
            {
                int y1 = my - 8 + (forcedIndex % 2 == 1 ? -4 : 0);
                int color = sheet.selection.has(j) ? Colors.WHITE :  Colors.setA(Colors.mulRGB(sheet.color, 0.9F), 0.75F);

                context.batcher.fillRect(builder, matrix, x1, y1 - 2, 1, 5, color, color, color, color);
                context.batcher.fillRect(builder, matrix, x2, y1 - 2, 1, 5, color, color, color, color);
                context.batcher.fillRect(builder, matrix, x1 + 1, y1, x2 - x1, 1, color, color, color, color);
            }

            forcedIndex += 1;
        }

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /** Uniform strip from the midpoint handle to the slow-side key; opacity follows shift intensity. */
    private void renderMotionShiftBars(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int y, int width)
    {
        boolean shifted = false;
        for (Object entry : sheet.channel.getKeyframes())
        {
            Keyframe<?> key = (Keyframe<?>) entry;
            if (key.getMotionShift() != 0F && key.supportsMotionShift())
            {
                shifted = true;
                break;
            }
        }
        if (!shifted) return;

        Keyframe<?> previousA = null, previousB = null;
        boolean same = false;
        /* Sample playback in screen space: finite loops use their source shift too, and work
         * stays bounded by the visible width rather than the film's duration or zoom. */
        for (int x = area.x; x < area.ex(); x += 2)
        {
            int end = Math.min(x + 2, area.ex());
            float tick = (float) this.keyframes.fromGraphX((x + end) / 2);
            KeyframeSegment<?> segment = sheet.channel.find(tick);
            if (segment == null || segment.isSame() || segment.a.getMotionShift() == 0F || !segment.a.supportsMotionShift()) continue;
            if (segment.a != previousA || segment.b != previousB)
            {
                previousA = segment.a;
                previousB = segment.b;
                same = segment.a.getFactory().compare(segment.a.getValue(), segment.b.getValue());
            }
            /* Equal values already have their hold strip; don't make it darker by drawing twice. */
            if (same) continue;

            float shift = segment.a.getMotionShift();
            float start = segment.a.getTick() + segment.timeOffset;
            float finish = segment.b.getTick() + segment.timeOffset;
            float midpoint = start + segment.duration * (0.5F + shift);
            int left = Math.max(x, this.keyframes.toGraphX(shift > 0F ? start : midpoint));
            int right = Math.min(end, this.keyframes.toGraphX(shift > 0F ? Math.min(midpoint, finish) : finish));
            if (right <= left) continue;
            float alpha = TRACK_BAR_ALPHA * Math.min(1F, Math.abs(shift) / 0.49F);
            if (alpha < 1F / 255F) continue;
            int color = Colors.setA(sheet.color, alpha);
            context.batcher.fillRect(builder, matrix, left, y - width / 2, right - left, width, color, color, color, color);
        }
    }

    private void renderSheetKeyframeShapes(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int y)
    {
        if (!this.isVisible(sheet))
        {
            return;
        }

        int height = this.getTrackHeight(sheet);

        if (y + height < area.y || y > area.ey())
        {
            return;
        }

        List keyframes = sheet.channel.getKeyframes();
        int my = y + height / 2;
        int forcedIndex = 0;
        int cullMinX = area.x - CULL_MARGIN;
        int cullMaxX = area.ex() + CULL_MARGIN;

        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe frame = (Keyframe) keyframes.get(j);
            float tick = frame.getTick();
            int x1 = this.keyframes.toGraphX(tick);
            int x2 = this.keyframes.toGraphX(tick + frame.getDuration());

            if (x1 > cullMaxX)
            {
                break;
            }

            if (Math.max(x1, x2) < cullMinX)
            {
                continue;
            }

            if (x1 != x2)
            {
                forcedIndex += 1;
            }

            boolean isPointHover = this.isNear(x1, my, context.mouseX, context.mouseY, Window.isAltPressed() && Window.isShiftPressed());
            boolean toRemove = this.keyframes.isRemovingKeyframe() && isPointHover;

            if (this.keyframes.isSelecting())
            {
                isPointHover = isPointHover || this.keyframes.getGrabbingArea(context).isInside(x1, my);
            }

            int kc = keyframeColor(frame, sheet);
            int c = (sheet.selection.has(j) || isPointHover ? Colors.WHITE : kc) | Colors.A100;

            if (toRemove)
            {
                c = Colors.RED | Colors.A100;
            }

            int pointOffset = toRemove ? 4 : 3;

            renderShape(frame, context, builder, matrix, x1, my, pointOffset, c);
        }

        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe frame = (Keyframe) keyframes.get(j);
            int mx = this.keyframes.toGraphX(frame.getTick());

            if (mx < cullMinX)
            {
                continue;
            }

            if (mx > cullMaxX)
            {
                break;
            }

            int mc = keyframeCoreColor(frame, sheet, sheet.selection.has(j));
            IKeyframeShapeRenderer shapeResult = renderShape(frame, context, builder, matrix, mx, my, 2, mc);

            shapeResult.renderKeyframeBackground(context, builder, matrix, mx, my, 2, mc);
        }
    }

    private void renderSheetsTopmostKeyframes(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area)
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (!this.isVisible(sheet))
            {
                continue;
            }

            this.renderSheetKeyframeShapes(context, builder, matrix, area, sheet, this.getDopeSheetY(sheet));
        }
    }

    private void renderSectionKeyframes(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area)
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            Integer offset = this.sectionYCache.get(sheet.section);

            if (offset == null)
            {
                continue;
            }

            int y = this.getDopeSheetY() + offset + (int) this.trackHeight / 2;

            if (y + 3 < area.y || y - 3 > area.ey())
            {
                continue;
            }

            for (int i = 0; i < sheet.channel.getKeyframes().size(); i++)
            {
                Keyframe frame = (Keyframe) sheet.channel.getKeyframes().get(i);
                int x = this.keyframes.toGraphX(frame.getTick());

                if (x < area.x - CULL_MARGIN) continue;
                if (x > area.ex() + CULL_MARGIN) break;

                int color = sheet.section.color() | Colors.A100;
                context.batcher.fillRect(builder, matrix, x - 3, y - 3, 6, 6, color, color, color, color);
            }
        }
    }

    @Override
    public void renderTopmostKeyframes(UIContext context)
    {
        if (this.sheets.isEmpty())
        {
            return;
        }

        Area area = this.keyframes.graphArea;
        int rulerBottom = TimelineRulerRenderer.getRulerBottom(area);
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

        context.batcher.clipBox(area.x, rulerBottom, area.ex(), area.ey(), context);
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        this.renderSectionKeyframes(context, builder, matrix, area);
        this.renderSheetsTopmostKeyframes(context, builder, matrix, area);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        context.batcher.unclip(context);
    }

    /** Left edge of the fold arrow's slot: just left of the icon, or where the icon would have been. */
    private int getArrowX(int labelX, int labelWidth, boolean hasIcon)
    {
        int iconX = labelX + labelWidth - LABEL_RIGHT_PAD - LABEL_ICON_SIZE;

        return hasIcon ? iconX - LABEL_ARROW_SIZE : iconX + (LABEL_ICON_SIZE - LABEL_ARROW_SIZE) / 2;
    }

    /** {@link Icons#ARROW_SMALL}, turned down when the row is unfolded — the same arrow the collapsible sections use. */
    private void renderFoldArrow(UIContext context, float cx, float cy, boolean unfolded)
    {
        MatrixStack matrices = context.batcher.getContext().getMatrices();

        matrices.push();
        matrices.translate(cx, cy, 0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(unfolded ? 90F : 0F), 0F, 0F, 0F);
        context.batcher.icon(Icons.ARROW_SMALL, Colors.WHITE, 0, 0, 0.5F, 0.5F);
        matrices.pop();
    }

    /**
     * Whether a click on this row lands on its fold toggle: the arrow, and the icon next to it. The
     * two sit together at the right end of the name column and read as one control, so hitting either
     * folds the row — the arrow alone is a 10px target, which is a lot to ask of a mouse.
     */
    private boolean isFoldToggleHit(UIContext context, UIKeyframeSheet sheet, int y, int labelWidth)
    {
        int height = this.getTrackHeight(sheet);
        boolean hasIcon = sheet.getIcon() != null && height >= 12D;
        int x = this.getArrowX(this.keyframes.area.x, labelWidth, hasIcon);
        int right = this.keyframes.area.x + labelWidth - LABEL_RIGHT_PAD;

        return context.mouseX >= x && context.mouseX < right
            && context.mouseY >= y && context.mouseY < y + height;
    }

    @Override
    public void postRender(UIContext context)
    {
        if (!this.sheets.isEmpty())
        {
            this.renderLabels(context);
        }

        this.dopeSheet.renderScrollbar(context.batcher);
    }

    /* State recovery */

    @Override
    public void saveState(MapType extra)
    {
        extra.putDouble("track_height", this.trackHeight);
        extra.putDouble("scroll", this.dopeSheet.getScroll());
    }

    @Override
    public void restoreState(MapType extra)
    {
        this.setTrackHeight(extra.getDouble("track_height"));
        this.dopeSheet.setScroll(extra.getDouble("scroll"));
    }
}
