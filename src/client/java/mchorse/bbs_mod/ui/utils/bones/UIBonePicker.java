package mchorse.bbs_mod.ui.utils.bones;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.function.Consumer;

/**
 * The bone-valued control: a button that shows the current bone and opens the
 * {@link UIBonePickerContextMenu} popup, plus an optional eyedropper icon that arms
 * a viewport pick — the next click on the model (its stencil map) delivers the bone
 * straight into this control. Both paths land in the one callback.
 *
 * <p>The host wires the two capabilities separately: {@link #menu} tells the picker
 * how to fill its popup at click time (an unfilled popup simply doesn't open), and
 * {@link #viewport} provides the eyedropper backend — without it the icon stays
 * hidden and the control is just the button (e.g. the model editor has no stencil
 * viewport). The host keeps owning the button label via {@link #setLabel}, so
 * host-specific decorations (the IK panel's cycle warning) stay possible.</p>
 */
public class UIBonePicker extends UIElement
{
    public final UIButton button;
    public final UIIcon eyedropper;

    private final Consumer<String> callback;

    private Consumer<UIBonePickerContextMenu> menu;
    private Viewport viewport;
    private boolean picking;

    public UIBonePicker(Consumer<String> callback)
    {
        this.callback = callback;

        this.button = new UIButton(IKey.EMPTY, (b) -> this.openMenu());

        /* The armed state renders as the standard bottom highlight, same as the
         * IK panel's lock icons — the glyph is the mode, no extra state to sync. */
        this.eyedropper = new UIIcon(Icons.EYEDROPPER, (b) -> this.togglePicking())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                if (UIBonePicker.this.picking)
                {
                    context.batcher.highlight(this.area, Direction.BOTTOM);
                }

                super.renderSkin(context);
            }
        };
        /* UIIcon defaults to 20x20 which is taller than the control row — that
         * padded the whole picker out; the glyph is 16x16, so match it exactly. */
        this.eyedropper.wh(16, 16);
        this.eyedropper.setVisible(false);

        this.h(UIConstants.CONTROL_HEIGHT);
        this.row(UIConstants.MARGIN).preferred(0);
        this.add(this.button, this.eyedropper);
    }

    /** How to fill the popup when the button is clicked; an empty fill means "nothing to pick" and no popup opens. */
    public UIBonePicker menu(Consumer<UIBonePickerContextMenu> configurator)
    {
        this.menu = configurator;

        return this;
    }

    /** Eyedropper backend; the icon only appears once a viewport is provided. */
    public UIBonePicker viewport(Viewport viewport)
    {
        this.viewport = viewport;
        this.eyedropper.setVisible(viewport != null);

        return this;
    }

    public void setLabel(IKey label)
    {
        this.button.label = label;
    }

    @Override
    public UIElement tooltip(IKey tooltip)
    {
        this.button.tooltip(tooltip);

        return this;
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);

        this.button.setEnabled(enabled);
        this.eyedropper.setEnabled(enabled);
    }

    private void openMenu()
    {
        /* Opening the popup is a change of mind — an armed eyedropper would silently
         * hijack the next viewport click after the popup pick, so disarm it. */
        if (this.picking && this.viewport != null)
        {
            this.viewport.stopPicking();
        }

        if (this.menu == null)
        {
            return;
        }

        UIBonePickerContextMenu popup = new UIBonePickerContextMenu(this.callback);

        this.menu.accept(popup);

        if (!popup.isEmpty())
        {
            this.getContext().replaceContextMenu(popup);
        }
    }

    private void togglePicking()
    {
        if (this.viewport == null)
        {
            return;
        }

        if (this.picking)
        {
            /* Cancelling delivers null through the callback below, resetting the flag. */
            this.viewport.stopPicking();

            return;
        }

        this.picking = true;
        this.viewport.startPicking((bone) ->
        {
            this.picking = false;

            if (bone != null && !bone.isEmpty() && this.callback != null)
            {
                this.callback.accept(bone);
            }
        });
    }

    /**
     * The eyedropper's backend: arms a one-shot viewport pick. The implementation must
     * ALWAYS answer — the picked bone on a hit, null on a miss, cancel or re-arm — so
     * the picker's armed state can't get stuck.
     */
    public interface Viewport
    {
        void startPicking(Consumer<String> callback);

        void stopPicking();
    }
}
