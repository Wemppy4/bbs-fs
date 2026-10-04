package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.clips.Clips;
import java.util.*;
import java.util.function.Supplier;

/** Keeps network delivery independent of the dashboard's construction. The original
 * film panel binds its data supplier and receiveActions method while it owns editing. */
public final class FilmClientBridge
{
    public interface Actions { void receive(String filmId,int replay,int tick,BaseType clips); }
    private static Supplier<Film> editing;
    private static Actions receiver;
    private static final Map<String,Film> recording=new HashMap<>();
    private static final List<Delivery> pending=new ArrayList<>();
    public static void bind(Supplier<Film> film,Actions actions)
    {
        editing=film;receiver=actions;
        if(receiver!=null){for(Delivery data:new ArrayList<>(pending))receiver.receive(data.id,data.replay,data.tick,data.clips);pending.clear();}
    }
    public static Film editedFilm(){return editing==null?null:editing.get();}
    public static void trackRecording(Film film){recording.put(film.getId(),film);}
    public static void receiveActions(String id,int index,int tick,BaseType data)
    {
        if(receiver!=null){receiver.receive(id,index,tick,data);recording.remove(id);return;}
        Film film=recording.remove(id);
        if(film!=null&&index>=0&&index<film.replays.getList().size())
        {
            Replay replay=film.replays.getList().get(index);
            BaseValue.edit(film,f->{Clips clips=new Clips("",BBSMod.getFactoryActionClips());clips.fromData(data);replay.actions.copyOver(clips,tick);});
        }
        else pending.add(new Delivery(id,index,tick,data));
    }
    public static void reset(){editing=null;receiver=null;recording.clear();pending.clear();}
    private static final class Delivery
    {
        final String id;final int replay,tick;final BaseType clips;
        Delivery(String id,int replay,int tick,BaseType clips){this.id=id;this.replay=replay;this.tick=tick;this.clips=clips;}
    }
    private FilmClientBridge(){}
}
