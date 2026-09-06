package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UIParticleSettings;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UIVanillaParticleFormPanel extends UIFormPanel<VanillaParticleForm>
{
    /** Not a bound value: the settings editor is filled from the form's particle settings. */
    public UIParticleSettings settings;

    public UIVanillaParticleFormPanel(UIForm editor)
    {
        super(editor);

        this.settings = new UIParticleSettings();

        UITrackpad count = UIValues.trackpad(() -> this.form.count).integer();
        UITrackpad frequency = UIValues.trackpad(() -> this.form.frequency).integer();
        UITrackpad scatteringYaw = UIValues.trackpad(() -> this.form.scatteringYaw);
        UITrackpad scatteringPitch = UIValues.trackpad(() -> this.form.scatteringPitch);
        UITrackpad offsetX = UIValues.trackpad(() -> this.form.offsetX);
        UITrackpad offsetY = UIValues.trackpad(() -> this.form.offsetY);
        UITrackpad offsetZ = UIValues.trackpad(() -> this.form.offsetZ);

        count.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_COUNT);
        frequency.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_FREQUENCY);
        scatteringYaw.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_HORIZONTAL);
        scatteringPitch.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_VERTICAL);
        offsetX.tooltip(UIKeys.GENERAL_X);
        offsetY.tooltip(UIKeys.GENERAL_Y);
        offsetZ.tooltip(UIKeys.GENERAL_Z);

        this.options.add(
            this.settings,
            UIValues.toggle(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_PAUSED, () -> this.form.paused).marginTop(UIConstants.SECTION_GAP),
            UIValues.toggle(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_LOCAL, () -> this.form.local),
            UI.labelRow(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_VELOCITY, UIValues.trackpad(() -> this.form.velocity)).marginTop(UIConstants.SECTION_GAP)
        );

        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_EMISSION).marginTop(UIConstants.SECTION_GAP), UI.row(count, frequency));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_SCATTER).marginTop(UIConstants.SECTION_GAP), UI.row(scatteringYaw, scatteringPitch));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_OFFSET).marginTop(UIConstants.SECTION_GAP), UI.row(offsetX, offsetY, offsetZ));
    }

    @Override
    public void startEdit(VanillaParticleForm form)
    {
        super.startEdit(form);

        this.settings.setSettings(form.settings.get());
    }
}
