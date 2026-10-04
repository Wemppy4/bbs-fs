package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.meta.ParticleComponentInitialization;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIParticleSchemeInitializationSection extends UIParticleSchemeComponentSection<ParticleComponentInitialization>
{
    public UIMolangExpression create;
    public UIMolangExpression update;

    public UIParticleSchemeInitializationSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.create = new UIMolangExpression(() -> this.component.creation, (b) -> this.editMoLang("initialization.create", (str) -> this.component.creation = this.parse(str, this.component.creation), this.component.creation));
        this.create.icon(Icons.ADD).tooltip(UIKeys.SNOWSTORM_INITIALIZATION_CREATION_TOOLTIP);
        this.update = new UIMolangExpression(() -> this.component.update, (b) -> this.editMoLang("initialization.update", (str) -> this.component.update = this.parse(str, this.component.update), this.component.update));
        this.update.icon(Icons.REFRESH).tooltip(UIKeys.SNOWSTORM_INITIALIZATION_UPDATE_TOOLTIP);

        this.fields.add(this.create, this.update);
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.SNOWSTORM_INITIALIZATION_TITLE;
    }

    @Override
    protected ParticleComponentInitialization getComponent(ParticleScheme scheme)
    {
        return this.scheme.getOrCreate(ParticleComponentInitialization.class);
    }
}