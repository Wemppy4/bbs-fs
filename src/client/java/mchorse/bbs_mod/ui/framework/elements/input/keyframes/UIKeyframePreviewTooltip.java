package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.KeyframeType;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.tooltips.ITooltip;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Hover preview for keyframes: when the mouse rests on a keyframe point, a small
 * window shows what the keyframe holds — the actual color, the actual texture,
 * the form posed by a pose/bone keyframe, T/R/S numbers for transforms, and the
 * plain value for scalars.
 */
public class UIKeyframePreviewTooltip implements ITooltip
{
    private static final Area AREA = new Area();
    private static final float EPSILON = 1e-4F;

    private static final int COLOR_SIZE = 48;
    private static final int TEXTURE_SIZE = 96;
    private static final int TEXTURE_MIN_SIZE = 48;
    private static final int FORM_SIZE = 80;
    private static final int CURSOR_OFFSET = 12;

    private final UIKeyframes keyframes;

    /* Pose preview cache: the posed form copy is rebuilt only when the hovered
     * keyframe (or its tick/value) changes, not every frame */
    private Keyframe poseKeyframe;
    private float poseTick;
    private Object poseValue;
    private Form poseForm;

    public UIKeyframePreviewTooltip(UIKeyframes keyframes)
    {
        this.keyframes = keyframes;
    }

    @Override
    public IKey getLabel()
    {
        return IKey.EMPTY;
    }

    @Override
    public void renderTooltip(UIContext context)
    {
        Pair<Keyframe, KeyframeType> pair = this.findKeyframe(context);

        if (pair == null)
        {
            this.clearPoseCache();

            return;
        }

        Keyframe keyframe = pair.a;
        IKeyframeFactory factory = keyframe.getFactory();
        Object value = keyframe.getValue();

        if (factory == KeyframeFactories.COLOR)
        {
            this.renderColor(context, (Color) value);
        }
        else if (factory == KeyframeFactories.LINK)
        {
            this.renderLink(context, (Link) value);
        }
        else if (factory == KeyframeFactories.POSE || factory == KeyframeFactories.POSE_TRANSFORM)
        {
            this.renderPose(context, keyframe);
        }
        else
        {
            List<String> lines = this.getLines(factory, value);

            if (!lines.isEmpty())
            {
                this.renderLines(context, lines);
            }
        }
    }

    private Pair<Keyframe, KeyframeType> findKeyframe(UIContext context)
    {
        if (!BBSSettings.keyframePreview.get())
        {
            return null;
        }

        /* No preview while dragging/selecting/etc., or in the Ctrl (remove/create)
         * and Alt (duplicate/column select) modifier modes */
        if (this.keyframes.isInteracting() || Window.isCtrlPressed() || Window.isAltPressed())
        {
            return null;
        }

        if (!this.keyframes.graphArea.isInside(context))
        {
            return null;
        }

        Pair<Keyframe, KeyframeType> pair = this.keyframes.getGraph().findKeyframe(context.mouseX, context.mouseY);

        return pair != null && pair.b == KeyframeType.REGULAR ? pair : null;
    }

    /* Layout */

    /**
     * Compute the preview window's area near the cursor (clamped to the screen)
     * and render a semi-transparent backdrop for it.
     */
    private Area start(UIContext context, int w, int h)
    {
        int x = context.mouseX + CURSOR_OFFSET;
        int y = context.mouseY - h - CURSOR_OFFSET;

        x = MathUtils.clamp(x, 6, context.menu.width - w - 6);
        y = MathUtils.clamp(y, 6, context.menu.height - h - 6);

        AREA.set(x, y, w, h);

        AREA.offset(3);

        int color = BBSSettings.primaryColor.get();

        context.batcher.dropShadow(AREA.x, AREA.y, AREA.ex(), AREA.ey(), 6, Colors.A25 + color, color);
        AREA.render(context.batcher, BBSSettings.raisedSurface());

        AREA.offset(-3);

        return AREA;
    }

    /* Per-type previews */

    private void renderColor(UIContext context, Color color)
    {
        FontRenderer font = context.batcher.getFont();
        String label = color.stringify(true);
        int w = Math.max(COLOR_SIZE, font.getWidth(label));
        int h = COLOR_SIZE + 4 + font.getHeight();
        Area area = this.start(context, w, h);
        int x = area.mx() - COLOR_SIZE / 2;

        if (color.a < 1F)
        {
            context.batcher.iconArea(Icons.CHECKBOARD, x, area.y, COLOR_SIZE, COLOR_SIZE);
        }

        context.batcher.box(x, area.y, x + COLOR_SIZE, area.y + COLOR_SIZE, color.getARGBColor());
        context.batcher.textShadow(label, area.mx() - font.getWidth(label) / 2, area.ey() - font.getHeight(), Colors.WHITE);
    }

    private void renderLink(UIContext context, Link link)
    {
        if (link == null)
        {
            return;
        }

        Texture texture = context.render.getTextures().getTexture(link);
        FontRenderer font = context.batcher.getFont();
        int fw = texture.width;
        int fh = texture.height;

        if (fw <= 0 || fh <= 0)
        {
            return;
        }

        /* Fit big textures into the preview box, integer-upscale tiny ones */
        if (fw > TEXTURE_SIZE || fh > TEXTURE_SIZE)
        {
            float scale = TEXTURE_SIZE / (float) Math.max(fw, fh);

            fw = Math.max(1, (int) (fw * scale));
            fh = Math.max(1, (int) (fh * scale));
        }
        else if (fw < TEXTURE_MIN_SIZE && fh < TEXTURE_MIN_SIZE)
        {
            int factor = Math.max(1, TEXTURE_MIN_SIZE / Math.max(fw, fh));

            fw *= factor;
            fh *= factor;
        }

        String label = link.toString();

        if (font.getWidth(label) > 160)
        {
            label = label.substring(label.lastIndexOf('/') + 1);
        }

        int w = Math.max(fw, font.getWidth(label));
        int h = fh + 4 + font.getHeight();
        Area area = this.start(context, w, h);
        int x = area.mx() - fw / 2;

        context.batcher.iconArea(Icons.CHECKBOARD, x, area.y, fw, fh);
        context.batcher.fullTexturedBox(texture, x, area.y, fw, fh);
        context.batcher.textShadow(label, area.mx() - font.getWidth(label) / 2, area.ey() - font.getHeight(), Colors.WHITE);
    }

    private void renderPose(UIContext context, Keyframe keyframe)
    {
        UIKeyframeSheet sheet = this.keyframes.getGraph().getSheet(keyframe);

        if (sheet == null)
        {
            return;
        }

        Form form = this.getPoseForm(sheet, keyframe);

        if (form == null)
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        PerLimbService.PoseBonePath path = keyframe.getFactory() == KeyframeFactories.POSE_TRANSFORM
            ? PerLimbService.parsePoseBonePath(sheet.id)
            : null;
        String label = path == null ? null : path.bone();

        int w = FORM_SIZE;
        int h = FORM_SIZE;

        if (label != null)
        {
            w = Math.max(w, font.getWidth(label));
            h += 4 + font.getHeight();
        }

        Area area = this.start(context, w, h);
        int x = area.mx() - FORM_SIZE / 2;

        context.batcher.clip(x, area.y, x + FORM_SIZE, area.y + FORM_SIZE, context);
        FormUtilsClient.renderUI(form, context, x, area.y, x + FORM_SIZE, area.y + FORM_SIZE);
        context.batcher.unclip(context);

        if (label != null)
        {
            context.batcher.textShadow(label, area.mx() - font.getWidth(label) / 2, area.ey() - font.getHeight(), Colors.WHITE);
        }
    }

    /* Pose form assembly */

    private void clearPoseCache()
    {
        this.poseKeyframe = null;
        this.poseValue = null;
        this.poseForm = null;
    }

    private Form getPoseForm(UIKeyframeSheet sheet, Keyframe keyframe)
    {
        IKeyframeFactory factory = keyframe.getFactory();
        Object value = keyframe.getValue();

        if (this.poseKeyframe == keyframe && this.poseTick == keyframe.getTick() && this.poseValue != null && factory.compare(this.poseValue, value))
        {
            return this.poseForm;
        }

        this.poseKeyframe = keyframe;
        this.poseTick = keyframe.getTick();
        this.poseValue = factory.copy(value);
        this.poseForm = this.createPoseForm(sheet, factory, value, keyframe);

        return this.poseForm;
    }

    private Form createPoseForm(UIKeyframeSheet sheet, IKeyframeFactory factory, Object value, Keyframe keyframe)
    {
        Form owner = sheet.form;

        if (owner == null && sheet.property != null)
        {
            owner = FormUtils.getForm(sheet.property);
        }

        if (owner == null)
        {
            return null;
        }

        Form evaluated = this.evaluateAtTick(sheet, owner, keyframe);

        if (evaluated != null)
        {
            return evaluated;
        }

        /* Fallback for channels that don't live in a FormProperties (non-film
         * editors): the keyframe's value lands on a clean copy's base state */
        Form copy = FormUtils.copy(owner);

        if (copy == null)
        {
            return null;
        }

        if (factory == KeyframeFactories.POSE)
        {
            String id = sheet.property == null ? "pose" : sheet.property.getId();

            if (copy.get(id) instanceof ValuePose pose && value instanceof Pose newPose)
            {
                pose.set(newPose.copy());

                return copy;
            }

            return null;
        }

        /* Bone track: the keyframe's transform stacks onto the base pose's bone,
         * matching FormProperties.applyProperty */
        PerLimbService.PoseBonePath path = PerLimbService.parsePoseBonePath(sheet.id);

        if (path == null || !(copy instanceof ModelForm modelForm) || !(value instanceof Transform boneValue))
        {
            return null;
        }

        Pose pose = modelForm.pose.get();
        PoseTransform transform = pose.transforms.get(path.bone());

        if (transform == null)
        {
            transform = new PoseTransform();
            pose.transforms.put(path.bone(), transform);
        }

        transform.add(boneValue);

        return copy;
    }

    /**
     * The honest pose preview: the film's own playback code evaluates ALL channels
     * at the keyframe's tick on a copy of the root form, so pose layers (pose,
     * overlays, bone tracks) combine exactly like they do during playback. Returns
     * the copy's counterpart of the owner form, or null when the channel doesn't
     * belong to a {@link FormProperties} (non-film editors fall back to the plain
     * "config + keyframe value" preview).
     */
    private Form evaluateAtTick(UIKeyframeSheet sheet, Form owner, Keyframe keyframe)
    {
        if (!(sheet.channel.getParent() instanceof FormProperties properties))
        {
            return null;
        }

        Form copy = FormUtils.copy(FormUtils.getRoot(owner));

        if (copy == null)
        {
            return null;
        }

        properties.applyProperties(copy, keyframe.getTick());

        String path = FormUtils.getPath(owner);

        return path.isEmpty() ? copy : FormUtils.getForm(copy, path);
    }

    /* Text previews */

    private List<String> getLines(IKeyframeFactory factory, Object value)
    {
        List<String> lines = new ArrayList<>();

        if (factory == KeyframeFactories.TRANSFORM && value instanceof Transform transform)
        {
            Vector3f t = transform.translate;
            Vector3f r = transform.getEulerRotation(new Vector3f());
            Vector3f s = transform.scale;
            float rx = MathUtils.toDeg(r.x);
            float ry = MathUtils.toDeg(r.y);
            float rz = MathUtils.toDeg(r.z);

            if (!this.isZero(t.x) || !this.isZero(t.y) || !this.isZero(t.z))
            {
                lines.add("T " + this.format(t.x, t.y, t.z));
            }

            if (!this.isZero(rx) || !this.isZero(ry) || !this.isZero(rz))
            {
                lines.add("R " + this.format(rx, ry, rz) + "°");
            }

            if (!this.isZero(s.x - 1F) || !this.isZero(s.y - 1F) || !this.isZero(s.z - 1F))
            {
                lines.add("S " + this.format(s.x, s.y, s.z));
            }

            if (lines.isEmpty())
            {
                lines.add("default");
            }
        }
        else if (value instanceof Vector3f v)
        {
            lines.add(this.format(v.x, v.y, v.z));
        }
        else if (value instanceof Vector4f v)
        {
            lines.add(this.format(v.x, v.y, v.z) + " " + UINumericInput.format(v.w));
        }
        else if (value instanceof Number number)
        {
            lines.add(UINumericInput.format(number.doubleValue()));
        }
        else if (value instanceof Boolean b)
        {
            lines.add(b.toString());
        }
        else if (factory == KeyframeFactories.STRING && value instanceof String s && !s.isEmpty())
        {
            lines.add(s);
        }

        return lines;
    }

    private void renderLines(UIContext context, List<String> lines)
    {
        FontRenderer font = context.batcher.getFont();
        int w = 0;
        int h = lines.size() * (font.getHeight() + 4) - 4;

        for (String line : lines)
        {
            w = Math.max(w, font.getWidth(line));
        }

        Area area = this.start(context, w, h);
        int y = area.y;

        for (String line : lines)
        {
            context.batcher.textShadow(line, area.x, y, Colors.WHITE);

            y += font.getHeight() + 4;
        }
    }

    private boolean isZero(float value)
    {
        return Math.abs(value) < EPSILON;
    }

    private String format(float x, float y, float z)
    {
        return UINumericInput.format(x) + " " + UINumericInput.format(y) + " " + UINumericInput.format(z);
    }
}
