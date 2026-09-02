package mchorse.bbs_mod.ui.framework.elements.input.keyframes.overlays;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import mchorse.bbs_mod.utils.keyframes.KeyframeStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * How a keyframe is drawn - its shape, its colour and whether it is filled in.
 *
 * <p>It edits a style of its own and hands a copy to whoever opened it after every change, so the
 * same panel serves the keyframes that are selected right now and the style in the settings that
 * every new keyframe is born with. Neither caller has to know what fields a style has.</p>
 */
public class UIKeyframeStyleOverlayPanel extends UIOverlayPanel
{
    public UIIcons shapes;
    public UIColor color;
    public UIToggle filled;

    /** Which shape each slot of {@link #shapes} stands for - the strip only knows indices. */
    private final List<KeyframeShape> order = new ArrayList<>();

    private final KeyframeStyle style;
    private final Consumer<KeyframeStyle> callback;

    public UIKeyframeStyleOverlayPanel(KeyframeStyle style, Consumer<KeyframeStyle> callback)
    {
        super(L10n.lang("bbs.ui.keyframes.keyframe_style.title"));

        this.style = style.copy();
        this.callback = callback;

        this.shapes = new UIIcons((b) ->
        {
            this.style.setShape(this.order.get(b.getValue()));
            this.update();
        });

        for (KeyframeShape shape : KeyframeShape.values())
        {
            IKeyframeShapeRenderer renderer = KeyframeShapeRenderers.SHAPES.get(shape);

            if (renderer == null)
            {
                continue;
            }

            this.order.add(shape);
            this.shapes.add(renderer.getIcon(), renderer.getLabel());
        }

        this.shapes.setValue(this.order.indexOf(this.style.getShape()));

        this.color = new UIColor((value) ->
        {
            this.style.setColor(Color.rgb(value));
            this.update();
        });
        this.color.setColor(this.styleColor());
        this.color.tooltip(UIKeys.KEYFRAMES_CHANGE_COLOR);
        this.color.context((menu) -> menu.action(Icons.COLOR, UIKeys.KEYFRAMES_RESET_COLOR, () ->
        {
            this.style.setColor(null);
            this.color.setColor(this.styleColor());
            this.update();
        }));

        this.filled = new UIToggle(L10n.lang("bbs.ui.keyframes.keyframe_style.filled"), this.style.isFilled(), (b) ->
        {
            this.style.setFilled(b.getValue());
            this.update();
        });

        UIButton reset = new UIButton(L10n.lang("bbs.ui.keyframes.keyframe_style.reset"), (b) -> this.reset());

        UIElement column = UI.column(
            UI.label(L10n.lang("bbs.ui.keyframes.keyframe_style.shape")),
            this.shapes,
            UI.label(L10n.lang("bbs.ui.keyframes.keyframe_style.color")).marginTop(6),
            this.color,
            this.filled.marginTop(6),
            reset.marginTop(10)
        );

        column.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -12);

        this.content.add(column);

        /* The picker lives next to the overlay, not inside it, so it would outlive the panel */
        this.onClose((e) -> this.color.picker.removeFromParent());
    }

    /**
     * A style with no colour of its own follows the track, and there is no track here to show -
     * so the swatch goes black, the same way the keyframe editor used to render "not set".
     */
    private int styleColor()
    {
        Color color = this.style.getColor();

        return color == null ? 0 : color.getRGBColor();
    }

    private void reset()
    {
        this.style.reset();

        this.shapes.setValue(this.order.indexOf(this.style.getShape()));
        this.color.setColor(this.styleColor());
        this.filled.setValue(this.style.isFilled());

        this.update();
    }

    private void update()
    {
        this.callback.accept(this.style);
    }
}
