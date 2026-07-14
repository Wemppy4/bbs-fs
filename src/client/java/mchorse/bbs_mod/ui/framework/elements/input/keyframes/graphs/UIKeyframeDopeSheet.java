package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeGroup;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

public class UIKeyframeDopeSheet implements IUIKeyframeGraph
{
    private static final int POSE_TAB_BASE_INDENT = 4;
    private static final int POSE_TAB_DEPTH_STEP = 4;
    private static final float TRACK_BAR_ALPHA = 0.3F;

    /** Track-name column layout: left text indent, right padding, right-side icon slot, text/icon gap. */
    private static final int LABEL_TEXT_LEFT = 5;
    private static final int LABEL_RIGHT_PAD = 2;
    private static final int LABEL_ICON_SIZE = 16;
    private static final int LABEL_TEXT_ICON_GAP = 3;

    private UIKeyframes keyframes;

    private List<UIKeyframeElement> elements = new ArrayList<>();
    private List<UIKeyframeSheet> sheets = new ArrayList<>();
    private Map<UIKeyframeSheet, Integer> sheetYCache = new HashMap<>();
    private UIKeyframeSheet lastSheet;
    private Map<UIKeyframeSheet, UIKeyframeSheet> poseTabRoots = new HashMap<>();
    private Map<UIKeyframeSheet, Integer> poseTabDepths = new HashMap<>();
    private Set<UIKeyframeSheet> poseTabParents = new HashSet<>();
    private Set<UIKeyframeSheet> expandedPoseTabs = new HashSet<>();

    private Scroll dopeSheet;
    private double trackHeight;

    public static IKeyframeShapeRenderer renderShape(Keyframe frame, UIContext context, BufferBuilder builder, Matrix4f matrix, int x, int y, int offset, int c)
    {
        KeyframeShape keyframeShape = frame.getShape();
        IKeyframeShapeRenderer shape = KeyframeShapeRenderers.SHAPES.get(keyframeShape);

        shape.renderKeyframe(context, builder, matrix, x, y, offset, c);

        return shape;
    }

    public UIKeyframeDopeSheet(UIKeyframes keyframes)
    {
        this.keyframes = keyframes;
        this.dopeSheet = new Scroll(this.keyframes.area);
        this.dopeSheet.smoothScrolling(() -> !BBSSettings.scrollingDisableSmoothnessInEditors.get());
        this.dopeSheet.wheelScrollStep(() -> (int) this.trackHeight);

        this.setTrackHeight(16);
    }

    public double getTrackHeight()
    {
        return this.trackHeight;
    }

    public void setTrackHeight(double height)
    {
        this.trackHeight = MathUtils.clamp(height, 8D, 100D);
        this.updateScrollSize();

        this.dopeSheet.clamp();
    }

    private void updateScrollSize()
    {
        this.sheetYCache.clear();
        this.dopeSheet.scrollSize = this.calculateLayout(this.elements, 0) + TOP_MARGIN;
    }

    private int calculateLayout(List<UIKeyframeElement> elements, int y)
    {
        for (UIKeyframeElement element : elements)
        {
            if (element instanceof UIKeyframeSheet sheet)
            {
                if (!this.isVisible(sheet))
                {
                    continue;
                }

                this.sheetYCache.put(sheet, y);
            }

            y += (int) this.trackHeight;

            if (element instanceof UIKeyframeGroup group && !group.collapsed)
            {
                y = this.calculateLayout(group.children, y);
            }
        }

        return y;
    }

    private int getElementHeight(UIKeyframeElement element)
    {
        if (element instanceof UIKeyframeSheet sheet && !this.isVisible(sheet))
        {
            return 0;
        }

        if (element instanceof UIKeyframeGroup group)
        {
            int h = (int) this.trackHeight;

            if (!group.collapsed)
            {
                for (UIKeyframeElement child : group.children)
                {
                    h += this.getElementHeight(child);
                }
            }

            return h;
        }

        return (int) this.trackHeight;
    }

    private boolean isVisible(UIKeyframeSheet sheet)
    {
        UIKeyframeSheet root = this.poseTabRoots.get(sheet);

        return root == null || this.expandedPoseTabs.contains(root);
    }

    private int getSheetIndent(UIKeyframeSheet sheet)
    {
        if (!this.poseTabRoots.containsKey(sheet))
        {
            return 0;
        }

        int depth = Math.max(0, this.poseTabDepths.getOrDefault(sheet, 0));
        int labelWidth = Math.max(1, this.keyframes.getLabelWidth());
        float scale = MathUtils.clamp(labelWidth / 120F, 0.75F, 1.5F);
        int baseIndent = Math.max(1, Math.round(POSE_TAB_BASE_INDENT * scale));
        int depthStep = Math.max(1, Math.round(POSE_TAB_DEPTH_STEP * scale));

        return baseIndent + depth * depthStep;
    }

    private boolean isPoseTabParent(UIKeyframeSheet sheet)
    {
        return this.poseTabParents.contains(sheet);
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

    public void configurePoseTabs(Map<UIKeyframeSheet, List<UIKeyframeSheet>> tabs, Map<UIKeyframeSheet, Integer> depths, Set<String> expandedPoseIds)
    {
        this.poseTabRoots.clear();
        this.poseTabDepths.clear();
        this.poseTabParents.clear();
        this.expandedPoseTabs.clear();

        for (Map.Entry<UIKeyframeSheet, List<UIKeyframeSheet>> entry : tabs.entrySet())
        {
            UIKeyframeSheet parent = entry.getKey();

            this.poseTabParents.add(parent);

            if (expandedPoseIds.contains(parent.id))
            {
                this.expandedPoseTabs.add(parent);
            }

            for (UIKeyframeSheet child : entry.getValue())
            {
                this.poseTabRoots.put(child, parent);
                this.poseTabDepths.put(child, Math.max(0, depths.getOrDefault(child, 0)));
            }
        }

        this.updateScrollSize();
    }

    public Set<String> getExpandedPoseTabIds()
    {
        Set<String> expandedIds = new HashSet<>();

        for (UIKeyframeSheet sheet : this.expandedPoseTabs)
        {
            expandedIds.add(sheet.id);
        }

        return expandedIds;
    }

    public void removeAllSheets()
    {
        this.elements.clear();
        this.sheets.clear();
        this.poseTabRoots.clear();
        this.poseTabDepths.clear();
        this.poseTabParents.clear();
        this.expandedPoseTabs.clear();
        this.updateScrollSize();
    }

    public void addSheet(UIKeyframeSheet sheet)
    {
        this.elements.add(sheet);
        this.sheets.add(sheet);
        this.updateScrollSize();
    }

    public void addElement(UIKeyframeElement element)
    {
        this.elements.add(element);
        this.flatten(element);
        this.updateScrollSize();
    }

    @Override
    public void clearSelection()
    {
        for (UIKeyframeSheet sheet : this.getInteractiveSheets())
        {
            sheet.selection.clear();
        }

        this.pickKeyframe(null);
    }

    @Override
    public void selectAll()
    {
        for (UIKeyframeSheet sheet : this.getInteractiveSheets())
        {
            sheet.selection.all();
        }

        this.pickSelected();
    }

    @Override
    public void selectAfter(float tick, int direction)
    {
        for (UIKeyframeSheet sheet : this.getInteractiveSheets())
        {
            sheet.selection.after(tick, direction);
        }

        this.pickSelected();
    }

    @Override
    public Keyframe getSelected()
    {
        for (UIKeyframeSheet sheet : this.getInteractiveSheets())
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
        for (UIKeyframeSheet sheet : this.getInteractiveSheets())
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
        for (UIKeyframeSheet sheet : this.getInteractiveSheets())
        {
            sheet.selection.removeSelected();
        }

        this.pickKeyframe(null);
    }

    private void flatten(UIKeyframeElement element)
    {
        if (element instanceof UIKeyframeSheet sheet)
        {
            this.sheets.add(sheet);
        }
        else if (element instanceof UIKeyframeGroup group)
        {
            for (UIKeyframeElement child : group.children)
            {
                this.flatten(child);
            }
        }
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
                int y = this.getDopeSheetY(sheet) + (int) this.trackHeight / 2;

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
                int y = this.getDopeSheetY(sheet) + (int) this.trackHeight / 2;

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

            if (relY >= y && relY < y + this.trackHeight)
            {
                return entry.getKey();
            }
        }

        return null;
    }

    @Override
    public boolean addKeyframe(int mouseX, int mouseY)
    {
        float tick = (float) this.keyframes.fromGraphX(mouseX);
        UIKeyframeSheet sheet = this.getSheet(mouseY);

        if (!Window.isShiftPressed())
        {
            tick = Math.round(tick);
        }

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
            int y = this.getDopeSheetY(sheet) + (int) this.trackHeight / 2;

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
    public void pickKeyframe(Keyframe keyframe)
    {
        this.keyframes.pickKeyframe(keyframe);
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

            int y = this.getDopeSheetY();

            return this.clickElements(context, this.elements, 0, y);
        }

        return false;
    }

    private boolean clickElements(UIContext context, List<UIKeyframeElement> elements, int offset, int y)
    {
        int labelWidth = this.keyframes.getLabelWidth();

        for (UIKeyframeElement element : elements)
        {
            if (element instanceof UIKeyframeGroup group)
            {
                if (context.mouseY >= y && context.mouseY < y + this.trackHeight)
                {
                    group.collapsed = !group.collapsed;
                    this.updateScrollSize();

                    return true;
                }

                if (!group.collapsed)
                {
                    if (this.clickElements(context, group.children, offset + 10, y + (int) this.trackHeight))
                    {
                        return true;
                    }
                }
            }
            else if (element instanceof UIKeyframeSheet sheet)
            {
                if (!this.isVisible(sheet))
                {
                    continue;
                }

                if (context.mouseY >= y && context.mouseY < y + this.trackHeight)
                {
                    if (this.isPoseTabParent(sheet) && this.isPoseTabArrowHit(context, y, labelWidth))
                    {
                        this.togglePoseTab(sheet);
                        this.updateScrollSize();

                        return true;
                    }

                    this.addKeyframeManually(sheet, this.keyframes.getTick(), null);

                    return true;
                }
            }

            y += this.getElementHeight(element);
        }

        return false;
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
            double offsetX = (25F * BBSSettings.scrollingSensitivityHorizontal.get() * context.mouseWheelHorizontal) / this.keyframes.getXAxis().getZoom();

            this.keyframes.getXAxis().setShift(this.keyframes.getXAxis().getShift() - offsetX);
        }
        else if (Window.isShiftPressed())
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
            this.keyframes.getXAxis().zoomAnchor(Scale.getAnchorX(context, this.keyframes.graphArea), Math.copySign(this.keyframes.getXAxis().getZoomFactor(), context.mouseWheel));
        }
    }

    @Override
    public void handleMouse(UIContext context, int lastX, int lastY)
    {
        this.dopeSheet.drag(context);

        if (this.keyframes.isNavigating())
        {
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;
            double offset = (mouseX - lastX) / this.keyframes.getXAxis().getZoom();

            this.keyframes.getXAxis().setShift(this.keyframes.getXAxis().getShift() - offset);
            this.dopeSheet.scrollBy(-(mouseY - lastY));
        }
    }

    @Override
    public void dragKeyframes(UIContext context, Pair<Keyframe, KeyframeType> type, int originalX, int originalY, float originalT, Object originalV)
    {
        float offset = (float) (this.keyframes.fromGraphX(originalX) - originalT);
        float tick = (float) this.keyframes.fromGraphX(context.mouseX) - offset;

        if (!Window.isShiftPressed())
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
        else if (Window.isCtrlPressed())
        {
            UIKeyframeSheet sheet = this.getSheet(context.mouseY);

            if (sheet != null)
            {
                float tick = (float) this.keyframes.fromGraphX(context.mouseX);

                if (!Window.isShiftPressed())
                {
                    tick = Math.round(tick);
                }

                this.renderPreviewKeyframe(context, sheet, tick, Colors.WHITE);
            }
        }
        else if (Window.isAltPressed() && !Window.isShiftPressed())
        {
            List<UIKeyframeSheet> sheets = new ArrayList<>();

            for (UIKeyframeSheet sheet : this.getInteractiveSheets())
            {
                if (sheet.selection.hasAny())
                {
                    sheets.add(sheet);
                }
            }

            if (sheets.size() == 1)
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

                    this.renderPreviewKeyframe(context, hovered, Math.round(this.keyframes.fromGraphX(context.mouseX)) + (keyframe.getTick() - first.getTick()), Colors.YELLOW);
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
                    List<Keyframe> selected = sheet.selection.getSelected();

                    for (int i = 0; i < selected.size(); i++)
                    {
                        Keyframe keyframe = selected.get(i);

                        this.renderPreviewKeyframe(context, sheet, Math.round(this.keyframes.fromGraphX(context.mouseX)) + (keyframe.getTick() - min), Colors.YELLOW);
                    }
                }
            }
        }
    }

    private void renderPreviewKeyframe(UIContext context, UIKeyframeSheet sheet, double tick, int color)
    {
        int x = this.keyframes.toGraphX(tick);
        int y = this.getDopeSheetY(sheet) + (int) this.trackHeight / 2;
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
        if (this.elements.isEmpty())
        {
            return;
        }

        this.updateScrollSize();

        Area area = this.keyframes.graphArea;
        int rulerBottom = TimelineRulerRenderer.getRulerBottom(area);
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

        context.batcher.clip(area.x, rulerBottom, area.ex(), area.ey(), context);
        this.renderElements(context, builder, matrix, area, this.elements, 0, this.getDopeSheetY());
        this.renderOutOfRangeShading(context, builder, matrix, area);
        context.batcher.unclip(context);
    }

    private void renderOutOfRangeShading(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area)
    {
        int timelineBottom = TimelineRulerRenderer.getTimelineBottom(area);
        int contentY = Math.min(area.ey(), timelineBottom + 1);

        if (contentY >= area.ey())
        {
            return;
        }

        int startX = this.keyframes.toGraphX(0);
        if (startX > area.x)
        {
            int leftEx = Math.min(startX, area.ex());

            context.batcher.box(area.x, contentY, leftEx, area.ey(), BBSSettings.chromeSurface());
        }

        int endX = this.keyframes.toGraphX(this.keyframes.getDuration());
        if (endX < area.ex())
        {
            int rightX = Math.max(endX, area.x);

            context.batcher.box(rightX, contentY, area.ex(), area.ey(), BBSSettings.chromeSurface());
        }
    }

    private void renderLabels(UIContext context, BufferBuilder builder, Matrix4f matrix, List<UIKeyframeElement> elements, int offset, int y)
    {
        Area area = this.keyframes.area;
        int w = this.keyframes.getLabelWidth();

        /* Render background */
        context.batcher.box(area.x + w - 1, area.y, area.x + w, area.ey(), BBSSettings.dividerColor());

        context.batcher.clip(area.x, area.y, area.x + w, area.ey(), context);

        for (UIKeyframeElement element : elements)
        {
            if (element instanceof UIKeyframeSheet sheet)
            {
                if (this.isVisible(sheet))
                {
                    this.renderSheetLabel(context, builder, matrix, area, sheet, offset, y, w);
                }
            }
            else if (element instanceof UIKeyframeGroup group)
            {
                this.renderGroupLabel(context, builder, matrix, area, group, offset, y, w);
            }

            y += this.getElementHeight(element);

            if (element instanceof UIKeyframeGroup group && !group.collapsed)
            {
                this.renderLabels(context, builder, matrix, group.children, offset + 10, y);

                y = this.getElementHeight(group) - (int) this.trackHeight + y;
            }
        }

        context.batcher.unclip(context);
    }

    private void renderGroupLabel(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeGroup group, int offset, int y, int w)
    {
        if (y + this.trackHeight < area.y || y > area.ey())
        {
            return;
        }

        /* Hover: whole row (label + track area) */
        boolean hover = area.isInside(context) && context.mouseY >= y && context.mouseY < y + this.trackHeight;
        int my = y + (int) this.trackHeight / 2;
        int lx = area.x;

        if (hover)
        {
            context.batcher.gradientHBox(lx, y, lx + w, y + (int) this.trackHeight, Colors.setA(group.color, 0.2F), Colors.setA(group.color, 0.04F));
        }

        context.batcher.box(lx, y, lx + 3, y + (int) this.trackHeight, group.color | Colors.A100);

        int arrowX = lx + w - LABEL_RIGHT_PAD - LABEL_ICON_SIZE;
        FontRenderer font = context.batcher.getFont();
        int textColor = hover ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.75F);
        int textX = lx + LABEL_TEXT_LEFT + offset;
        String label = font.limitToWidth(group.title.get(), Math.max(0, arrowX - LABEL_TEXT_ICON_GAP - textX));

        context.batcher.textShadow(label, textX, my - font.getHeight() / 2, textColor);
        context.batcher.icon(group.collapsed ? Icons.ARROW_RIGHT : Icons.ARROW_DOWN, arrowX, my - 8);
    }

    private void renderSheetLabel(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int offset, int y, int w)
    {
        if (y + this.trackHeight < area.y || y > area.ey())
        {
            return;
        }

        /* Hover: whole row (label + track area) */
        boolean hover = area.isInside(context) && context.mouseY >= y && context.mouseY < y + this.trackHeight;
        int my = y + (int) this.trackHeight / 2;
        int lx = area.x;

        if (hover)
        {
            context.batcher.gradientHBox(lx, y, lx + w, y + (int) this.trackHeight, Colors.setA(sheet.color, 0.2F), Colors.setA(sheet.color, 0.04F));
        }

        context.batcher.box(lx, y, lx + 2, y + (int) this.trackHeight, sheet.color | Colors.A100);

        boolean poseTab = this.isPoseTabParent(sheet);
        Icon icon = poseTab
            ? (this.expandedPoseTabs.contains(sheet) ? Icons.ARROW_DOWN : Icons.ARROW_RIGHT)
            : sheet.getIcon();
        boolean hasIcon = icon != null && this.trackHeight >= 12D;

        int iconX = lx + w - LABEL_RIGHT_PAD - LABEL_ICON_SIZE;
        FontRenderer font = context.batcher.getFont();
        int textColor = hover ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.75F);
        int textX = lx + LABEL_TEXT_LEFT + offset + this.getSheetIndent(sheet);
        int textRight = hasIcon ? iconX - LABEL_TEXT_ICON_GAP : lx + w - LABEL_RIGHT_PAD;
        String title = font.limitToWidth(sheet.title.get(), Math.max(0, textRight - textX));

        context.batcher.textShadow(title, textX, my - font.getHeight() / 2, textColor);

        if (hasIcon)
        {
            context.batcher.icon(icon, iconX, my - icon.h / 2);
        }
    }

    private int renderElements(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, List<UIKeyframeElement> elements, int offset, int y)
    {
        for (UIKeyframeElement element : elements)
        {
            if (element instanceof UIKeyframeSheet sheet)
            {
                if (this.isVisible(sheet))
                {
                    this.renderSheet(context, builder, matrix, area, sheet, offset, y);
                }
            }
            else if (element instanceof UIKeyframeGroup group)
            {
                this.renderGroup(context, builder, matrix, area, group, offset, y);
            }

            y += this.getElementHeight(element);

            if (element instanceof UIKeyframeGroup group && !group.collapsed)
            {
                y = this.renderElements(context, builder, matrix, area, group.children, offset + 10, y);
            }
        }

        return y;
    }

    private int getTrackGap()
    {
        return 0;
    }

    private int getTrackBodyY(int y)
    {
        return y + this.getTrackGap();
    }

    private int getTrackBodyHeight()
    {
        int gap = this.getTrackGap();

        return Math.max(2, (int) this.trackHeight - gap * 2);
    }

    private void renderGroup(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeGroup group, int offset, int y)
    {
        if (y + this.trackHeight < area.y || y > area.ey())
        {
            return;
        }

        boolean hover = area.isInside(context) && context.mouseY >= y && context.mouseY < y + this.trackHeight;
        int by = this.getTrackBodyY(y);
        int bh = this.getTrackBodyHeight();
        int row = Math.max(0, (y - TimelineRulerRenderer.getTimelineBottom(area)) / Math.max(1, (int) this.trackHeight));
        int surface = row % 2 == 0 ? BBSSettings.deepSurface() : BBSSettings.baseSurface();

        context.batcher.box(area.x, by, area.ex(), by + bh, surface);

        if (hover)
        {
            context.batcher.box(area.x, by, area.ex(), by + bh, BBSSettings.color(BBSSettings.raisedSurface(), Colors.A25));
        }
    }

    private void renderSheet(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int offset, int y)
    {
        if (!this.isVisible(sheet))
        {
            return;
        }

        if (y + this.trackHeight < area.y || y > area.ey())
        {
            return;
        }

        List keyframes = sheet.channel.getKeyframes();

        boolean hover = area.isInside(context) && context.mouseY >= y && context.mouseY < y + this.trackHeight;
        int my = y + (int) this.trackHeight / 2;
        int by = this.getTrackBodyY(y);
        int bh = this.getTrackBodyHeight();
        int row = 0;
        Integer sheetY = this.sheetYCache.get(sheet);

        if (sheetY != null)
        {
            row = sheetY / Math.max(1, (int) this.trackHeight);
        }

        int trackWidth = BBSSettings.editorTrackWidth.get();

        int surface = row % 2 == 0 ? BBSSettings.deepSurface() : BBSSettings.baseSurface();

        context.batcher.box(area.x, by, area.ex(), by + bh, surface);

        if (hover)
        {
            context.batcher.box(area.x, by, area.ex(), by + bh, BBSSettings.color(BBSSettings.raisedSurface(), Colors.A25));
        }

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        /* Render bars indicating same values */
        for (int j = 1; j < keyframes.size(); j++)
        {
            Keyframe previous = (Keyframe) keyframes.get(j - 1);
            Keyframe frame = (Keyframe) keyframes.get(j);
            int c = Colors.setA(sheet.color, TRACK_BAR_ALPHA);
            int xx = this.keyframes.toGraphX(previous.getTick());
            int xxx = this.keyframes.toGraphX(frame.getTick());

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

        /* Draw keyframe handles (outer) */
        int forcedIndex = 0;

        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe frame = (Keyframe) keyframes.get(j);
            float tick = frame.getTick();
            int x1 = this.keyframes.toGraphX(tick);
            int x2 = this.keyframes.toGraphX(tick + frame.getDuration());

            /* Render custom duration markers */
            if (x1 != x2)
            {
                int y1 = my - 8 + (forcedIndex % 2 == 1 ? -4 : 0);
                int color = sheet.selection.has(j) ? Colors.WHITE :  Colors.setA(Colors.mulRGB(sheet.color, 0.9F), 0.75F);

                context.batcher.fillRect(builder, matrix, x1, y1 - 2, 1, 5, color, color, color, color);
                context.batcher.fillRect(builder, matrix, x2, y1 - 2, 1, 5, color, color, color, color);
                context.batcher.fillRect(builder, matrix, x1 + 1, y1, x2 - x1, 1, color, color, color, color);

                forcedIndex += 1;
            }

            boolean isPointHover = this.isNear(this.keyframes.toGraphX(frame.getTick()), my, context.mouseX, context.mouseY, Window.isAltPressed() && Window.isShiftPressed());
            boolean toRemove = Window.isCtrlPressed() && isPointHover;

            if (this.keyframes.isSelecting())
            {
                isPointHover = isPointHover || this.keyframes.getGrabbingArea(context).isInside(x1, my);
            }

            int kc = frame.getColor() != null ? frame.getColor().getRGBColor() | Colors.A100 : sheet.color;
            int c = (sheet.selection.has(j) || isPointHover ? Colors.WHITE : kc) | Colors.A100;

            if (toRemove)
            {
                c = Colors.RED | Colors.A100;
            }

            int pointOffset = toRemove ? 4 : 3;

            renderShape(frame, context, builder, matrix, x1, my, pointOffset, c);
        }

        /* Render keyframe handles (inner) */
        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe frame = (Keyframe) keyframes.get(j);
            int c = sheet.selection.has(j) ? Colors.ACTIVE : 0;
            int mx = this.keyframes.toGraphX(frame.getTick());
            int mc = c | Colors.A100;
            IKeyframeShapeRenderer shapeResult = renderShape(frame, context, builder, matrix, mx, my, 2, mc);

            shapeResult.renderKeyframeBackground(context, builder, matrix, mx, my, 2, mc);
        }

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private void renderSheetKeyframeShapes(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int y)
    {
        if (!this.isVisible(sheet))
        {
            return;
        }

        if (y + this.trackHeight < area.y || y > area.ey())
        {
            return;
        }

        List keyframes = sheet.channel.getKeyframes();
        int my = y + (int) this.trackHeight / 2;
        int forcedIndex = 0;

        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe frame = (Keyframe) keyframes.get(j);
            float tick = frame.getTick();
            int x1 = this.keyframes.toGraphX(tick);
            int x2 = this.keyframes.toGraphX(tick + frame.getDuration());

            if (x1 != x2)
            {
                forcedIndex += 1;
            }

            boolean isPointHover = this.isNear(x1, my, context.mouseX, context.mouseY, Window.isAltPressed() && Window.isShiftPressed());
            boolean toRemove = Window.isCtrlPressed() && isPointHover;

            if (this.keyframes.isSelecting())
            {
                isPointHover = isPointHover || this.keyframes.getGrabbingArea(context).isInside(x1, my);
            }

            int kc = frame.getColor() != null ? frame.getColor().getRGBColor() | Colors.A100 : sheet.color;
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
            int c = sheet.selection.has(j) ? Colors.ACTIVE : 0;
            int mx = this.keyframes.toGraphX(frame.getTick());
            int mc = c | Colors.A100;
            IKeyframeShapeRenderer shapeResult = renderShape(frame, context, builder, matrix, mx, my, 2, mc);

            shapeResult.renderKeyframeBackground(context, builder, matrix, mx, my, 2, mc);
        }
    }

    private int renderElementsTopmostKeyframes(UIContext context, BufferBuilder builder, Matrix4f matrix, Area area, List<UIKeyframeElement> elements, int y)
    {
        for (UIKeyframeElement element : elements)
        {
            if (element instanceof UIKeyframeSheet sheet)
            {
                this.renderSheetKeyframeShapes(context, builder, matrix, area, sheet, y);
            }

            y += this.getElementHeight(element);

            if (element instanceof UIKeyframeGroup group && !group.collapsed)
            {
                y = this.renderElementsTopmostKeyframes(context, builder, matrix, area, group.children, y);
            }
        }

        return y;
    }

    @Override
    public void renderTopmostKeyframes(UIContext context)
    {
        if (this.elements.isEmpty())
        {
            return;
        }

        Area area = this.keyframes.graphArea;
        int rulerBottom = TimelineRulerRenderer.getRulerBottom(area);
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

        context.batcher.clip(area.x, rulerBottom, area.ex(), area.ey(), context);
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        this.renderElementsTopmostKeyframes(context, builder, matrix, area, this.elements, this.getDopeSheetY());
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        context.batcher.unclip(context);
    }

    private boolean isPoseTabArrowHit(UIContext context, int y, int labelWidth)
    {
        int x = this.keyframes.area.x + labelWidth - LABEL_RIGHT_PAD - LABEL_ICON_SIZE;
        int minY = y + (int) this.trackHeight / 2 - 8;

        return context.mouseX >= x && context.mouseX < x + LABEL_ICON_SIZE && context.mouseY >= minY && context.mouseY < minY + LABEL_ICON_SIZE;
    }

    private void togglePoseTab(UIKeyframeSheet parent)
    {
        if (this.expandedPoseTabs.contains(parent))
        {
            this.expandedPoseTabs.remove(parent);

            for (Map.Entry<UIKeyframeSheet, UIKeyframeSheet> entry : this.poseTabRoots.entrySet())
            {
                if (entry.getValue() == parent)
                {
                    entry.getKey().selection.clear();
                }
            }
        }
        else
        {
            this.expandedPoseTabs.add(parent);
        }
    }

    @Override
    public void postRender(UIContext context)
    {
        if (!this.elements.isEmpty())
        {
            BufferBuilder builder = Tessellator.getInstance().getBuffer();
            Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

            this.renderLabels(context, builder, matrix, this.elements, 0, this.getDopeSheetY());
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
