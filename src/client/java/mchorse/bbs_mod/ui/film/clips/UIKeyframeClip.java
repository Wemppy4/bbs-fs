package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import org.joml.Vector3f;

public class UIKeyframeClip extends UIClip<KeyframeClip>
{
    public UIButton edit;
    public UIKeyframeEditor keyframes;
    public UIToggle additive;

    public UIKeyframeClip(KeyframeClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void addEnvelopes()
    {
        super.addEnvelopes();

        this.additive = this.toggle(UIKeys.CAMERA_PANELS_ADDITIVE, this.clip.additive);

        this.panels.add(this.additive);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("keyframe_keyframes");

        this.edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.registerEditKeys(this.edit, this.keyframes);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_KEYFRAMES, this.edit, this.keyFields(this.keyframes)));
    }

    @Override
    public void editClip(Position position)
    {
        Position newPos = position.copy();
        UIContext context = this.getContext();
        float tick = this.editor.getKeyframeCursor(context == null ? 0F : context.getTransition()) - this.clip.tick.get();

        if (this.clip.additive.get())
        {
            Position underneath = this.clip.getUnderneath();
            float factor = this.clip.envelope.factorEnabled(this.clip.duration.get(), tick);

            /* At zero the additive contribution is always zero. Do not rewrite
             * the reference there and accidentally change the rest of the clip. */
            if (underneath == null || tick <= 0F || factor == 0F || !this.clip.enabled.get())
            {
                return;
            }

            this.insertAdditiveKeyframe(tick, this.clip.x, (newPos.point.x - underneath.point.x) / factor);
            this.insertAdditiveKeyframe(tick, this.clip.y, (newPos.point.y - underneath.point.y) / factor);
            this.insertAdditiveKeyframe(tick, this.clip.z, (newPos.point.z - underneath.point.z) / factor);
            this.insertAdditiveKeyframe(tick, this.clip.yaw, (newPos.angle.yaw - underneath.angle.yaw) / factor);
            this.insertAdditiveKeyframe(tick, this.clip.pitch, (newPos.angle.pitch - underneath.angle.pitch) / factor);
            this.insertAdditiveKeyframe(tick, this.clip.roll, (newPos.angle.roll - underneath.angle.roll) / factor);
            this.insertAdditiveKeyframe(tick, this.clip.fov, (newPos.angle.fov - underneath.angle.fov) / factor);

            return;
        }

        if (!this.clip.distance.isEmpty())
        {
            double distance = this.clip.distance.interpolate(tick);

            if (distance != 0D)
            {
                Vector3f rotation = Matrices.rotation(
                    MathUtils.toRad(newPos.angle.pitch),
                    MathUtils.toRad(-newPos.angle.yaw - 180)
                );

                newPos.point.x -= rotation.x * distance;
                newPos.point.y -= rotation.y * distance;
                newPos.point.z -= rotation.z * distance;
            }
        }

        this.insertKeyframe(tick, this.clip.x, newPos.point.x);
        this.insertKeyframe(tick, this.clip.y, newPos.point.y);
        this.insertKeyframe(tick, this.clip.z, newPos.point.z);
        this.insertKeyframe(tick, this.clip.yaw, newPos.angle.yaw);
        this.insertKeyframe(tick, this.clip.pitch, newPos.angle.pitch);
        this.insertKeyframe(tick, this.clip.roll, newPos.angle.roll);
        this.insertKeyframe(tick, this.clip.fov, newPos.angle.fov);
    }

    private void insertAdditiveKeyframe(float tick, KeyframeChannel<Double> channel, double offset)
    {
        double reference = channel.isEmpty() ? 0D : channel.interpolate(0F);

        /* A channel without a key at zero needs a fixed reference,
         * otherwise inserting the edited key also changes interpolate(0). */
        if (channel.getKeyframes().stream().noneMatch(keyframe -> keyframe.getTick() == 0F))
        {
            this.insertKeyframe(0F, channel, reference);
        }

        this.insertKeyframe(tick, channel, reference - offset);
    }

    private void insertKeyframe(float tick, KeyframeChannel<Double> channel, double x)
    {
        KeyframeSegment<Double> segment = channel.findSegment(tick);
        int insert = channel.insert(tick, x);

        if (segment != null)
        {
            channel.get(insert).copyOverExtra(segment.a);
        }
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.updateDuration(this.clip.duration.get());
        this.keyframes.setClip(this.clip);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("keyframe"))
        {
            this.editor.embedView(this.keyframes);
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.keyframes.hasParent())
        {
            data.putString("embed", "keyframe");
        }
    }
}
