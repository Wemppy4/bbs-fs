package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.colors.Color;

public class LabelForm extends Form
{
    /** Also what its main tab in the form editor wears — see {@link Form#getIcon()}. */
    public static final Icon ICON = Icons.FONT;

    public final ValueString text = new ValueString("text", "Hello, World!");

    /* Font: a TrueType file in the assets, empty for Minecraft's own one */
    public final ValueLink font = new ValueLink("font", null);
    public final ValueInt fontSize = new ValueInt("fontSize", 9);
    /** 0 hands the spacing over to the font itself. */
    public final ValueInt lineHeight = new ValueInt("lineHeight", 0);

    public final ValueBoolean billboard = new ValueBoolean("billboard", false);
    public final ValueColor color = new ValueColor("color", Color.white());

    public final ValueInt max = new ValueInt("max", -1);
    public final ValueFloat anchorX = new ValueFloat("anchorX", 0.5F);
    public final ValueFloat anchorY = new ValueFloat("anchorY", 0.5F);
    public final ValueBoolean anchorLines = new ValueBoolean("anchorLines", false);

    /* Shadow properties */
    public final ValueFloat shadowX = new ValueFloat("shadowX", 1F);
    public final ValueFloat shadowY = new ValueFloat("shadowY", 1F);
    public final ValueColor shadowColor = new ValueColor("shadowColor", new Color(0, 0, 0, 0));

    /* Background */
    public final ValueColor background = new ValueColor("background", new Color(0, 0, 0, 0));
    public final ValueFloat offset = new ValueFloat("offset", 3F);

    public LabelForm()
    {
        super();

        this.add(this.text);
        this.add(this.font);
        this.add(this.fontSize);
        this.add(this.lineHeight);
        this.add(this.billboard);
        this.add(this.color);
        this.add(this.max);
        this.add(this.anchorX);
        this.add(this.anchorY);
        this.add(this.anchorLines);
        this.add(this.shadowX);
        this.add(this.shadowY);
        this.add(this.shadowColor);
        this.add(this.background);
        this.add(this.offset);
    }

    @Override
    public String getDefaultDisplayName()
    {
        return this.text.get();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }

}