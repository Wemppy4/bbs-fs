package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.UIBezierHandles;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.joml.Vector3f;

public class UIVector3fKeyframeFactory extends UIKeyframeFactory<Vector3f>
{
    private UITrackpad x;
    private UITrackpad y;
    private UITrackpad z;
    private UIBezierHandles handlesX;
    private UIBezierHandles handlesY;
    private UIBezierHandles handlesZ;

    public UIVector3fKeyframeFactory(Keyframe<Vector3f> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        Vector3f value = keyframe.getValue();
        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);
        String group = this.getTransformGroup(sheet);
        IKey tooltip = this.getTooltipKey(group);

        this.x = new UITrackpad((v) -> this.setValue(this.getValue()));
        this.x.block().onlyNumbers();
        if (tooltip != null) this.x.tooltip(IKey.constant("%s (%s)").format(tooltip, UIKeys.GENERAL_X));
        this.x.textbox.setColor(Colors.RED);
        this.x.setValue(value.x);
        this.y = new UITrackpad((v) -> this.setValue(this.getValue()));
        this.y.block().onlyNumbers();
        if (tooltip != null) this.y.tooltip(IKey.constant("%s (%s)").format(tooltip, UIKeys.GENERAL_Y));
        this.y.textbox.setColor(Colors.GREEN);
        this.y.setValue(value.y);
        this.z = new UITrackpad((v) -> this.setValue(this.getValue()));
        this.z.block().onlyNumbers();
        if (tooltip != null) this.z.tooltip(IKey.constant("%s (%s)").format(tooltip, UIKeys.GENERAL_Z));
        this.z.textbox.setColor(Colors.BLUE);
        this.z.setValue(value.z);
        this.handlesX = new UIBezierHandles(keyframe, 0);
        this.handlesY = new UIBezierHandles(keyframe, 1);
        this.handlesZ = new UIBezierHandles(keyframe, 2);

        if (group.equals("rotate") || group.equals("rotate2"))
        {
            this.x.degrees();
            this.y.degrees();
            this.z.degrees();
        }

        if (!group.isEmpty())
        {
            UIIcon icon = new UIIcon(this.getIcon(group), null);
            icon.disabledColor = icon.hoverColor = Colors.WHITE;
            icon.setEnabled(false);

            this.scroll.add(UI.row(icon, this.x, this.y, this.z).w(190), this.handlesX.createColumn(), this.handlesY.createColumn(), this.handlesZ.createColumn());
        }
        else
        {
            this.scroll.add(UI.row(this.x, this.y), this.z, this.handlesX.createColumn(), this.handlesY.createColumn(), this.handlesZ.createColumn());
        }
    }

    @Override
    public void update()
    {
        super.update();

        Vector3f value = this.keyframe.getValue();

        this.x.setValue(value.x);
        this.y.setValue(value.y);
        this.z.setValue(value.z);
        this.handlesX.update();
        this.handlesY.update();
        this.handlesZ.update();
    }

    private Vector3f getValue()
    {
        return new Vector3f((float) this.x.getValue(), (float) this.y.getValue(), (float) this.z.getValue());
    }

    private String getTransformGroup(UIKeyframeSheet sheet)
    {
        if (sheet == null || sheet.id == null)
        {
            return "";
        }

        int index = sheet.id.lastIndexOf(".");

        if (index == -1)
        {
            return "";
        }

        String group = sheet.id.substring(index + 1);

        return group.equals("translate") || group.equals("scale") || group.equals("rotate") || group.equals("rotate2") ? group : "";
    }

    private Icon getIcon(String group)
    {
        return switch (group)
        {
            case "translate" -> Icons.ALL_DIRECTIONS;
            case "scale" -> Icons.SCALE;
            case "rotate", "rotate2" -> Icons.REFRESH;
            default -> null;
        };
    }

    private IKey getTooltipKey(String group)
    {
        return switch (group)
        {
            case "translate" -> UIKeys.TRANSFORMS_TRANSLATE;
            case "scale" -> UIKeys.TRANSFORMS_SCALE;
            case "rotate" -> UIKeys.TRANSFORMS_ROTATE;
            case "rotate2" -> UIKeys.TRANSFORMS_ROTATE2;
            default -> null;
        };
    }
}

