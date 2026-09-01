package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UIParticleSettings;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

public class UIVanillaParticleFormPanel extends UIFormPanel<VanillaParticleForm>
{
    public UIParticleSettings settings;
    public UIToggle paused;
    public UIToggle local;
    public UITrackpad velocity;
    public UITrackpad count;
    public UITrackpad frequency;
    public UITrackpad scatteringYaw;
    public UITrackpad scatteringPitch;
    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UITrackpad offsetZ;

    public UIVanillaParticleFormPanel(UIForm editor)
    {
        super(editor);

        this.settings = new UIParticleSettings();
        this.paused = UIValues.toggle(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_PAUSED, () -> this.form.paused);
        this.local = UIValues.toggle(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_LOCAL, () -> this.form.local);
        this.velocity = UIValues.trackpad(() -> this.form.velocity);
        this.count = UIValues.trackpad(() -> this.form.count).integer();
        this.count.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_COUNT);
        this.frequency = UIValues.trackpad(() -> this.form.frequency).integer();
        this.frequency.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_FREQUENCY);
        this.scatteringYaw = UIValues.trackpad(() -> this.form.scatteringYaw);
        this.scatteringYaw.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_HORIZONTAL);
        this.scatteringPitch = UIValues.trackpad(() -> this.form.scatteringPitch);
        this.scatteringPitch.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_VERTICAL);
        this.offsetX = UIValues.trackpad(() -> this.form.offsetX);
        this.offsetX.tooltip(UIKeys.GENERAL_X);
        this.offsetY = UIValues.trackpad(() -> this.form.offsetY);
        this.offsetY.tooltip(UIKeys.GENERAL_Y);
        this.offsetZ = UIValues.trackpad(() -> this.form.offsetZ);
        this.offsetZ.tooltip(UIKeys.GENERAL_Z);

        this.options.add(this.settings, this.paused.marginTop(UIConstants.SECTION_GAP), this.local, UI.labelRow(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_VELOCITY, this.velocity).marginTop(UIConstants.SECTION_GAP));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_EMISSION).marginTop(UIConstants.SECTION_GAP), UI.row(this.count, this.frequency));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_SCATTER).marginTop(UIConstants.SECTION_GAP), UI.row(this.scatteringYaw, this.scatteringPitch));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_OFFSET).marginTop(UIConstants.SECTION_GAP), UI.row(this.offsetX, this.offsetY, this.offsetZ));
    }

    @Override
    public void startEdit(VanillaParticleForm form)
    {
        super.startEdit(form);

        this.settings.setSettings(form.settings.get());
        this.paused.setValue(form.paused.get());
        this.local.setValue(form.local.get());
        this.velocity.setValue(form.velocity.get());
        this.count.setValue(form.count.get());
        this.frequency.setValue(form.frequency.get());
        this.scatteringYaw.setValue(form.scatteringYaw.get());
        this.scatteringPitch.setValue(form.scatteringPitch.get());
        this.offsetX.setValue(form.offsetX.get());
        this.offsetY.setValue(form.offsetY.get());
        this.offsetZ.setValue(form.offsetZ.get());
    }
}