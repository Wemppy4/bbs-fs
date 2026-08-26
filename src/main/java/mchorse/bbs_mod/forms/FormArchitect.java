package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.data.migration.FormStableIds;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.factory.MapFactory;

public class FormArchitect extends MapFactory<Form, Void>
{
    @Override
    public String getTypeKey()
    {
        return "id";
    }

    /**
     * Every form read anywhere passes through here, so this is where form fragments living outside
     * the versioned documents — model blocks, morphs, items, old clipboards — get their body part
     * ids on first contact. Film documents arrive already converted (see {@code FilmStableIds});
     * for them this is a no-op walk.
     */
    @Override
    public Form fromData(MapType data)
    {
        if (data != null)
        {
            FormStableIds.ensure(data);
        }

        return super.fromData(data);
    }

    public boolean has(MapType data)
    {
        if (data.has(this.getTypeKey()))
        {
            Link id = Link.create(data.getString(this.getTypeKey()));

            return this.factory.containsKey(id);
        }

        return false;
    }
}