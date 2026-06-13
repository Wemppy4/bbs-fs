package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.particles.components.rate.ParticleComponentRate;
import mchorse.bbs_mod.particles.components.rate.ParticleComponentRateInstant;
import mchorse.bbs_mod.particles.components.rate.ParticleComponentRateSteady;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIParticleSchemeRateSection extends UIParticleSchemeModeSection<ParticleComponentRate>
{
    public UIMolangExpression rate;
    public UIMolangExpression particles;

    public UIParticleSchemeRateSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.rate = new UIMolangExpression(() -> ((ParticleComponentRateSteady) this.component).spawnRate, (b) ->
        {
            ParticleComponentRateSteady comp = (ParticleComponentRateSteady) this.component;

            this.editMoLang("rate.rate", (str) -> comp.spawnRate = this.parse(str, comp.spawnRate), comp.spawnRate);
        });
        this.rate.icon(Icons.SPRAY).tooltip(UIKeys.SNOWSTORM_RATE_SPAWN_RATE);
        this.particles = new UIMolangExpression(() -> this.component.particles, (b) ->
        {
            this.editMoLang("rate.particles", (str) -> this.component.particles = this.parse(str, this.component.particles), this.component.particles);
        });
        this.particles.icon(Icons.PARTICLE);

        this.fields.add(this.particles);
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.SNOWSTORM_RATE_TITLE;
    }

    @Override
    protected void fillModes(UICirculate button)
    {
        button.addLabel(UIKeys.SNOWSTORM_RATE_INSTANT);
        button.addLabel(UIKeys.SNOWSTORM_RATE_STEADY);
    }

    @Override
    protected void restoreInfo(ParticleComponentRate component, ParticleComponentRate old)
    {
        component.particles = old.particles;
    }

    @Override
    protected Class<ParticleComponentRate> getBaseClass()
    {
        return ParticleComponentRate.class;
    }

    @Override
    protected Class getDefaultClass()
    {
        return ParticleComponentRateInstant.class;
    }

    @Override
    protected Class getModeClass(int value)
    {
        return value == 0 ? ParticleComponentRateInstant.class : ParticleComponentRateSteady.class;
    }

    @Override
    protected void fillData()
    {
        super.fillData();

        this.updateVisibility();
        this.particles.tooltip(this.isInstant()
            ? UIKeys.SNOWSTORM_RATE_PARTICLES
            : UIKeys.SNOWSTORM_RATE_MAX_PARTICLES);
    }

    private void updateVisibility()
    {
        if (this.isInstant())
        {
            this.rate.removeFromParent();
        }
        else if (!this.rate.hasParent())
        {
            this.fields.add(this.rate);
        }

        this.resizeParent();
    }

    private boolean isInstant()
    {
        return this.component instanceof ParticleComponentRateInstant;
    }
}