package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.ui.forms.editors.utils.UIParticleSettings;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import net.minecraft.util.ResourceLocation;

public class UIParticleSettingsKeyframeFactory extends UIKeyframeFactory<ParticleSettings>
{
    private UIParticleSettings settings;

    public UIParticleSettingsKeyframeFactory(UITrackValue<ParticleSettings> track, UIKeyframes editor)
    {
        super(track, editor);

        this.settings = new UIParticleSettingsEditor(this);
        this.settings.setSettings(track.getValue());

        this.scroll.add(this.settings);
    }

    @Override
    public void update()
    {
        if (!this.settings.arguments.isFocused()) this.settings.setSettings(this.getDisplayValue());
    }

    public static class UIParticleSettingsEditor extends UIParticleSettings
    {
        private final UIParticleSettingsKeyframeFactory editor;

        public UIParticleSettingsEditor(UIParticleSettingsKeyframeFactory editor)
        {
            super();

            this.editor = editor;
        }

        @Override
        protected void setParticle(ResourceLocation id)
        {
            this.editor.track.edit(value -> value.particle = id);
        }

        @Override
        protected void setArguments(String args)
        {
            this.editor.track.edit(value -> value.arguments = args);
        }
    }
}