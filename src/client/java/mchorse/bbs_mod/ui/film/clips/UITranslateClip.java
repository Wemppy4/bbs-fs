package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.TranslateClip;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
public class UITranslateClip extends UIClip<TranslateClip>
{
    public UIPointModule point;
    public UIBitToggle active;

    public UITranslateClip(TranslateClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.point = new UIPointModule(this.editor);
        this.active = new UIBitToggle((value) -> this.clip.active.set(value)).point();
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.point, this.active);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.point.fill(this.clip.translate);
        this.active.setValue(this.clip.active.get());
    }
}