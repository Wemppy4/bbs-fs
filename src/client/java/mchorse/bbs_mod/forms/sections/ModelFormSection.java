package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.ModelFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ModelFormSection extends SubFormSection
{
    public ModelFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        this.categories.clear();

        List<String> keys = BBSModClient.getModels().getAvailableKeys();

        keys.sort(String::compareToIgnoreCase);

        for (String key : keys)
        {
            this.add(key);
        }
    }

    @Override
    protected IKey getTitle()
    {
        return UIKeys.FORMS_CATEGORIES_MODELS;
    }

    @Override
    protected Form create(String key)
    {
        ModelForm form = new ModelForm();

        form.model.set(key);

        return form;
    }

    @Override
    protected FormCategory createCategory(IKey uiKey, String id)
    {
        return new ModelFormCategory(uiKey, this.parent.visibility.get("models_" + id));
    }

    @Override
    protected boolean isEqual(Form form, String key)
    {
        ModelForm modelForm = (ModelForm) form;

        return Objects.equals(modelForm.model.get(), key);
    }

    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        File file = path.toFile();
        Link link = BBSMod.getProvider().getLink(file);

        if (file.isDirectory())
        {
            this.initiate();
            this.parent.markDirty();
        }
        else if (link.path.startsWith(ModelManager.MODELS_PREFIX))
        {
            String extension = this.getExtension(link);

            if (extension == null)
            {
                return;
            }

            String key = link.path.substring(ModelManager.MODELS_PREFIX.length());

            key = key.substring(0, key.length() - extension.length());

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

    private String getExtension(Link link)
    {
        if (BBSModClient.getModels().isRelodable(link))
        {
            return link.path.substring(link.path.lastIndexOf('/') + 1);
        }

        return null;
    }
}