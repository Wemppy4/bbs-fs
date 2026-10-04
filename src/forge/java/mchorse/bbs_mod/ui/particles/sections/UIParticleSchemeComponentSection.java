package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;

public abstract class UIParticleSchemeComponentSection <T extends ParticleComponentBase> extends UIParticleSchemeSection
{
    protected T component;

    public UIParticleSchemeComponentSection(UIParticleSchemePanel parent)
    {
        super(parent);
    }

    @Override
    public void setScheme(ParticleScheme scheme)
    {
        super.setScheme(scheme);

        this.component = this.getComponent(scheme);
        this.fillData();
    }

    protected abstract T getComponent(ParticleScheme scheme);

    protected void fillData()
    {}
}