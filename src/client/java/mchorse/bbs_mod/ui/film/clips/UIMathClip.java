package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.MathClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.film.utils.UITextboxHelp;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIMathClip extends UIClip<MathClip>
{
    public UITextboxHelp expression;
    public UIBitToggle active;

    public UIMathClip(MathClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.expression = new UITextboxHelp(1000, (str) ->
        {
            this.clip.expression.setExpression(str);
            this.expression.setColor(!this.clip.expression.isErrored() ? Colors.WHITE : Colors.RED);
        });
        this.expression.link("https://github.com/mchorse/aperture/wiki/Math-Expressions").tooltip(UIKeys.CAMERA_PANELS_MATH);

        this.active = new UIBitToggle((value) -> this.clip.active.set(value)).all();
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_EXPRESSION, this.expression).marginTop(UIConstants.SECTION_GAP));
        this.panels.add(this.active);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.expression.setText(this.clip.expression.toString());
        this.expression.setColor(Colors.WHITE);
        this.active.setValue(this.clip.active.get());
    }
}