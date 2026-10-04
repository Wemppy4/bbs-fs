package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.utils.UI;

public abstract class UIParticleSchemeModeSection <T extends ParticleComponentBase> extends UIParticleSchemeComponentSection<T>
{
    public UIIcons mode;
    public UILabel modeLabel;

    public UIParticleSchemeModeSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.mode = new UIIcons((b) -> this.updateMode(this.mode.getValue()));
        this.fillModes(this.mode);
        this.modeLabel = UI.label(UIKeys.SNOWSTORM_MODE, 20).labelAnchor(0, 0.5F);

        this.fields.add(UI.row(5, 0, 20, this.modeLabel, this.mode));
    }

    @Override
    protected T getComponent(ParticleScheme scheme)
    {
        return scheme.getOrCreate(this.getBaseClass(), this.getDefaultClass());
    }

    @Override
    protected void fillData()
    {
        super.fillData();

        for (int i = 0, c = this.mode.getCount(); i < c; i ++)
        {
            if (this.getModeClass(i) == this.component.getClass())
            {
                this.mode.setValue(i);

                break;
            }
        }
    }

    protected abstract void fillModes(UIIcons button);

    protected void updateMode(int value)
    {
        T old = this.component;

        this.component = this.scheme.replace(this.getBaseClass(), this.getModeClass(this.mode.getValue()));
        this.restoreInfo(this.component, old);
        this.editor.dirty();

        this.fillData();
    }

    protected void restoreInfo(T component, T old)
    {}

    protected abstract Class<T> getBaseClass();

    protected abstract Class getDefaultClass();

    protected abstract Class getModeClass(int value);
}