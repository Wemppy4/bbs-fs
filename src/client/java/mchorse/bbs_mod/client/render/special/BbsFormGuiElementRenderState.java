package mchorse.bbs_mod.client.render.special;

import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Render state for a BBS form preview drawn as a vanilla special GUI element — the same mechanism vanilla
 * uses for 3D-in-GUI thumbnails (entities, banners, the inventory player). One is submitted per form-list
 * cell via {@code DrawContext.state.addSpecialElement}; {@link BbsFormGuiElementRenderer} renders the form's
 * model off-screen and the deferred GUI composites it into the cell. This is needed because the list draws
 * each cell in the GUI record phase, where a direct immediate 3D draw cannot composite (two-phase GUI).
 *
 * <p>The interface accessors (x1/x2/y1/y2/scale/scissorArea/bounds) are provided by the record components;
 * {@code pose()} is defaulted by {@link SpecialGuiElementRenderState}.</p>
 */
public record BbsFormGuiElementRenderState(
    Form form,
    int x1, int y1, int x2, int y2,
    float scale,
    @Nullable ScreenRect scissorArea,
    @Nullable ScreenRect bounds
) implements SpecialGuiElementRenderState
{
    public BbsFormGuiElementRenderState(Form form, int x1, int y1, int x2, int y2, float scale, @Nullable ScreenRect scissorArea)
    {
        this(form, x1, y1, x2, y2, scale, scissorArea,
            SpecialGuiElementRenderState.createBounds(x1, y1, x2, y2, scissorArea));
    }
}
