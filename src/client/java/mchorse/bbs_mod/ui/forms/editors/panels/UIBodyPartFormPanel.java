package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.Pair;

/**
 * Body part panel
 *
 * This panel edits how a form is attached to its parent form &mdash; whether it follows the target
 * entity, the bone it sticks to, and its local transform. It's only registered by {@link UIForm}
 * when the edited form actually has a parent body part (see {@link UIForm#startEdit}), so the root
 * form never shows it. Picking/replacing the attached form itself lives in the forms sidebar (so it
 * stays reachable for an empty body part, which has no editor tab yet).
 */
public class UIBodyPartFormPanel extends UIFormPanel
{
    public UIToggle useTarget;
    public UIStringList bone;
    public UIPropTransform transform;

    public UIBodyPartFormPanel(UIForm editor)
    {
        super(editor);

        this.useTarget = new UIToggle(UIKeys.FORMS_EDITOR_USE_TARGET, (b) ->
        {
            BodyPart part = this.getPart();

            if (part != null)
            {
                part.useTarget.set(b.getValue());
            }
        });

        this.bone = new UIStringList((l) ->
        {
            BodyPart part = this.getPart();

            if (part != null)
            {
                part.bone.set(l.get(0));
            }
        });
        this.bone.background().h(UIConstants.LIST_ITEM_HEIGHT * 6);

        this.transform = new UIPropTransform().callbacks(() ->
        {
            BodyPart part = this.getPart();

            return part == null ? null : part.transform;
        }).barBackground();
        this.transform.enableHotkeys();
        this.transform.hotkeyDrag(() -> this.editor.editor == null ? null : this.editor.editor.buildHotkeyDrag(this.transform));
    }

    private BodyPart getPart()
    {
        return this.form != null && this.form.getParent() instanceof BodyPart part ? part : null;
    }

    @Override
    public void startEdit(Form form)
    {
        super.startEdit(form);

        BodyPart part = this.getPart();

        this.options.removeAll();

        if (part == null)
        {
            return;
        }

        Form owner = part.getManager() == null ? null : part.getManager().getOwner();

        this.useTarget.setValue(part.useTarget.get());
        this.bone.clear();
        this.bone.add(FormUtilsClient.getBones(owner));
        this.bone.sort();
        this.bone.setCurrentScroll(part.bone.get());

        if (!this.bone.getList().isEmpty())
        {
            this.options.add(this.useTarget, UI.label(UIKeys.FORMS_EDITOR_BONE).marginTop(UIConstants.SECTION_GAP), this.bone, this.transform);
        }
        else
        {
            this.options.add(this.useTarget, this.transform);
        }

        this.transform.setTransform(part.transform.get());

        this.options.resize();
    }

    /**
     * Ctrl + clicking a bone of the parent form in the viewport to pick the bone this
     * body part attaches to. Validated against the part's owner so a click on an
     * unrelated form is ignored.
     */
    public void pickBone(Pair<Form, String> pair)
    {
        BodyPart part = this.getPart();

        if (part != null && this.bone.getList().contains(pair.b) && part.getManager().getOwner() == pair.a)
        {
            part.bone.set(pair.b);
            this.bone.setCurrentScroll(pair.b);
        }
    }
}
