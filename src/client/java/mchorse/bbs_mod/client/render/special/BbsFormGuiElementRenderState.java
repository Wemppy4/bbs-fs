package mchorse.bbs_mod.client.render.special;

import mchorse.bbs_mod.forms.renderers.FormRenderer;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Render state for a BBS form thumbnail drawn as a vanilla special GUI element — the same mechanism
 * vanilla uses for 3D-in-GUI thumbnails (entities, banners, the inventory player). One is submitted per
 * form-list cell via {@code DrawContext.state.addSpecialElement}; {@link BbsFormGuiElementRenderer} calls
 * back into {@link FormRenderer#renderUIPreview} to render the form off-screen, and the deferred GUI
 * composites it into the cell. This indirection is needed because the list draws each cell in the GUI
 * record phase, where a direct immediate 3D draw cannot composite (two-phase GUI). The {@code renderer} is
 * the generic {@link FormRenderer} so every 3D form type (model/billboard/extruded/block/particle) can use
 * this one path; each supplies its own {@link FormRenderer#renderUIPreview}.
 *
 * <p>{@code pose} is the live 2D GUI matrix captured at submit time (it carries the list's scroll translate),
 * applied to the composite quad so the thumbnail scrolls with the cell — faithful to the original, which
 * rendered the model directly onto {@code context.batcher.getContext().getMatrices()}. {@code angle}
 * (cursor-driven yaw) and {@code transition} are likewise captured at submit; the remaining accessors
 * (x1/x2/y1/y2/scale/scissorArea/bounds) are record components.</p>
 */
public record BbsFormGuiElementRenderState(
    FormRenderer<?> renderer,
    float angle,
    float transition,
    Matrix3x2f pose,
    int x1, int y1, int x2, int y2,
    float scale,
    @Nullable ScreenRect scissorArea,
    @Nullable ScreenRect bounds
) implements SpecialGuiElementRenderState
{
    public BbsFormGuiElementRenderState(FormRenderer<?> renderer, float angle, float transition, Matrix3x2f pose, int x1, int y1, int x2, int y2, float scale, @Nullable ScreenRect scissorArea)
    {
        this(renderer, angle, transition, pose, x1, y1, x2, y2, scale, scissorArea,
            SpecialGuiElementRenderState.createBounds(x1, y1, x2, y2, scissorArea));
    }
}
