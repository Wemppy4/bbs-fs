package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.particles.components.lifetime.ParticleComponentLifetime;
import mchorse.bbs_mod.particles.components.lifetime.ParticleComponentLifetimeExpression;
import mchorse.bbs_mod.particles.components.lifetime.ParticleComponentLifetimeLooping;
import mchorse.bbs_mod.particles.components.lifetime.ParticleComponentLifetimeOnce;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIParticleSchemeLifetimeSection extends UIParticleSchemeModeSection<ParticleComponentLifetime>
{
    public UIMolangExpression active;
    public UIMolangExpression expiration;

    public UIParticleSchemeLifetimeSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.active = new UIMolangExpression(() -> this.component.activeTime, (b) ->
        {
            this.editMoLang("lifetime.active_time", (str) -> this.component.activeTime = this.parse(str, this.component.activeTime), this.component.activeTime);
        });
        this.active.icon(Icons.TIME).tooltip(IKey.EMPTY);
        this.expiration = new UIMolangExpression(() -> this.component instanceof ParticleComponentLifetimeLooping
            ? ((ParticleComponentLifetimeLooping) this.component).sleepTime
            : ((ParticleComponentLifetimeExpression) this.component).expiration, (b) ->
        {
            if (this.component instanceof ParticleComponentLifetimeLooping)
            {
                ParticleComponentLifetimeLooping component = (ParticleComponentLifetimeLooping) this.component;

                this.editMoLang("lifetime.sleep_time", (str) -> component.sleepTime = this.parse(str, component.sleepTime), component.sleepTime);
            }
            else
            {
                ParticleComponentLifetimeExpression component = (ParticleComponentLifetimeExpression) this.component;

                this.editMoLang("lifetime.expiration", (str) -> component.expiration = this.parse(str, component.expiration), component.expiration);
            }

            this.editor.dirty();
        });
        this.expiration.icon(Icons.STOPWATCH).tooltip(IKey.EMPTY);

        this.fields.add(this.active);
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.SNOWSTORM_LIFETIME_TITLE;
    }

    @Override
    protected void fillModes(UICirculate button)
    {
        button.addLabel(UIKeys.SNOWSTORM_LIFETIME_EXPRESSION);
        button.addLabel(UIKeys.SNOWSTORM_LIFETIME_LOOPING);
        button.addLabel(UIKeys.SNOWSTORM_LIFETIME_ONCE);
    }

    @Override
    protected void restoreInfo(ParticleComponentLifetime component, ParticleComponentLifetime old)
    {
        component.activeTime = old.activeTime;
    }

    @Override
    protected Class<ParticleComponentLifetime> getBaseClass()
    {
        return ParticleComponentLifetime.class;
    }

    @Override
    protected Class getDefaultClass()
    {
        return ParticleComponentLifetimeLooping.class;
    }

    @Override
    protected Class getModeClass(int value)
    {
        if (value == 0)
        {
            return ParticleComponentLifetimeExpression.class;
        }
        else if (value == 1)
        {
            return ParticleComponentLifetimeLooping.class;
        }

        return ParticleComponentLifetimeOnce.class;
    }

    @Override
    protected void fillData()
    {
        super.fillData();

        boolean once = this.component instanceof ParticleComponentLifetimeOnce;

        this.expiration.setVisible(!once);

        if (this.component instanceof ParticleComponentLifetimeExpression)
        {
            this.expiration.tooltip(UIKeys.SNOWSTORM_LIFETIME_EXPIRATION_EXPRESSION);
            this.active.tooltip(UIKeys.SNOWSTORM_LIFETIME_ACTIVE_EXPRESSION);
        }
        else if (this.component instanceof ParticleComponentLifetimeLooping)
        {
            this.expiration.tooltip(UIKeys.SNOWSTORM_LIFETIME_SLEEP_TIME);
            this.active.tooltip(UIKeys.SNOWSTORM_LIFETIME_ACTIVE_LOOPING);
        }
        else
        {
            this.active.tooltip(UIKeys.SNOWSTORM_LIFETIME_ACTIVE_ONCE);
        }

        this.expiration.removeFromParent();

        if (!once)
        {
            this.fields.add(this.expiration);
        }

        this.resizeParent();
    }
}