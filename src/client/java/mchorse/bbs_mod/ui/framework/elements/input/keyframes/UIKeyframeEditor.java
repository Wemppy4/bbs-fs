package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class UIKeyframeEditor extends UIElement
{
    public static final int[] COLORS = {Colors.RED, Colors.GREEN, Colors.BLUE, Colors.CYAN, Colors.MAGENTA, Colors.YELLOW, Colors.LIGHTEST_GRAY & 0xffffff, Colors.DEEP_PINK};

    public UIKeyframes view;
    public UIKeyframeFactory editor;

    private UIElement target;
    private boolean timelineVisible = true;
    private boolean propertiesVisible = true;

    public UIKeyframeEditor(Function<Consumer<Keyframe>, UIKeyframes> factory)
    {
        this.view = factory.apply(this::pickKeyframe);
        this.view.changed(() ->
        {
            if (this.editor != null)
            {
                this.editor.update();
            }
        });

        this.add(this.view.full(this).w(1F, -140));
    }

    /**
     * The parameters panel is parented to {@link #target}, not to this editor, so nothing would take
     * it down when this editor is dropped &mdash; it would stay in the edit area, clickable, and the
     * next editor would stack its own panel on top of it.
     */
    @Override
    public void removeFromParent()
    {
        super.removeFromParent();

        if (this.editor != null)
        {
            this.editor.removeFromParent();
        }
    }

    public UIKeyframeEditor target(UIElement target)
    {
        this.target = target;

        this.view.resetFlex().full(this).w(1F);

        return this;
    }

    private void pickKeyframe(Keyframe keyframe)
    {
        UIKeyframeFactory.saveScroll(this.editor);

        if (this.editor != null)
        {
            this.editor.removeFromParent();
            this.editor = null;
        }

        if (keyframe != null)
        {
            this.editor = UIKeyframeFactory.createPanel(keyframe, this.view);

            if (this.target != null)
            {
                this.editor.relative(this.target).x(0).y(0).w(1F).h(1F);
            }
            else
            {
                this.editor.relative(this).x(1F, -140).w(140).h(1F);
            }

            /* The panel lives in whichever element it is laid out over, so it stays visible when
             * the timeline is hidden behind another dock tab. */
            (this.target == null ? this : this.target).add(this.editor);
            this.editor.setVisible(this.propertiesVisible);
            this.resize();

            if (this.target != null)
            {
                this.target.resize();
                this.editor.resize();
            }
        }

        this.resize();

        if (this.editor != null)
        {
            this.editor.restoreScroll();
        }
    }

    public void setTimelineVisible(boolean visible)
    {
        this.timelineVisible = visible;
        this.view.setVisible(visible);
    }

    public void setPropertiesVisible(boolean visible)
    {
        this.propertiesVisible = visible;

        if (this.editor != null)
        {
            this.editor.setVisible(visible);
        }
    }

    public void setChannel(KeyframeChannel channel, int color)
    {
        this.view.removeAllSheets();
        this.view.addSheet(new UIKeyframeSheet(color, false, channel, null));

        this.pickKeyframe(null);
    }

    public void setClip(KeyframeClip clip)
    {
        this.view.removeAllSheets();

        for (int i = 0; i < clip.channels.length; i++)
        {
            KeyframeChannel channel = clip.channels[i];

            this.view.addSheet(new UIKeyframeSheet(COLORS[i], false, channel, null));
        }

        this.pickKeyframe(null);
    }

    public UIKeyframeSheet getSheet(Keyframe keyframe)
    {
        if (keyframe == null)
        {
            return null;
        }

        for (UIKeyframeSheet sheet : this.view.getGraph().getSheets())
        {
            if (sheet.channel == keyframe.getParent())
            {
                return sheet;
            }
        }

        return null;
    }

    public Pair<String, Boolean> getBone()
    {
        UIKeyframeFactory editor = this.editor;
        String bone = null;
        boolean local = false;

        if (editor instanceof UIPoseKeyframeFactory pose)
        {
            UIKeyframeSheet sheet = this.getSheet(editor.getKeyframe());
            String currentFirst = pose.poseEditor.groups.list.getCurrentFirst();

            if (sheet != null)
            {
                String id = StringUtils.fileName(sheet.id);

                if (id.startsWith("pose"))
                {
                    PerLimbService.PoseBonePath path = PerLimbService.parsePoseBonePath(sheet.id);
                    if (path != null)
                        bone = path.formPath().isEmpty() ? currentFirst : path.formPath() + "/" + currentFirst;
                    else
                    {
                        int i = sheet.id.lastIndexOf('/');
                        bone = i >= 0 ? sheet.id.substring(0, i + 1) + currentFirst : currentFirst;
                    }
                    local = pose.poseEditor.transform.isLocal();
                }
            }
        }
        else if (editor instanceof UITransformKeyframeFactory transform)
        {
            UIKeyframeSheet sheet = this.getSheet(editor.getKeyframe());

            if (sheet != null)
            {
                String id = StringUtils.fileName(sheet.id);

                PerLimbService.PoseBonePath poseBonePath = PerLimbService.parsePoseBonePath(sheet.id);

                if (poseBonePath != null)
                {
                    bone = poseBonePath.formPath().isEmpty() ? poseBonePath.bone() : poseBonePath.formPath() + "/" + poseBonePath.bone();
                    local = transform.transform.isLocal();
                }
                else if (id.startsWith("transform"))
                {
                    int i = sheet.id.lastIndexOf('/');

                    bone = i >= 0 ? sheet.id.substring(0, i) : "";
                    local = transform.transform.isLocal();
                }
            }
        }
        else if (editor instanceof UIPoseTransformKeyframeFactory poseTransform)
        {
            UIKeyframeSheet sheet = this.getSheet(editor.getKeyframe());

            if (sheet != null)
            {
                PerLimbService.PoseBonePath poseBonePath = PerLimbService.parsePoseBonePath(sheet.id);

                if (poseBonePath != null)
                {
                    bone = poseBonePath.formPath().isEmpty() ? poseBonePath.bone() : poseBonePath.formPath() + "/" + poseBonePath.bone();
                    local = poseTransform.transform.isLocal();
                }
            }
        }

        if (bone != null)
        {
            return new Pair<>(bone, local);
        }

        return null;
    }

    /** The space of the active editable transform (mirrors
     *  {@code UIReplaysEditorUtils.getEditableTransform}'s dispatch — the bone
     *  tracks AND the form anchor), so the film gizmo is drawn in the very space
     *  its drag operates in. */
    public TransformSpace getBoneSpace()
    {
        UIKeyframeFactory editor = this.editor;

        if (editor instanceof UIPoseKeyframeFactory pose)
        {
            return pose.poseEditor.transform.getSpace();
        }
        else if (editor instanceof UITransformKeyframeFactory transform)
        {
            return transform.transform.getSpace();
        }
        else if (editor instanceof UIPoseTransformKeyframeFactory poseTransform)
        {
            return poseTransform.transform.getSpace();
        }
        else if (editor instanceof UIAnchorKeyframeFactory anchor)
        {
            return anchor.transform.getSpace();
        }

        return TransformSpace.LOCAL;
    }

    /**
     * Whether the active editor is the form's "anchor" property track — the one
     * that re-parents the whole form to another replay's attachment and carries
     * a {@link mchorse.bbs_mod.utils.pose.Transform} offset the gizmo can edit.
     * The IK/pole/physics target tracks reuse the {@code Anchor} value type but
     * are created without a backing property, so the {@code property != null}
     * test excludes them; the {@code "anchor"} id keeps it to the root form's
     * track, whose placement {@link mchorse.bbs_mod.film.BaseFilmController}
     * resolves from the entity's own {@code form.anchor}.
     */
    public boolean isFormAnchorTrack()
    {
        if (!(this.editor instanceof UIAnchorKeyframeFactory))
        {
            return false;
        }

        UIKeyframeSheet sheet = this.getSheet(this.editor.getKeyframe());

        return sheet != null && sheet.property != null && "anchor".equals(sheet.id);
    }

    /** Whether the anchor gizmo should be oriented in the bone's local space (mirrors {@link #getBone()}'s flag). */
    public boolean getAnchorLocal()
    {
        return this.editor instanceof UIAnchorKeyframeFactory factory && factory.transform.isLocal();
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        KeyframeState state = new KeyframeState();

        state.extra = data.getMap("extra");

        for (BaseType type : data.getList("selection"))
        {
            state.selected.add(DataStorageUtils.intListFromData(type));
        }

        this.view.applyState(state);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        KeyframeState keyframeState = this.view.cacheState();
        ListType selection = new ListType();

        for (List<Integer> integers : keyframeState.selected)
        {
            selection.add(DataStorageUtils.intListToData(integers));
        }

        data.put("extra", keyframeState.extra);
        data.put("selection", selection);
    }
}
