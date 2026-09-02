package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.RecentFormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserFormSection extends FormSection
{
    public List<UserFormCategory> categories = new ArrayList<>();

    /**
     * Categories changed since the last {@link #flush()}. Every edit used to serialise and
     * write its whole category on the spot, so dropping a handful of forms froze the game
     * for as many full writes; now edits only mark, and the tick writes each dirty category
     * once, off the render thread.
     */
    private final Set<UserFormCategory> dirty = new LinkedHashSet<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor((runnable) ->
    {
        Thread thread = new Thread(runnable, "BBS user form categories writer");

        thread.setDaemon(true);

        return thread;
    });

    public static File getUserCategoriesFile(int index)
    {
        return BBSMod.getSettingsPath("forms/" + index + ".json");
    }

    public UserFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        this.categories.clear();

        File folder = getUserCategoriesFile(0).getParentFile();

        if (folder.isDirectory())
        {
            this.loadNewCategories();
        }
    }

    private void loadNewCategories()
    {
        /* Just in case 420 categories, because it's blazing */
        for (int i = 0; i < 420; i++)
        {
            File file = getUserCategoriesFile(i);

            if (!file.isFile())
            {
                break;
            }

            UserFormCategory category = new UserFormCategory(IKey.EMPTY, this.parent.preferences.visible(UUID.randomUUID().toString()), this);

            try
            {
                MapType data = (MapType) DataToString.read(file);

                category.fromData(data);
                this.categories.add(category);
            }
            catch (Exception e)
            {
                System.err.println("Failed to load user form category: " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }
    }

    @Override
    public List<FormCategory> getCategories()
    {
        List<FormCategory> categoryList = new ArrayList<>();

        for (UserFormCategory category : this.categories)
        {
            categoryList.add(category);
        }

        return categoryList;
    }

    /** Mark every category for saving. Written on the next {@link #flush()}. */
    public void writeUserCategories()
    {
        this.dirty.addAll(this.categories);
    }

    /** Mark a category for saving. Written on the next {@link #flush()}. */
    public void writeUserCategories(UserFormCategory formCategory)
    {
        if (formCategory != null)
        {
            this.dirty.add(formCategory);
        }
    }

    /**
     * Save what changed. Serialising happens here, on the game thread, where the forms are
     * safe to read; only the disk write goes to the writer thread.
     */
    public void flush()
    {
        if (this.dirty.isEmpty())
        {
            return;
        }

        for (UserFormCategory category : this.dirty)
        {
            int index = this.categories.indexOf(category);

            if (index == -1)
            {
                continue;
            }

            File file = getUserCategoriesFile(index);
            String text = DataToString.toString(category.toData(), true);

            this.writer.submit(() ->
            {
                file.getParentFile().mkdirs();

                try
                {
                    IOUtils.writeText(file, text);
                }
                catch (IOException e)
                {
                    System.err.println("Failed to save user category: " + file.getAbsolutePath());
                    e.printStackTrace();
                }
            });
        }

        this.dirty.clear();
    }

    public void addUserCategory(UserFormCategory category)
    {
        int index = 0;

        for (FormCategory formCat : this.categories)
        {
            if (formCat instanceof RecentFormCategory || formCat instanceof UserFormCategory)
            {
                index += 1;
            }
            else
            {
                break;
            }
        }

        this.categories.add(index, category);
        this.parent.markDirty();
        this.writeUserCategories();
    }

    /**
     * Put a category at a new index, {@code to} being a position in the list as it is now.
     * Files are named by index, so the whole set is rewritten.
     */
    public void moveUserCategory(UserFormCategory category, int to)
    {
        int from = this.categories.indexOf(category);

        if (from == -1)
        {
            return;
        }

        this.categories.remove(from);

        if (to > from)
        {
            to -= 1;
        }

        this.categories.add(Math.max(0, Math.min(to, this.categories.size())), category);
        this.parent.markDirty();
        this.writeUserCategories();
    }

    public void removeUserCategory(UserFormCategory category)
    {
        File lastFile = getUserCategoriesFile(this.categories.size() - 1);

        if (lastFile.isFile())
        {
            lastFile.delete();
        }

        this.categories.remove(category);
        this.dirty.remove(category);
        this.parent.markDirty();
        this.parent.preferences.remove(category.visible.getId());

        this.writeUserCategories();
    }
}
