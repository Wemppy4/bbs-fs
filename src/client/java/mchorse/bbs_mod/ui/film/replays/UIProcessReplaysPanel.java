package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIPanelBase;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILabelList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.ui.utils.Label;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Batch processing of the picked replays: scatter them randomly, line them up, shape them into
 * squares, circles, cubes and spheres, drop them onto the ground, aim them at a target, shift
 * them — or drive the chosen channels with a MoLang expression. Split out of the replay list:
 * the list is a list, and this is half an editor of its own with two views and remembered state.
 */
public class UIProcessReplaysPanel extends UIConfirmOverlayPanel
{
    /** What the dialog remembers between openings — the last operation and its inputs. */
    private static final ProcessReplaysState PROCESS_STATE = new ProcessReplaysState();

    private final UIFilmPanel filmPanel;

    /** The picked replays in view order, snapshotted at opening (the overlay is modal). */
    private final List<Replay> replays;

    private final UIStringList properties = new UIStringList(null)
    {
        @Override
        protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
        {
            int h = this.scroll.scrollItemSize;
            int color = UIReplaysEditor.getColor(element);
            Icon icon = UIReplaysEditor.getIcon(element);

            context.batcher.box(x, y, x + 2, y + h, Colors.A100 | color);
            context.batcher.gradientHBox(x + 2, y, x + 24, y + h, Colors.A25 | color, color);
            context.batcher.icon(icon, x + 2, y + h / 2F, 0F, 0.5F);
            context.batcher.textShadow(this.elementToString(context, i, element), x + 24, y + (h - context.batcher.getFont().getHeight()) / 2, hover ? Colors.HIGHLIGHT : Colors.WHITE);
        }
    };

    private final UIPanelBase<UIElement> modes = new UIPanelBase<>(Direction.TOP);
    private final UINormalProcessView normal = new UINormalProcessView();
    private final UIAdvancedProcessView advanced = new UIAdvancedProcessView();

    private static class ProcessReplaysState
    {
        public String expression = "v";
        public List<String> properties = new ArrayList<>(List.of("x"));
        public boolean advanced;
        public boolean fill = false;
        public int lookAtTarget = -1;

        public NormalOperation operation = NormalOperation.RANDOM;
        public double randomMin = -1;
        public double randomMax = 1;
        public double lineOffset = 1;
        public double size = 3;
        public double shift = 1;
    }

    public UIProcessReplaysPanel(UIFilmPanel filmPanel, List<Replay> replays)
    {
        super(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_TITLE, IKey.EMPTY, null);

        this.filmPanel = filmPanel;
        this.replays = replays;

        this.message.setVisible(false);

        this.properties.scroll.scrollItemSize = 16;

        for (String id : collectProcessChannelIds(replays.get(0)))
        {
            this.properties.add(id);
        }

        this.properties.background().multi();
        this.properties.update();

        if (!PROCESS_STATE.properties.isEmpty())
        {
            this.properties.setCurrentScroll(PROCESS_STATE.properties.get(0));
        }

        for (String property : PROCESS_STATE.properties)
        {
            this.properties.addIndex(this.properties.getList().indexOf(property));
        }

        this.modes.registerPanel(this.normal, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_MODE_NORMAL, Icons.SHAPES);
        this.modes.registerPanel(this.advanced, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_MODE_ADVANCED, Icons.CODE);
        this.modes.setPanel(PROCESS_STATE.advanced ? this.advanced : this.normal);

        UIElement body = new UIElement();
        body.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -40);

        this.modes.relative(body).x(0).y(0).w(1F, -126).h(1F);

        this.properties.relative(body).x(1F, -120).y(20).w(120).h(1F, -20);

        this.confirm.w(1F, -10);
        this.content.add(body);
        body.add(this.modes, this.properties);
    }

    /** Numeric channels of a replay, curated first, in the order the timeline shows them. */
    private static List<String> collectProcessChannelIds(Replay replay)
    {
        ArrayList<String> out = new ArrayList<>();
        HashSet<String> added = new HashSet<>();

        for (String id : ReplayKeyframes.CURATED_CHANNELS)
        {
            BaseValue baseValue = replay.keyframes.get(id);

            if (baseValue instanceof KeyframeChannel<?> channel && KeyframeFactories.isNumeric(channel.getFactory()))
            {
                out.add(id);
                added.add(id);
            }
        }

        for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
        {
            if (!KeyframeFactories.isNumeric(channel.getFactory()) || added.contains(channel.getId()))
            {
                continue;
            }

            out.add(channel.getId());
        }

        return out;
    }

    @Override
    public void confirm()
    {
        if (this.apply())
        {
            super.confirm();
        }
    }

    private boolean apply()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return false;
        }

        List<String> selectedProperties = new ArrayList<>(this.properties.getCurrent());

        List<ReplayBatchProcessor.VisibleReplay> visible = this.collectVisibleReplays();

        if (visible.isEmpty())
        {
            return false;
        }

        boolean isAdvanced = this.modes.view == this.advanced;

        PROCESS_STATE.advanced = isAdvanced;

        if (isAdvanced)
        {
            if (selectedProperties.isEmpty())
            {
                context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NO_CHANNELS);

                return false;
            }

            PROCESS_STATE.properties = new ArrayList<>(selectedProperties);

            if (!this.applyAdvanced(context, visible, selectedProperties))
            {
                return false;
            }
        }
        else
        {
            NormalOperation operation = this.normal.getSelectedOperation();

            if (operation == null)
            {
                operation = NormalOperation.RANDOM;
            }

            if (operation != NormalOperation.LOOK_AT && selectedProperties.isEmpty())
            {
                context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NO_CHANNELS);

                return false;
            }

            if (operation == NormalOperation.FIT_HEIGHT && !selectedProperties.contains("y"))
            {
                context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NEED_Y_CHANNEL);

                return false;
            }

            PROCESS_STATE.properties = new ArrayList<>(selectedProperties);

            if (!this.applyNormal(context, visible, selectedProperties))
            {
                return false;
            }
        }

        this.filmPanel.getController().createEntities();
        this.filmPanel.replayEditor.updateChannelsList();

        return true;
    }

    private List<ReplayBatchProcessor.VisibleReplay> collectVisibleReplays()
    {
        /* The stagger is ordered by the film's own replay list, not by visible rows: a
         * selected replay whose folder is collapsed has no row, and indexing by rows used
         * to silently drop it from the batch (and shift everyone else's offset).
         *
         * The index is taken by identity: ValueGroup compares by content, so List.indexOf
         * hands back the first replay that merely looks the same - and a batch is usually
         * made of duplicates, which all reported index 0 and landed in one spot. */
        List<Replay> all = this.filmPanel.getData().replays.getList();
        int min = Integer.MAX_VALUE;

        for (Replay replay : this.replays)
        {
            int index = CollectionUtils.getIndex(all, replay);

            if (index >= 0)
            {
                min = Math.min(min, index);
            }
        }

        if (min == Integer.MAX_VALUE)
        {
            return new ArrayList<>();
        }

        List<ReplayBatchProcessor.VisibleReplay> out = new ArrayList<>();

        for (Replay replay : this.replays)
        {
            int index = CollectionUtils.getIndex(all, replay);

            if (index >= 0)
            {
                out.add(new ReplayBatchProcessor.VisibleReplay(replay, index, index - min));
            }
        }

        return out;
    }

    private boolean applyAdvanced(UIContext context, List<ReplayBatchProcessor.VisibleReplay> selected, List<String> selectedProperties)
    {
        String expressionText = this.advanced.expression.getText();
        ReplayBatchProcessor.Error error = ReplayBatchProcessor.applyAdvanced(selected, selectedProperties, expressionText);

        if (error == ReplayBatchProcessor.Error.INVALID_EXPRESSION)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_INVALID_EXPRESSION);
            return false;
        }

        return error == null;
    }

    private boolean applyNormal(UIContext context, List<ReplayBatchProcessor.VisibleReplay> selected, List<String> selectedProperties)
    {
        NormalOperation operation = this.normal.getSelectedOperation();

        if (operation == null)
        {
            operation = NormalOperation.RANDOM;
        }

        PROCESS_STATE.operation = operation;

        if (operation == NormalOperation.RANDOM)
        {
            PROCESS_STATE.randomMin = this.normal.randomMin.getValue();
            PROCESS_STATE.randomMax = this.normal.randomMax.getValue();
        }
        else if (operation == NormalOperation.LINE)
        {
            PROCESS_STATE.lineOffset = this.normal.lineOffset.getValue();
        }
        else if (operation == NormalOperation.SQUARE || operation == NormalOperation.SQUARE_OUTLINE || operation == NormalOperation.CUBE || operation == NormalOperation.CIRCLE || operation == NormalOperation.CIRCLE_OUTLINE || operation == NormalOperation.SPHERE)
        {
            PROCESS_STATE.size = this.normal.size.getValue();
        }
        else if (operation == NormalOperation.SHIFT)
        {
            PROCESS_STATE.shift = this.normal.shift.getValue();
        }
        ReplayBatchProcessor.NormalParams params = new ReplayBatchProcessor.NormalParams();
        params.randomMin = PROCESS_STATE.randomMin;
        params.randomMax = PROCESS_STATE.randomMax;
        params.lineOffset = PROCESS_STATE.lineOffset;
        params.size = PROCESS_STATE.size;
        params.shift = PROCESS_STATE.shift;
        params.fill = PROCESS_STATE.fill;
        params.lookAtTarget = this.resolveLookAtTargetReplay();
        params.groundProvider = operation == NormalOperation.FIT_HEIGHT ? this.createGroundProvider() : null;

        ReplayBatchProcessor.Error error = ReplayBatchProcessor.applyNormal(selected, selectedProperties, operation.op, params);

        if (error == ReplayBatchProcessor.Error.NEED_TWO_CHANNELS)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NEED_TWO_CHANNELS);
            return false;
        }
        else if (error == ReplayBatchProcessor.Error.NEED_THREE_CHANNELS)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NEED_THREE_CHANNELS);
            return false;
        }
        else if (error == ReplayBatchProcessor.Error.NEED_Y_CHANNEL)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NEED_Y_CHANNEL);
            return false;
        }
        else if (error == ReplayBatchProcessor.Error.NEED_TARGET)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NO_TARGET);
            return false;
        }
        else if (error == ReplayBatchProcessor.Error.NO_WORLD)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NO_WORLD);
            return false;
        }
        else if (error == ReplayBatchProcessor.Error.NEED_POSITION_CHANNELS)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NEED_THREE_CHANNELS);
            return false;
        }

        return error == null;
    }

    private ReplayBatchProcessor.GroundProvider createGroundProvider()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;

        if (world == null)
        {
            return null;
        }

        HashMap<Long, Double> cache = new HashMap<>();

        return (x, z) ->
        {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            long key = (((long) bx) << 32) ^ (bz & 0xffffffffL);

            Double cached = cache.get(key);

            if (cached != null)
            {
                return cached;
            }

            double top = world.getTopY() + 5;
            Vec3d pos = new Vec3d(x, top, z);
            BlockHitResult result = RayTracing.rayTrace(world, pos, new Vec3d(0D, -1D, 0D), top - world.getBottomY() + 5D);

            double y = Double.NaN;

            if (result != null && result.getType() != HitResult.Type.MISS)
            {
                y = result.getPos().y;
            }

            cache.put(key, y);

            return y;
        };
    }

    private Replay resolveLookAtTargetReplay()
    {
        if (PROCESS_STATE.operation != NormalOperation.LOOK_AT)
        {
            return null;
        }

        Film film = this.filmPanel.getData();

        if (film == null)
        {
            return null;
        }

        List<Replay> replays = film.replays.getList();
        int index = PROCESS_STATE.lookAtTarget;

        if (index < 0 || index >= replays.size())
        {
            return null;
        }

        return replays.get(index);
    }

    private class UINormalProcessView extends UIElement
    {
        private final UILabelList<NormalOperation> operations;

        private final UITrackpad randomMin;
        private final UITrackpad randomMax;
        private final UITrackpad lineOffset;
        private final UITrackpad size;
        private final UITrackpad shift;
        private final UIToggle fill;
        private final UIButton lookAtTarget;

        private final UIElement params = new UIElement();
        private final UIText hint = new UIText(IKey.EMPTY).padding(0, 0).lineHeight(10);

        public UINormalProcessView()
        {
            super();

            this.operations = new UILabelList<>((l) -> this.updateOperation())
            {
                @Override
                protected void renderElementPart(UIContext context, Label<NormalOperation> element, int i, int x, int y, boolean hover, boolean selected)
                {
                    int h = this.scroll.scrollItemSize;
                    Icon icon = element.value.icon;

                    context.batcher.icon(icon, x + 3, y + (h - 16) / 2F);
                    context.batcher.textShadow(element.title.get(), x + 22, y + (h - context.batcher.getFont().getHeight()) / 2, hover ? Colors.HIGHLIGHT : Colors.WHITE);
                }
            };
            this.operations.background();
            this.operations.scroll.scrollItemSize = UIConstants.CONTROL_HEIGHT;

            for (NormalOperation operation : NormalOperation.values())
            {
                this.operations.add(operation.title, operation);
            }

            this.randomMin = new UITrackpad();
            this.randomMin.limit(-10000, 10000, false);
            this.randomMin.setValue(PROCESS_STATE.randomMin);

            this.randomMax = new UITrackpad();
            this.randomMax.limit(-10000, 10000, false);
            this.randomMax.setValue(PROCESS_STATE.randomMax);

            this.lineOffset = new UITrackpad();
            this.lineOffset.limit(-10000, 10000, false);
            this.lineOffset.setValue(PROCESS_STATE.lineOffset);

            this.size = new UITrackpad();
            this.size.limit(0, 10000, false);
            this.size.setValue(PROCESS_STATE.size);

            this.shift = new UITrackpad();
            this.shift.limit(-10000, 10000, false);
            this.shift.setValue(PROCESS_STATE.shift);

            this.fill = new UIToggle(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FILL, PROCESS_STATE.fill, (b) -> PROCESS_STATE.fill = b.getValue());
            this.fill.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FILL_TOOLTIP);

            this.lookAtTarget = new UIButton(IKey.EMPTY, (b) -> this.openLookAtTargetMenu());
            this.lookAtTarget.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_LOOK_AT_TARGET_TOOLTIP);

            int opsHeight = UIConstants.CONTROL_HEIGHT * NormalOperation.values().length;

            this.operations.relative(this).xy(0, 0).w(1F).h(opsHeight);
            this.params.relative(this.operations).y(1F, UIConstants.MARGIN * 2).w(1F).h(UIConstants.CONTROL_HEIGHT * 2 + UIConstants.MARGIN);
            this.hint.relative(this.params).y(1F, UIConstants.MARGIN).w(1F);

            this.add(this.operations, this.params, this.hint);

            this.operations.setCurrentValue(PROCESS_STATE.operation);
            this.updateOperation();
        }

        private void updateOperation()
        {
            NormalOperation operation = null;
            Label<NormalOperation> operationLabel = this.operations.getCurrentFirst();

            if (operationLabel != null)
            {
                operation = operationLabel.value;
            }

            this.params.removeAll();
            int rows = 0;

            if (operation == NormalOperation.RANDOM)
            {
                UILabel minLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_MIN, 36);
                UILabel maxLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_MAX, 36);
                UIElement row = UI.row(minLabel, this.randomMin, maxLabel, this.randomMax);
                row.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                this.params.add(row);
                rows = 1;
                this.hint.text(operation.hint);
            }
            else if (operation == NormalOperation.LINE)
            {
                UILabel offsetLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_OFFSET, 56);
                UIElement row = UI.row(offsetLabel, this.lineOffset);
                row.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                this.params.add(row);
                rows = 1;
                this.hint.text(operation.hint);
            }
            else if (operation == NormalOperation.SQUARE || operation == NormalOperation.SQUARE_OUTLINE)
            {
                UILabel sizeLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_SIZE, 56);
                UIElement row1 = UI.row(sizeLabel, this.size);
                row1.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                this.params.add(row1);
                rows = 1;
                this.hint.text(operation.hint);
            }
            else if (operation == NormalOperation.CUBE || operation == NormalOperation.SPHERE)
            {
                UILabel sizeLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_SIZE, 56);
                UIElement row = UI.row(sizeLabel, this.size);
                row.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                this.params.add(row);
                rows = 1;
                this.hint.text(operation.hint);
            }
            else if (operation == NormalOperation.CIRCLE || operation == NormalOperation.CIRCLE_OUTLINE)
            {
                UILabel sizeLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_SIZE, 56);
                UIElement row = UI.row(sizeLabel, this.size);
                row.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                this.params.add(row);
                rows = 1;
                this.hint.text(operation.hint);
            }
            else if (operation == NormalOperation.FIT_HEIGHT)
            {
                this.hint.text(operation.hint);
            }
            else if (operation == NormalOperation.LOOK_AT)
            {
                UILabel targetLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_LOOK_AT_TARGET, 56);
                this.updateLookAtTargetLabel();
                UIElement row = UI.row(targetLabel, this.lookAtTarget);
                row.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                this.params.add(row);
                rows = 1;
                this.hint.text(operation.hint);
            }
            else if (operation == NormalOperation.SHIFT)
            {
                UILabel shiftLabel = this.paramLabel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_SHIFT, 56);
                UIElement row = UI.row(shiftLabel, this.shift);
                row.relative(this.params).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
                this.params.add(row);
                rows = 1;
                this.hint.text(operation.hint);
            }
            else
            {
                this.hint.text(IKey.EMPTY);
            }

            int y = UIConstants.CONTROL_HEIGHT + UIConstants.MARGIN;
            boolean showFill = operation == NormalOperation.CUBE || operation == NormalOperation.SPHERE;

            if (showFill)
            {
                this.fill.relative(this.params).x(0).y(y).w(1F).h(UIConstants.CONTROL_HEIGHT);
                this.params.add(this.fill);
                rows += 1;
            }

            int height = rows <= 0 ? 0 : rows * UIConstants.CONTROL_HEIGHT + (rows - 1) * UIConstants.MARGIN;
            this.params.h(height);
            this.resize();
        }

        private NormalOperation getSelectedOperation()
        {
            Label<NormalOperation> operationLabel = this.operations.getCurrentFirst();

            return operationLabel == null ? null : operationLabel.value;
        }

        private void updateLookAtTargetLabel()
        {
            Film film = UIProcessReplaysPanel.this.filmPanel.getData();

            if (film == null)
            {
                this.lookAtTarget.label = IKey.constant("-");
                return;
            }

            List<Replay> replays = film.replays.getList();
            int index = PROCESS_STATE.lookAtTarget;

            if (index < 0 || index >= replays.size())
            {
                this.lookAtTarget.label = IKey.constant("-");
                return;
            }

            this.lookAtTarget.label = IKey.constant(replays.get(index).getName());
        }

        private void openLookAtTargetMenu()
        {
            UIContext context = this.getContext();

            if (context == null)
            {
                return;
            }

            Film film = UIProcessReplaysPanel.this.filmPanel.getData();

            if (film == null)
            {
                return;
            }

            context.replaceContextMenu((manager) ->
            {
                manager.autoKeys();

                List<Replay> replays = film.replays.getList();

                for (int i = 0; i < replays.size(); i++)
                {
                    int index = i;
                    Replay replay = replays.get(i);
                    manager.action(Icons.FILM, IKey.constant(replay.getName()), () ->
                    {
                        PROCESS_STATE.lookAtTarget = index;
                        this.updateLookAtTargetLabel();
                    });
                }
            });
        }

        private UILabel paramLabel(IKey key, int width)
        {
            UILabel label = UI.label(key, UIConstants.CONTROL_HEIGHT);
            label.w(width);

            return label.labelAnchor(0F, 0.5F);
        }
    }

    private class UIAdvancedProcessView extends UIElement
    {
        private final UITextbox expression = new UITextbox((t) -> PROCESS_STATE.expression = t);
        private final UIText description = new UIText(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_DESCRIPTION).padding(0, 0);

        public UIAdvancedProcessView()
        {
            super();

            this.expression.setText(PROCESS_STATE.expression);
            this.expression.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_EXPRESSION_TOOLTIP);
            this.expression.relative(this).xy(0, 0).w(1F).h(20);
            this.description.relative(this.expression).y(1F, 6).w(1F);

            this.add(this.expression, this.description);
        }
    }

    private enum NormalOperation
    {
        RANDOM(ReplayBatchProcessor.Operation.RANDOM, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_RANDOM, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_RANDOM, Icons.SIX_STAR),
        LINE(ReplayBatchProcessor.Operation.LINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_LINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_LINE, Icons.LINE),
        SQUARE(ReplayBatchProcessor.Operation.SQUARE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SQUARE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHAPES, Icons.SQUARE),
        SQUARE_OUTLINE(ReplayBatchProcessor.Operation.SQUARE_OUTLINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SQUARE_OUTLINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHAPES, Icons.OUTLINE),
        CUBE(ReplayBatchProcessor.Operation.CUBE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_CUBE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_CUBE, Icons.BLOCK),
        CIRCLE(ReplayBatchProcessor.Operation.CIRCLE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_CIRCLE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHAPES, Icons.CIRCLE),
        CIRCLE_OUTLINE(ReplayBatchProcessor.Operation.CIRCLE_OUTLINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_CIRCLE_OUTLINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHAPES, Icons.OUTLINE_SPHERE),
        SPHERE(ReplayBatchProcessor.Operation.SPHERE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SPHERE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SPHERE, Icons.SPHERE),
        FIT_HEIGHT(ReplayBatchProcessor.Operation.FIT_HEIGHT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_FIT_HEIGHT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_FIT_HEIGHT, Icons.ARROW_DOWN),
        LOOK_AT(ReplayBatchProcessor.Operation.LOOK_AT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_LOOK_AT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_LOOK_AT, Icons.LOOKING),
        SHIFT(ReplayBatchProcessor.Operation.SHIFT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SHIFT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHIFT, Icons.SHIFT_TO);

        public final ReplayBatchProcessor.Operation op;
        public final IKey title;
        public final IKey hint;
        public final Icon icon;

        NormalOperation(ReplayBatchProcessor.Operation op, IKey title, IKey hint, Icon icon)
        {
            this.op = op;
            this.title = title;
            this.hint = hint;
            this.icon = icon;
        }
    }
}
