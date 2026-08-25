package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One drag-and-drop in progress inside a form list: what's being carried and where it would
 * land. Either a handful of forms or a whole user category — never both.
 *
 * <p>A press only <em>arms</em> the drag; it goes {@link #isActive() active} once the cursor
 * has travelled a few pixels, so an ordinary click never turns into an accidental move. The
 * categories report the drop target while they paint (they're the ones that know their local
 * geometry), and the list resolves the drop on release.</p>
 */
public class FormDrag
{
    public static final int THRESHOLD = 4;

    public enum Kind
    {
        NONE, FORMS, CATEGORY
    }

    private Kind kind = Kind.NONE;
    private boolean active;
    private int startX;
    private int startY;

    private final List<Form> forms = new ArrayList<>();
    private FormCategory source;
    private UserFormCategory category;

    private UIFormCategory target;
    private int insertion;

    public void pressForms(List<Form> forms, FormCategory source, int x, int y)
    {
        this.reset();

        this.kind = Kind.FORMS;
        this.forms.addAll(forms);
        this.source = source;
        this.startX = x;
        this.startY = y;
    }

    public void pressCategory(UserFormCategory category, int x, int y)
    {
        this.reset();

        this.kind = Kind.CATEGORY;
        this.category = category;
        this.startX = x;
        this.startY = y;
    }

    public void reset()
    {
        this.kind = Kind.NONE;
        this.active = false;
        this.forms.clear();
        this.source = null;
        this.category = null;
        this.clearTarget();
    }

    /** Whether a button is held on something draggable, active or not yet. */
    public boolean isPressed()
    {
        return this.kind != Kind.NONE;
    }

    public boolean isActive()
    {
        return this.active;
    }

    public Kind getKind()
    {
        return this.kind;
    }

    public boolean isDragging(Form form)
    {
        return this.active && this.kind == Kind.FORMS && FormSelection.identityIndex(this.forms, form) != -1;
    }

    public boolean isDragging(FormCategory category)
    {
        return this.active && this.kind == Kind.CATEGORY && this.category == category;
    }

    public List<Form> getForms()
    {
        return Collections.unmodifiableList(this.forms);
    }

    /** The category the pressed form was in — the one a same-category drop rearranges. */
    public FormCategory getSource()
    {
        return this.source;
    }

    public UserFormCategory getCategory()
    {
        return this.category;
    }

    /** Feed the cursor; returns whether the drag is active after this move. */
    public boolean update(int x, int y)
    {
        if (this.kind != Kind.NONE && !this.active)
        {
            this.active = Math.abs(x - this.startX) >= THRESHOLD || Math.abs(y - this.startY) >= THRESHOLD;
        }

        return this.active;
    }

    /* Drop target, reported by categories while painting */

    public void setTarget(UIFormCategory target, int insertion)
    {
        this.target = target;
        this.insertion = insertion;
    }

    public void clearTarget()
    {
        this.target = null;
        this.insertion = -1;
    }

    public UIFormCategory getTarget()
    {
        return this.target;
    }

    public boolean isTarget(UIFormCategory category)
    {
        return this.active && this.target == category;
    }

    /**
     * For forms: the slot in the target's displayed order. For a category: 0 to land above
     * the target category, 1 to land below it.
     */
    public int getInsertion()
    {
        return this.insertion;
    }
}
