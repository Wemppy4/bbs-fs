package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.nio.file.Path;
import java.util.Objects;

public class ParticleFormSection extends SubFormSection
{
    public ParticleFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    protected Icon getIcon()
    {
        return Icons.PARTICLE;
    }

    @Override
    public void initiate()
    {
        for (String key : BBSModClient.getParticles().getKeys())
        {
            this.add(key);
        }
    }

    @Override
    protected IKey getTitle()
    {
        return UIKeys.FORMS_CATEGORIES_PARTICLES;
    }

    @Override
    protected Form create(String key)
    {
        ParticleForm form = new ParticleForm();

        form.effect.set(key);

        return form;
    }

    @Override
    protected boolean isEqual(Form form, String key)
    {
        ParticleForm particleForm = (ParticleForm) form;

        return Objects.equals(particleForm.effect.get(), key);
    }

    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        Link link = BBSMod.getProvider().getLink(path.toFile());

        if (link.path.startsWith("particles/") && link.path.endsWith(".json"))
        {
            String key = link.path.substring("particles/".length());

            key = key.substring(0, key.length() - ".json".length());

            if (event == WatchDogEvent.DELETED)
            {
                this.remove(key);
            }
            else
            {
                this.add(key);
            }

            this.parent.markDirty();
        }
    }
}