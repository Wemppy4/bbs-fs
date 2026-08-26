package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pose implements IMapSerializable
{
    private static Set<String> keys = new HashSet<>();
    private static List<Pair<Pattern, String>> patterns = new ArrayList<>();

    public final Map<String, PoseTransform> transforms = new HashMap<>();

    static
    {
        patterns.add(new Pair<>(Pattern.compile("^right([_.].+)$"), "left$1"));
        patterns.add(new Pair<>(Pattern.compile("^(.+[_.])right$"), "$1left"));
        patterns.add(new Pair<>(Pattern.compile("^(.+[_.])right([_.].+)$"), "$1left$2"));
        patterns.add(new Pair<>(Pattern.compile("^r([_.].+)$"), "l$1"));
        patterns.add(new Pair<>(Pattern.compile("^(.+[_.])r$"), "$1l"));
        patterns.add(new Pair<>(Pattern.compile("^(.+[_.])r([_.].+)$"), "$1l$2"));

        patterns.add(new Pair<>(Pattern.compile("^left([_.].+)$"), "right$1"));
        patterns.add(new Pair<>(Pattern.compile("^(.+[_.])left$"), "$1right"));
        patterns.add(new Pair<>(Pattern.compile("^(.+[_.])left([_.].+)$"), "$1right$2"));
        patterns.add(new Pair<>(Pattern.compile("^l([_.].+)$"), "r$1"));
        patterns.add(new Pair<>(Pattern.compile("^(.+[_.])l$"), "$1r"));
        patterns.add(new Pair<>(Pattern.compile("^(.+[_.])l([_.].+)$"), "$1r$2"));
    }

    public void flip(Map<String, String> flippedParts)
    {
        List<Pair<String, String>> list = new ArrayList<>();

        if (flippedParts == null || flippedParts.isEmpty())
        {
            for (String key : this.transforms.keySet())
            {
                if (key == null)
                {
                    continue;
                }

                for (Pair<Pattern, String> pair : patterns)
                {
                    Matcher matcher = pair.a.matcher(key);

                    if (matcher.matches())
                    {
                        Pair<String, String> e = new Pair<>(matcher.replaceAll(pair.b), key);

                        if (!list.contains(new Pair<>(e.b, e.a)))
                        {
                            list.add(e);
                        }
                    }
                }
            }
        }
        else
        {
            for (Map.Entry<String, String> entry : flippedParts.entrySet())
            {
                if (this.transforms.containsKey(entry.getKey()))
                {
                    list.add(new Pair<>(entry.getValue(), entry.getKey()));
                }
                else if (this.transforms.containsKey(entry.getValue()))
                {
                    list.add(new Pair<>(entry.getKey(), entry.getValue()));
                }
            }
        }

        Set<String> bones = new HashSet<>(this.transforms.keySet());

        for (Pair<String, String> pair : list)
        {
            PoseTransform l = this.transforms.get(pair.a);
            PoseTransform r = this.transforms.get(pair.b);

            if (r == null)
            {
                continue;
            }

            if (l == null)
            {
                l = new PoseTransform();

                this.transforms.put(pair.a, l);
            }

            this.transforms.remove(pair.a);
            this.transforms.remove(pair.b);
            this.transforms.put(pair.a, r);
            this.transforms.put(pair.b, l);

            r.mirrorX();
            l.mirrorX();

            bones.remove(pair.a);
            bones.remove(pair.b);
        }

        for (String bone : bones)
        {
            this.transforms.get(bone).mirrorX();
        }
    }

    /**
     * The mirror (opposite-side) counterpart of a bone name via the same
     * left/right patterns used for flipping, or the same name when it has no
     * side (a centre/unpaired bone).
     */
    public static String getMirrorName(String name)
    {
        if (name == null)
        {
            return null;
        }

        for (Pair<Pattern, String> pair : patterns)
        {
            Matcher matcher = pair.a.matcher(name);

            if (matcher.matches())
            {
                return matcher.replaceAll(pair.b);
            }
        }

        return name;
    }

    /**
     * The bone's transform, or {@code null} when this pose says nothing about it.
     *
     * <p>Reading does NOT insert: a pose holds exactly the bones somebody posed, and
     * "this pose is silent about that bone" is a real answer that callers act on —
     * interpolation substitutes the rest transform, a bone track adds onto it. Reading
     * used to create, so every reader quietly grew the pose it was only looking at.
     */
    public PoseTransform get(String name)
    {
        return this.transforms.get(name);
    }

    /** The bone's transform, inserting a rest one when the pose has none — for writers. */
    public PoseTransform getOrCreate(String name)
    {
        PoseTransform transform = this.transforms.get(name);

        if (transform == null)
        {
            transform = new PoseTransform();

            this.transforms.put(name, transform);
        }

        return transform;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (super.equals(obj))
        {
            return true;
        }

        if (obj instanceof Pose)
        {
            Pose pose = (Pose) obj;

            keys.clear();
            keys.addAll(this.transforms.keySet());
            keys.addAll(pose.transforms.keySet());

            for (String key : keys)
            {
                Transform a = this.transforms.get(key);
                Transform b = pose.transforms.get(key);

                if (a != null && b != null && !a.equals(b)) return false;
                if (a == null && !b.isDefault()) return false;
                if (b == null && !a.isDefault()) return false;
            }

            return true;
        }

        return false;
    }

    public Pose copy()
    {
        Pose pose = new Pose();

        pose.copy(this);

        return pose;
    }

    public void copy(Pose pose)
    {
        this.transforms.clear();

        if (pose.transforms.isEmpty())
        {
            return;
        }

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            if (!entry.getValue().isDefault())
            {
                this.transforms.put(entry.getKey(), (PoseTransform) entry.getValue().copy());
            }
        }
    }

    @Override
    public void toData(MapType data)
    {
        if (this.transforms.isEmpty())
        {
            return;
        }

        MapType pose = new MapType();

        for (Map.Entry<String, PoseTransform> entry : this.transforms.entrySet())
        {
            if (!entry.getValue().isDefault())
            {
                pose.put(entry.getKey(), entry.getValue().toData());
            }
        }

        data.put("pose", pose);
    }

    @Override
    public void fromData(MapType data)
    {
        this.transforms.clear();

        MapType pose = data.getMap("pose");

        for (String key : pose.keys())
        {
            PoseTransform transform = new PoseTransform();

            transform.fromData(pose.getMap(key));

            if (!transform.isDefault())
            {
                this.transforms.put(key, transform);
            }
        }
    }

    public boolean isEmpty()
    {
        return this.transforms.isEmpty();
    }
}