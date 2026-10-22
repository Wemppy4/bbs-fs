package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.forms.entities.IEntity;

public interface ITickable
{
    public void tick(IEntity entity);

    /**
     * The time the form lives in jumped instead of ticking (the film editor's
     * cursor moved), so whatever was simulated tick by tick no longer belongs
     * to the moment on screen. Start it over from here.
     */
    public default void restart()
    {}
}
