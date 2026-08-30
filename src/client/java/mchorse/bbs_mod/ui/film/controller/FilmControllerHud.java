package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The editor's overlay on the film preview. Split out of {@link UIFilmController} because it
 * only READS — every number on screen belongs to something else (the mouse control, the take,
 * the selection), and drawing them was the longest single stretch of the controller.
 */
public class FilmControllerHud
{
    private final UIFilmController controller;

    public FilmControllerHud(UIFilmController controller)
    {
        this.controller = controller;
    }

    /**
     * Everything the editor draws over the preview: the stick guide while an actor is driven by
     * hand, the recording tally, the loop and flight-speed marks, the selected replay's name and
     * form thumbnail — then the gizmo, the pick highlight and the orbit widget on top.
     */
    public void render(UIContext context, Area area)
    {
        FontRenderer font = context.batcher.getFont();
        int mode = this.controller.mouse.getMode();

        if (this.controller.getControlled() != null)
        {
            /* Render helpful guides for sticks and triggers controls */
            if (mode > 0)
            {
                String label = UIKeys.FILM_GROUPS_LEFT_STICK.get();

                if (mode == 2)
                {
                    label = UIKeys.FILM_GROUPS_RIGHT_STICK.get();
                }
                else if (mode == 3)
                {
                    label = UIKeys.FILM_GROUPS_TRIGGERS.get();
                }
                else if (mode == 4)
                {
                    label = UIKeys.FILM_GROUPS_EXTRA_1.get();
                }
                else if (mode == 5)
                {
                    label = UIKeys.FILM_GROUPS_EXTRA_2.get();
                }

                context.batcher.textCard(label, area.x + 5, area.ey() - 5 - font.getHeight(), Colors.WHITE, BBSSettings.primaryColor(Colors.A100));

                int ww = (int) (Math.min(area.w, area.h) * 0.75F);
                int hh = ww;
                int x = area.x + (area.w - ww) / 2;
                int y = area.y + (area.h - hh) / 2;
                int color = Colors.setA(Colors.WHITE, 0.5F);

                context.batcher.outline(x, y, x + ww, y + hh, color);

                int bx = area.x + area.w / 2 + (int) (this.controller.mouse.getStick().y * ww / 2);
                int by = area.y + area.h / 2 + (int) (this.controller.mouse.getStick().x * hh / 2);

                context.batcher.box(bx - 4, by - 4, bx + 4, by + 4, color);
            }
        }

        /* Render recording overlay */
        if (this.controller.isRecording())
        {
            int x = area.x + 5 + 16;
            int y = area.y + 5;

            context.batcher.icon(Icons.SPHERE, Colors.RED | Colors.A100, x, y, 1F, 0F);

            if (this.controller.getRecordingCountdown() <= 0)
            {
                context.batcher.textCard(UIKeys.FILM_CONTROLLER_TICKS.format(this.controller.getTick()).get(), x + 3, y + 4, Colors.WHITE, Colors.A50);
            }
            else
            {
                context.batcher.textCard(String.valueOf(this.controller.getRecordingCountdown() / 20F), x + 3, y + 4, Colors.WHITE, Colors.A50);
            }
        }

        int x = area.ex() - 4;
        int y = area.y + 5;

        if (BBSSettings.editorLoop.get())
        {
            context.batcher.icon(Icons.REFRESH, Colors.WHITE | Colors.A100, x, y, 1F, 0F);

            y += 16 + 5;
        }

        if (this.controller.panel.isFlying())
        {
            String label = UIKeys.FILM_CONTROLLER_SPEED.format(this.controller.panel.dashboard.orbit.speed.getValue()).get();
            int w = font.getWidth(label);

            context.batcher.textCard(label, x - w, y, Colors.WHITE, Colors.A50);

            y += font.getHeight() + 7;
        }

        Replay replay = this.controller.panel.replayEditor.getReplay();

        if (replay != null)
        {
            String label = replay.getName();
            int w = font.getWidth(label);

            context.batcher.textCard(label, x - w, y, Colors.WHITE, Colors.A50);

            /* What the gizmo is on, under the actor's name — the gizmo's position in the scene
             * is the only other clue, and a root gizmo standing at a bone's height is not one.
             * Only while a gizmo is actually shown: with nothing selected the line would be
             * answering a question nobody asked. */
            String targetLabel = this.editTargetLabel();

            if (targetLabel != null)
            {
                int tw = font.getWidth(targetLabel);

                context.batcher.textCard(targetLabel, x - tw, y + font.getHeight() + 7, Colors.LIGHTER_GRAY, Colors.A50);
            }

            Form form = replay.form.get();

            if (form != null)
            {
                x -= w + 35;
                y -= 5;

                context.batcher.clip(x, y - 10, 40, 40, context);

                y -= 10;

                FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);

                context.batcher.unclip(context);
            }
        }

        /* The visual gizmo draws here, before the picking preview, so the bone /
         * sphere hover highlights composite on top of it. It moved out of the
         * world pass into the UI pipeline so its translucent parts blend
         * correctly (see Gizmo#renderInterface). */
        if (this.controller.canShowGizmo())
        {
            this.controller.gizmo().renderGizmo(context);
        }

        this.controller.picker.renderPreview(context, area);

        this.controller.orbitGizmo.render(context, area);

        this.controller.orbit.handleOrbiting(context);
    }

    /**
     * What the gizmo is on, for the line under the actor's name, or {@code null} when no gizmo
     * is shown. The replay's own placement is named by its timeline category — that is where
     * its keys land, and no single track owns it; everything else is named by
     * {@link UIKeyframeEditor#getTargetLabel}, which knows whether the useful name is the
     * track's or a bone picked inside it.
     */
    private String editTargetLabel()
    {
        if (!this.controller.canShowGizmo())
        {
            return null;
        }

        FilmTarget target = this.controller.getEditTarget();

        if (target.is(FilmTarget.Kind.ROOT))
        {
            return UIReplaysEditor.ReplayCategory.REPLAY.label.get();
        }

        if (target.isNone())
        {
            return null;
        }

        UIKeyframeEditor editor = this.controller.panel.replayEditor.keyframeEditor;
        String label = editor == null ? null : editor.getTargetLabel();

        /* Nothing to ask (the track was rebuilt under us) — the path is still better than nothing. */
        return label != null ? label : StringUtils.fileName(target.bone());
    }
}
