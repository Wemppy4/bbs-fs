package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.RecentFormCategory;

import java.util.Collections;
import java.util.List;

public class RecentFormSection extends FormSection
{
    private RecentFormCategory recent;

    public RecentFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        this.recent = new RecentFormCategory(this.parent.preferences.visible("recent"));
    }

    @Override
    public List<FormCategory> getCategories()
    {
        return Collections.singletonList(this.recent);
    }
}