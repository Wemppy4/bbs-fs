package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
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
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ModelFormSection extends SubFormSection
{
    @Override
    protected Icon getIcon()
    {
        return Icons.POSE;
    }

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
        return new ModelFormCategory(uiKey, this.parent.preferences.visible("models_" + id));
    }

    @Override
    protected boolean isEqual(Form form, String key)
    {
        ModelForm modelForm = (ModelForm) form;

        return Objects.equals(modelForm.model.get(), key);
    }

    /** When the models tree was last rescanned — one full rescan per event burst, not per event. */
    private long lastStructureScan;

    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        File file = path.toFile();
        Link link = BBSMod.getProvider().getLink(file);

        if (link == null || !link.path.startsWith(ModelManager.MODELS_PREFIX))
        {
            return;
        }

        boolean reloadable = BBSModClient.getModels().isRelodable(link);

        /* A folder appearing rearranges the tree, and so does a delete that is not one of a model's
         * files: a removed model folder arrives here as a path that is no longer a directory, so it
         * would otherwise fall through every branch and the model would sit in the palette until the
         * next world load. A batch of such events lands within one flush and the rescan walks the whole
         * tree anyway, so once covers them all. */
        if (file.isDirectory() || (event == WatchDogEvent.DELETED && !reloadable))
        {
            long now = System.currentTimeMillis();

            if (now - this.lastStructureScan > 100)
            {
                this.initiate();
            }

            this.lastStructureScan = now;
            this.parent.markDirty();

            return;
        }

        if (!reloadable)
        {
            return;
        }

        /* A model is the FOLDER, not the file in it: a .jem sits beside its .jpm, an .obj beside its
         * .mtl, and the palette lists one entry per folder - the key getAvailableKeys builds. Taking the
         * file name off the path instead left a trailing slash on the key, which filed the model under a
         * category of its own name and gave the entry a model id nothing could load. Formats whose
         * loader makes texture folders hid it: those folder events triggered the rescan above, which
         * rebuilds the list correctly. A .jem folder makes no folders, so nothing ever corrected it. */
        String key = StringUtils.parentPath(link.path.substring(ModelManager.MODELS_PREFIX.length()));

        if (key.isEmpty())
        {
            return;
        }

        /* One deleted file of several is not a deleted model, and one saved file may be the first of a
         * model that is only now appearing - so ask the folder rather than the event. */
        if (this.hasModel(key))
        {
            this.add(key);
        }
        else
        {
            this.remove(key);
        }

        this.parent.markDirty();
    }

    /** Whether that model folder still holds a file some loader would read. */
    private boolean hasModel(String key)
    {
        ModelManager models = BBSModClient.getModels();

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets(ModelManager.MODELS_PREFIX + key), true))
        {
            if (models.isRelodable(link))
            {
                return true;
            }
        }

        return false;
    }
}