package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.motion.MotionComponents;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * Shared base for the position ({@link UIParticleSchemeMotionSection}) and rotation
 * ({@link UIParticleSchemeRotationSection}) sections. Each owns one axis with an independent
 * dynamic/parametric toggle backed by {@link MotionComponents}. The two are linked so that changing
 * one axis re-syncs the other (the dynamic and parametric components are shared between them).
 */
public abstract class UIParticleSchemeMotionAxisSection extends UIParticleSchemeSection
{
    public UIIcons mode;
    public UILabel modeLabel;

    protected UIParticleSchemeMotionAxisSection sibling;

    public UIParticleSchemeMotionAxisSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.mode = new UIIcons((b) -> this.changeMode(this.mode.getValue() == 1));
        this.mode.add(Icons.ALL_DIRECTIONS, UIKeys.SNOWSTORM_MOTION_DYNAMIC);
        this.mode.add(Icons.GRAPH, UIKeys.SNOWSTORM_MOTION_PARAMETRIC);
        this.modeLabel = UI.label(UIKeys.SNOWSTORM_MODE, 20).labelAnchor(0, 0.5F);

        this.fields.add(UI.row(5, 0, 20, this.modeLabel, this.mode));
    }

    public void link(UIParticleSchemeMotionAxisSection sibling)
    {
        this.sibling = sibling;
    }

    private void changeMode(boolean parametric)
    {
        this.applyMode(parametric);
        this.refresh();

        if (this.sibling != null)
        {
            this.sibling.refresh();
        }

        this.dirty();

        /* Adding/removing a motion component is structural — restart so live particles adopt the new mode. */
        this.editor.restartEmitter();
    }

    private void refresh()
    {
        if (this.scheme != null)
        {
            this.setScheme(this.scheme);
        }
    }

    @Override
    public void setScheme(ParticleScheme scheme)
    {
        super.setScheme(scheme);

        /* Make sure the components backing the current modes exist (e.g. for a fresh particle). */
        MotionComponents.setModes(scheme, MotionComponents.isPositionParametric(scheme), MotionComponents.isRotationParametric(scheme));

        this.mode.setValue(this.isParametric() ? 1 : 0);
        this.fillFields();
        this.resizeParent();
    }

    /** Whether this section's axis is currently in parametric mode. */
    protected abstract boolean isParametric();

    /** Switch this section's axis to the given mode (preserving the other axis). */
    protected abstract void applyMode(boolean parametric);

    /** Show/hide the mode-dependent fields. */
    protected abstract void fillFields();
}
