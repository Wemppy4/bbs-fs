package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class SubFormSection extends FormSection
{
    /** The icon every category of this section wears in a form list. */
    protected abstract Icon getIcon();

    protected Map<String, FormCategory> categories = new LinkedHashMap<>();

    public SubFormSection(FormCategories parent)
    {
        super(parent);
    }

    protected abstract IKey getTitle();

    protected abstract Form create(String key);

    protected FormCategory createCategory(IKey uiKey, String id)
    {
        return new FormCategory(uiKey, this.parent.preferences.visible(id)).icon(this.getIcon());
    }

    protected abstract boolean isEqual(Form form, String key);

    protected String getKey(String key)
    {
        int slash = key.lastIndexOf('/');

        return slash >= 0 ? key.substring(0, slash) : "";
    }

    protected FormCategory getCategory(String key)
    {
        String newKey = this.getKey(key);

        return this.categories.computeIfAbsent(newKey, (k) ->
        {
            IKey uiKey = this.getTitle();

            if (!newKey.isEmpty())
            {
                uiKey = IKey.comp(Arrays.asList(uiKey, IKey.constant(" (" + newKey + ")")));
            }

            return this.createCategory(uiKey, key);
        });
    }

    protected void add(String key)
    {
        FormCategory category = this.getCategory(key);

        for (Form form : category.getForms())
        {
            if (this.isEqual(form, key))
            {
                return;
            }
        }

        category.addForm(this.create(key));
    }

    protected void remove(String key)
    {
        FormCategory category = this.getCategory(key);
        Iterator<Form> it = category.getDirectForms().iterator();

        while (it.hasNext())
        {
            if (this.isEqual(it.next(), key))
            {
                it.remove();
                this.parent.markDirty();
            }
        }

        if (category.getForms().isEmpty())
        {
            this.categories.remove(this.getKey(key));
            this.parent.markDirty();
        }
    }

    @Override
    public List<FormCategory> getCategories()
    {
        return new ArrayList<>(this.categories.values());
    }
}