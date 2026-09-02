package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.settings.values.core.ValueStableList;
import mchorse.bbs_mod.utils.CollectionUtils;

public class Replays extends ValueStableList<Replay>
{
    public Replays(String id)
    {
        super(id);
    }

    public Replay addReplay()
    {
        Replay replay = new Replay("");

        this.preNotify();
        this.add(replay);
        this.postNotify();

        return replay;
    }

    public Replay getById(String id)
    {
        return (Replay) this.get(id);
    }

    public void remove(Replay replay)
    {
        int index = CollectionUtils.getIndex(this.list, replay);

        if (CollectionUtils.inRange(this.list, index))
        {
            this.preNotify();
            this.list.remove(index);
            this.postNotify();
        }
    }

    @Override
    protected Replay create(String id)
    {
        return new Replay(id);
    }
}
