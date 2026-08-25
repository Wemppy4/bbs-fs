package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The forms a user has picked in a form list to act on together — drag them, copy or move
 * them to another category, remove them.
 *
 * <p>This is deliberately separate from the list's <em>selected</em> form: that one is what
 * the editor opens and what a morph wears, and there is always at most one. A pick is a
 * bookkeeping mark and may span categories. Forms are matched by identity: two equal-looking
 * forms in different categories are different picks.</p>
 */
public class FormSelection
{
    private final List<Form> forms = new ArrayList<>();

    /** Where a Shift-click range starts. */
    private Form anchor;
    private FormCategory anchorCategory;

    public boolean contains(Form form)
    {
        return this.indexOf(form) != -1;
    }

    public boolean isEmpty()
    {
        return this.forms.isEmpty();
    }

    /** More than one form — the state in which cell actions and menus act on the whole pick. */
    public boolean isGroup()
    {
        return this.forms.size() > 1;
    }

    public int size()
    {
        return this.forms.size();
    }

    public List<Form> getForms()
    {
        return Collections.unmodifiableList(this.forms);
    }

    public Form getAnchor()
    {
        return this.anchor;
    }

    public FormCategory getAnchorCategory()
    {
        return this.anchorCategory;
    }

    public void clear()
    {
        this.forms.clear();
        this.anchor = null;
        this.anchorCategory = null;
    }

    public void set(Form form, FormCategory category)
    {
        this.clear();
        this.add(form, category);
    }

    public void add(Form form, FormCategory category)
    {
        if (form != null && !this.contains(form))
        {
            this.forms.add(form);
        }

        this.anchor = form;
        this.anchorCategory = category;
    }

    public void toggle(Form form, FormCategory category)
    {
        int index = this.indexOf(form);

        if (index == -1)
        {
            this.add(form, category);
        }
        else
        {
            this.forms.remove(index);

            if (this.anchor == form)
            {
                this.anchor = this.forms.isEmpty() ? null : this.forms.get(this.forms.size() - 1);
                this.anchorCategory = this.forms.isEmpty() ? null : category;
            }
        }
    }

    /**
     * Pick every form between the anchor and {@code form} in {@code order} (inclusive), the
     * way Shift-click extends a selection. Without an anchor in that order it's a plain add.
     */
    public void range(Form form, FormCategory category, List<Form> order)
    {
        int from = this.anchorCategory == category ? identityIndex(order, this.anchor) : -1;
        int to = identityIndex(order, form);

        if (from == -1 || to == -1)
        {
            this.add(form, category);

            return;
        }

        for (int i = Math.min(from, to); i <= Math.max(from, to); i++)
        {
            Form f = order.get(i);

            if (!this.contains(f))
            {
                this.forms.add(f);
            }
        }
    }

    /** Forget forms that are no longer in any of the given categories. */
    public void retain(List<FormCategory> categories)
    {
        this.forms.removeIf((form) ->
        {
            for (FormCategory category : categories)
            {
                if (identityIndex(category.getForms(), form) != -1)
                {
                    return false;
                }
            }

            return true;
        });

        if (this.anchor != null && !this.contains(this.anchor))
        {
            this.anchor = null;
            this.anchorCategory = null;
        }
    }

    private int indexOf(Form form)
    {
        return identityIndex(this.forms, form);
    }

    public static int identityIndex(List<Form> list, Form form)
    {
        for (int i = 0; i < list.size(); i++)
        {
            if (list.get(i) == form)
            {
                return i;
            }
        }

        return -1;
    }
}
