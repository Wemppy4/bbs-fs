package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.joml.Vector3d;

import java.util.function.Consumer;
import java.util.function.Function;

public class UIFilmMoveOverlayPanel extends UIMessageOverlayPanel
{
    private Consumer<Vector3d> callbackVector;
    private Function<Boolean, Vector3d> difference;
    private UIElement secondBar;
    private UIButton confirm;
    private UITrackpad x;
    private UITrackpad y;
    private UITrackpad z;

    public UIFilmMoveOverlayPanel(Consumer<Vector3d> callbackVector)
    {
        super(UIKeys.FILM_MOVE_TITLE, UIKeys.FILM_MOVE_DESCRIPTION);

        this.callbackVector = callbackVector;

        this.confirm = new UIButton(UIKeys.GENERAL_OK, (b) ->
        {
            this.close();

            if (this.callbackVector != null)
            {
                this.callbackVector.accept(new Vector3d(
                    this.x.getValue(),
                    this.y.getValue(),
                    this.z.getValue()
                ));
            }
        });
        this.x = new UITrackpad();
        this.y = new UITrackpad();
        this.z = new UITrackpad();

        UIElement row = UI.row(this.x, this.y, this.z);

        row.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.FILM_MOVE_CONTEXT_FILL, () -> this.fillDifference(false));
            menu.action(Icons.DOWNLOAD, UIKeys.FILM_MOVE_CONTEXT_FILL_ROUNDED, () -> this.fillDifference(true));
        });

        this.secondBar = UI.column(row, this.confirm);
        this.secondBar.relative(this.content).x(6).y(1F, -6).w(1F, -12).anchor(0, 1F);

        this.content.add(this.secondBar);
    }

    /**
     * Supplies the relative move that snaps the scene's first keyframe onto the
     * player (argument = whether to round the player's position); offered through
     * the XYZ row's context menu (not applied by default).
     */
    public UIFilmMoveOverlayPanel difference(Function<Boolean, Vector3d> difference)
    {
        this.difference = difference;

        return this;
    }

    private void fillDifference(boolean round)
    {
        if (this.difference != null)
        {
            this.fill(this.difference.apply(round));
        }
    }

    public void fill(Vector3d vector)
    {
        this.x.setValue(vector.x);
        this.y.setValue(vector.y);
        this.z.setValue(vector.z);
    }
}