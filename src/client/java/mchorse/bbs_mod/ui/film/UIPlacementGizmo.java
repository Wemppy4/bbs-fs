package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.clips.misc.OverlayBox;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.camera.clips.misc.VideoClip;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.settings.values.core.ValuePlacement;
import mchorse.bbs_mod.ui.film.clips.widgets.UIPlacement;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The screen manipulator of overlay clips (subtitle, image, video): while such
 * a clip is selected and under the timeline's cursor, its on-screen rectangle
 * gets a draggable frame in the preview - the body moves the placement's
 * offset, the corner handles scale around the anchor.
 *
 * Works in the frame units the overlay renderers draw in. The box AND the unit
 * frame width come from what was actually rendered last frame ({@link OverlayBox}) -
 * never recomputed here, so the frame cannot drift away from the drawn overlay.
 */
public class UIPlacementGizmo
{
    private static final int HANDLE_SIZE = 3;
    private static final float MIN_SCALE_DISTANCE = 4F;

    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_SCALE = 2;
    private static final int MODE_SCALE_X = 3;
    private static final int MODE_SCALE_Y = 4;

    private final UIFilmPanel panel;

    private int mode = MODE_NONE;
    private float startUnitX;
    private float startUnitY;
    private Placement startPlacement;
    private ValuePlacement editedValue;

    public UIPlacementGizmo(UIFilmPanel panel)
    {
        this.panel = panel;
    }

    public boolean isDragging()
    {
        return this.mode != MODE_NONE;
    }

    /**
     * The selected clip's placement value, but only when the gizmo can edit it:
     * an overlay clip that is enabled, covers the timeline's cursor, and is not
     * stretched over the whole screen.
     */
    private ValuePlacement getPlacement()
    {
        if (!this.panel.cameraEditor.isVisible())
        {
            return null;
        }

        Clip clip = this.panel.cameraEditor.getClip();

        if (clip == null || !clip.enabled.get())
        {
            return null;
        }

        int cursor = this.panel.getCursor();

        if (cursor < clip.tick.get() || cursor >= clip.tick.get() + clip.duration.get())
        {
            return null;
        }

        if (clip instanceof SubtitleClip subtitle)
        {
            return subtitle.placement;
        }
        else if (clip instanceof VideoClip video)
        {
            return video.fullscreen.get() ? null : video.placement;
        }
        else if (clip instanceof ImageClip image)
        {
            return image.fullscreen.get() ? null : image.placement;
        }

        return null;
    }

    private OverlayBox getBox()
    {
        Clip clip = this.panel.cameraEditor.getClip();
        OverlayBox box = null;

        if (clip instanceof SubtitleClip subtitle)
        {
            box = subtitle.getSubtitle().box;
        }
        else if (clip instanceof VideoClip video)
        {
            box = video.getOverlay().box;
        }
        else if (clip instanceof ImageClip image)
        {
            box = image.getOverlay().box;
        }

        return box == null || box.unitWidth <= 0F ? null : box;
    }

    /* Unit space <-> screen space (the unit frame is stretched over the viewport) */

    private float toUnitX(UIContext context, Area viewport, float unitWidth)
    {
        return (context.mouseX - viewport.x) / (float) viewport.w * unitWidth;
    }

    private float toUnitY(UIContext context, Area viewport)
    {
        return (context.mouseY - viewport.y) / (float) viewport.h * Placement.HEIGHT;
    }

    private float toScreenX(float unitX, Area viewport, float unitWidth)
    {
        return viewport.x + unitX / unitWidth * viewport.w;
    }

    private float toScreenY(float unitY, Area viewport)
    {
        return viewport.y + unitY / Placement.HEIGHT * viewport.h;
    }

    public boolean mouseClicked(UIContext context, Area viewport)
    {
        if (context.mouseButton != 0)
        {
            return false;
        }

        ValuePlacement value = this.getPlacement();
        OverlayBox box = value == null ? null : this.getBox();

        if (box == null)
        {
            return false;
        }

        int x1 = (int) this.toScreenX(box.x, viewport, box.unitWidth);
        int y1 = (int) this.toScreenY(box.y, viewport);
        int x2 = (int) this.toScreenX(box.x + box.w, viewport, box.unitWidth);
        int y2 = (int) this.toScreenY(box.y + box.h, viewport);
        int mx = (x1 + x2) / 2;
        int my = (y1 + y2) / 2;
        int handle = HANDLE_SIZE + 2;

        boolean corner = this.isNear(context, x1, y1, handle) || this.isNear(context, x2, y1, handle)
            || this.isNear(context, x1, y2, handle) || this.isNear(context, x2, y2, handle);
        boolean sideX = this.isNear(context, x1, my, handle) || this.isNear(context, x2, my, handle);
        boolean sideY = this.isNear(context, mx, y1, handle) || this.isNear(context, mx, y2, handle);

        if (!corner && !sideX && !sideY && !(context.mouseX >= x1 && context.mouseX < x2 && context.mouseY >= y1 && context.mouseY < y2))
        {
            return false;
        }

        this.mode = corner ? MODE_SCALE : (sideX ? MODE_SCALE_X : (sideY ? MODE_SCALE_Y : MODE_MOVE));
        this.startUnitX = this.toUnitX(context, viewport, box.unitWidth);
        this.startUnitY = this.toUnitY(context, viewport);
        this.startPlacement = value.get().copy();
        this.editedValue = value;

        return true;
    }

    private boolean isNear(UIContext context, int x, int y, int distance)
    {
        return Math.abs(context.mouseX - x) <= distance && Math.abs(context.mouseY - y) <= distance;
    }

    public boolean mouseReleased(UIContext context)
    {
        if (this.mode == MODE_NONE)
        {
            return false;
        }

        this.mode = MODE_NONE;
        this.startPlacement = null;
        this.editedValue = null;
        this.panel.cameraEditor.markLastUndoNoMerging();

        return true;
    }

    private void updateDrag(UIContext context, Area viewport, OverlayBox box)
    {
        ValuePlacement value = this.getPlacement();

        if (value != this.editedValue || value == null)
        {
            /* The clip got deselected or edited away mid-drag */
            this.mode = MODE_NONE;

            return;
        }

        float unitX = this.toUnitX(context, viewport, box.unitWidth);
        float unitY = this.toUnitY(context, viewport);
        Placement edited = this.startPlacement.copy();

        if (this.mode == MODE_MOVE)
        {
            edited.offsetX = this.startPlacement.offsetX + (unitX - this.startUnitX);
            edited.offsetY = this.startPlacement.offsetY + (unitY - this.startUnitY);
        }
        else
        {
            /* Scale around the anchor point, which stays put on screen */
            float anchorX = box.unitWidth * this.startPlacement.windowX + this.startPlacement.offsetX;
            float anchorY = Placement.HEIGHT * this.startPlacement.windowY + this.startPlacement.offsetY;

            if (this.mode == MODE_SCALE)
            {
                if (UIPlacement.isChained())
                {
                    float startDistance = (float) Math.hypot(this.startUnitX - anchorX, this.startUnitY - anchorY);
                    float distance = (float) Math.hypot(unitX - anchorX, unitY - anchorY);

                    if (startDistance < MIN_SCALE_DISTANCE)
                    {
                        return;
                    }

                    float ratio = distance / startDistance;

                    edited.scaleX = this.startPlacement.scaleX * ratio;
                    edited.scaleY = this.startPlacement.scaleY * ratio;
                }
                else
                {
                    /* With shift held the corner stretches each axis on its own */
                    float startX = Math.abs(this.startUnitX - anchorX);
                    float startY = Math.abs(this.startUnitY - anchorY);

                    if (startX >= MIN_SCALE_DISTANCE)
                    {
                        edited.scaleX = this.startPlacement.scaleX * (Math.abs(unitX - anchorX) / startX);
                    }

                    if (startY >= MIN_SCALE_DISTANCE)
                    {
                        edited.scaleY = this.startPlacement.scaleY * (Math.abs(unitY - anchorY) / startY);
                    }
                }
            }
            else if (this.mode == MODE_SCALE_X)
            {
                float startDistance = Math.abs(this.startUnitX - anchorX);

                if (startDistance < MIN_SCALE_DISTANCE)
                {
                    return;
                }

                float ratio = Math.abs(unitX - anchorX) / startDistance;

                edited.scaleX = this.startPlacement.scaleX * ratio;

                if (UIPlacement.isChained())
                {
                    edited.scaleY = this.startPlacement.scaleY * ratio;
                }
            }
            else
            {
                float startDistance = Math.abs(this.startUnitY - anchorY);

                if (startDistance < MIN_SCALE_DISTANCE)
                {
                    return;
                }

                float ratio = Math.abs(unitY - anchorY) / startDistance;

                edited.scaleY = this.startPlacement.scaleY * ratio;

                if (UIPlacement.isChained())
                {
                    edited.scaleX = this.startPlacement.scaleX * ratio;
                }
            }
        }

        if (!value.get().equals(edited))
        {
            this.panel.cameraEditor.editMultiple(value, (v) -> v.set(edited.copy()));
            this.panel.cameraEditor.fillData();
        }
    }

    public void render(UIContext context, Area viewport)
    {
        ValuePlacement value = this.getPlacement();
        OverlayBox box = value == null ? null : this.getBox();

        if (box == null)
        {
            return;
        }

        if (this.mode != MODE_NONE)
        {
            this.updateDrag(context, viewport, box);
        }

        int x1 = (int) this.toScreenX(box.x, viewport, box.unitWidth);
        int y1 = (int) this.toScreenY(box.y, viewport);
        int x2 = (int) this.toScreenX(box.x + box.w, viewport, box.unitWidth);
        int y2 = (int) this.toScreenY(box.y + box.h, viewport);

        context.batcher.clip(viewport.x, viewport.y, viewport.w, viewport.h, context.menu.width, context.menu.height);

        context.batcher.outline(x1, y1, x2, y2, this.mode == MODE_NONE ? Colors.A75 | Colors.WHITE & Colors.RGB : Colors.WHITE);

        int mx = (x1 + x2) / 2;
        int my = (y1 + y2) / 2;

        this.renderHandle(context, x1, y1);
        this.renderHandle(context, x2, y1);
        this.renderHandle(context, x1, y2);
        this.renderHandle(context, x2, y2);
        this.renderHandle(context, x1, my);
        this.renderHandle(context, x2, my);
        this.renderHandle(context, mx, y1);
        this.renderHandle(context, mx, y2);

        /* The anchor point the scaling pivots around */
        float anchorX = box.unitWidth * value.get().windowX + value.get().offsetX;
        float anchorY = Placement.HEIGHT * value.get().windowY + value.get().offsetY;
        int ax = (int) this.toScreenX(anchorX, viewport, box.unitWidth);
        int ay = (int) this.toScreenY(anchorY, viewport);

        context.batcher.box(ax - 1, ay - 1, ax + 1, ay + 1, Colors.WHITE);

        context.batcher.unclip(context);
    }

    private void renderHandle(UIContext context, int x, int y)
    {
        int s = HANDLE_SIZE;

        context.batcher.box(x - s, y - s, x + s, y + s, Colors.WHITE);
        context.batcher.outline(x - s, y - s, x + s, y + s, Colors.A100);
    }
}
