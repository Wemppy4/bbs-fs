package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.PoseForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIPoseFormPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.StringUtils;
import org.joml.Matrix4f;

/** Shared selection, undo and gizmo behavior for form types with editable bone poses. */
public abstract class UIPoseForm <T extends Form & PoseForm> extends UIForm<T>
{
    private UIPoseFormPanel<T> posePanel;

    protected final void setupPosePanel(UIPoseFormPanel<T> posePanel)
    {
        this.posePanel = posePanel;
        posePanel.poseEditor.transform.hotkeyDrag(() -> this.editor == null ? null : this.editor.buildHotkeyDrag(posePanel.poseEditor.transform));
        posePanel.poseEditor.transform.worldTransform(new FormBoneWorldProvider(this));
    }

    public UIPoseEditor getPoseEditor()
    {
        return this.posePanel.poseEditor;
    }

    @Override
    public UIPropTransform getEditableTransform()
    {
        return this.posePanel.poseEditor.transform;
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.put("bones", DataStorageUtils.stringListToData(this.posePanel.poseEditor.groups.list.getCurrent()));
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.has("bones"))
        {
            this.posePanel.poseEditor.restoreSelection(DataStorageUtils.stringListFromData(data.get("bones")));
        }
    }

    @Override
    public Matrix4f getOrigin(float transition)
    {
        return this.getOrigin(transition, this.bonePath(), this.posePanel.poseEditor.transform.isLocal());
    }

    @Override
    public Matrix4f getOriginMatrix(float transition)
    {
        return this.getOrigin(transition, this.bonePath(), true);
    }

    private String bonePath()
    {
        return StringUtils.combinePaths(FormUtils.getPath(this.form), this.posePanel.poseEditor.groups.list.getCurrentFirst());
    }

    @Override
    public boolean toggleBoneSelection(String bone)
    {
        if (!this.posePanel.poseEditor.hasBone(bone))
        {
            return false;
        }

        this.posePanel.poseEditor.selectBone(bone, true);

        return true;
    }
}
