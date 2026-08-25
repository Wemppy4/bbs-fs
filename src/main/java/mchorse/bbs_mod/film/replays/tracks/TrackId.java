package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.settings.values.core.StableIds;
import mchorse.bbs_mod.forms.FormUtils;

/**
 * The address of a track: which form it belongs to, what kind of thing it animates, and which one
 * of them.
 *
 * <p>This is the single place in the project that turns a track address into a string and back.
 * Before it, the same rules were written out by hand in {@code PerLimbService} and
 * {@code FormControlKeys} — one {@code is*} / {@code parse*} / {@code to*Key} trio per namespace,
 * nine namespaces, all matching with {@code contains()} — and the answers were re-derived from the
 * string at every use site.</p>
 *
 * <p>The string form is what films are saved with today, so {@link #parse(String)} and
 * {@link #toKey()} are exact inverses of the old helpers for every id that can occur in existing
 * data. Parsing walks the id from the front instead of searching the whole string, so a bone (or
 * material) literally named {@code ik_targets} no longer reads as an IK track.</p>
 *
 * @param kind     what the track animates
 * @param formPath path of the owning form inside the replay's form tree (body part indices joined
 *                 by {@code /}), empty for the root form
 * @param subject  which bone / material / controller / chain; empty for whole-form kinds
 * @param property for {@link TrackKind#MATERIAL_PROP}, which material property; empty otherwise
 */
public record TrackId(TrackKind kind, String formPath, String subject, String property)
{
    /* Segment prefixes of the string form. Kept exactly as they were written, since saved films
     * spell them this way. */
    public static final String BONE_PREFIX = "pose.bones.";
    public static final String CONSTRAINT_PREFIX = "constraints.bones.";
    public static final String MATERIAL_TEXTURE_PREFIX = "texture.materials.";
    public static final String MATERIAL_PROP_PREFIX = "materials.";
    public static final String IK_TARGETS = "ik_targets";
    public static final String POLE_TARGETS = "pole_targets";
    public static final String PHYSICS_TARGETS = "physics_targets";

    /**
     * Stands in for the whole-form material level, whose material name is empty in the data
     * ({@code ModelForm.materials} keys it by ""). An empty segment would make the string form
     * unparseable ({@code materials..color}), so it spells it {@code *} — the same sentinel
     * convention as {@code Form.DISABLED_ALL}.
     */
    public static final String MATERIAL_DEFAULT = "*";

    /* Animatable per-material properties. The material name itself may contain dots (OBJ's
     * "Material.001"), so parsing strips a KNOWN suffix instead of splitting on the last dot —
     * longest first, so stripping never bites a shorter one. */
    public static final String MATERIAL_PROP_COLOR = "color";
    public static final String MATERIAL_PROP_OVERLAY = "color_overlay";
    public static final String MATERIAL_PROP_LIGHTING = "lighting";
    public static final String MATERIAL_PROP_CULLING = "culling";
    public static final String MATERIAL_PROP_SMOOTHNESS = "smoothness";
    public static final String MATERIAL_PROP_METALLIC = "metallic";
    public static final String MATERIAL_PROP_SSS = "sss";
    public static final String MATERIAL_PROP_PIXEL_EMISSION = "pixel_emission";
    public static final String MATERIAL_PROP_RELIEF = "relief";

    public static final String[] MATERIAL_PROPS_ALL = {
        MATERIAL_PROP_PIXEL_EMISSION, MATERIAL_PROP_OVERLAY, MATERIAL_PROP_SMOOTHNESS,
        MATERIAL_PROP_LIGHTING, MATERIAL_PROP_METALLIC, MATERIAL_PROP_CULLING,
        MATERIAL_PROP_RELIEF, MATERIAL_PROP_COLOR, MATERIAL_PROP_SSS
    };

    public TrackId
    {
        formPath = formPath == null ? "" : formPath;
        subject = subject == null ? "" : subject;
        property = property == null ? "" : property;
    }

    /* Construction */

    public static TrackId property(String formPath, String name)
    {
        return new TrackId(TrackKind.PROPERTY, formPath, name, "");
    }

    public static TrackId bone(String formPath, String bone)
    {
        return new TrackId(TrackKind.BONE, formPath, bone, "");
    }

    public static TrackId boneConstraint(String formPath, String bone)
    {
        return new TrackId(TrackKind.BONE_CONSTRAINT, formPath, bone, "");
    }

    public static TrackId materialTexture(String formPath, String material)
    {
        return new TrackId(TrackKind.MATERIAL_TEXTURE, formPath, material, "");
    }

    public static TrackId materialProp(String formPath, String material, String property)
    {
        return new TrackId(TrackKind.MATERIAL_PROP, formPath, material, property);
    }

    public static TrackId ikTarget(String formPath, String controller)
    {
        return new TrackId(TrackKind.IK_TARGET, formPath, controller, "");
    }

    public static TrackId poleTarget(String formPath, String controller)
    {
        return new TrackId(TrackKind.POLE_TARGET, formPath, controller, "");
    }

    public static TrackId physicsTarget(String formPath, String rootBone)
    {
        return new TrackId(TrackKind.PHYSICS_TARGET, formPath, rootBone, "");
    }

    public static TrackId ikControls(String formPath)
    {
        return new TrackId(TrackKind.IK_CONTROLS, formPath, "", "");
    }

    public static TrackId physicsControls(String formPath)
    {
        return new TrackId(TrackKind.PHYSICS_CONTROLS, formPath, "", "");
    }

    public static TrackId windControls(String formPath)
    {
        return new TrackId(TrackKind.WIND_CONTROLS, formPath, "", "");
    }

    /* Queries */

    public boolean is(TrackKind kind)
    {
        return this.kind == kind;
    }

    /** The material this track animates, with the whole-form sentinel resolved back to the empty name the data uses. */
    public String material()
    {
        return MATERIAL_DEFAULT.equals(this.subject) ? "" : this.subject;
    }

    /**
     * The subject under its owning form, without the namespace segment that says what kind it is —
     * {@code 0/1/pose.bones.head} reads as {@code 0/1/head}. That is how the bone matrix cache keys
     * its entries, so it is the form a bone address takes once its kind is already known.
     */
    public String subjectPath()
    {
        return prefix(this.formPath, this.subject);
    }

    /* String form */

    /** The saved id of this track — the exact string the old per-namespace {@code to*Key} helpers produced. */
    public String toKey()
    {
        return prefix(this.formPath, switch (this.kind)
        {
            case PROPERTY -> this.subject;
            case BONE -> BONE_PREFIX + this.subject;
            case BONE_CONSTRAINT -> CONSTRAINT_PREFIX + this.subject;
            case MATERIAL_TEXTURE -> MATERIAL_TEXTURE_PREFIX + this.subject;
            case MATERIAL_PROP -> MATERIAL_PROP_PREFIX + (this.subject.isEmpty() ? MATERIAL_DEFAULT : this.subject) + "." + this.property;
            case IK_TARGET -> IK_TARGETS + FormUtils.PATH_SEPARATOR + this.subject;
            case POLE_TARGET -> POLE_TARGETS + FormUtils.PATH_SEPARATOR + this.subject;
            case PHYSICS_TARGET -> PHYSICS_TARGETS + FormUtils.PATH_SEPARATOR + this.subject;
            case IK_CONTROLS, PHYSICS_CONTROLS, WIND_CONTROLS -> this.kind.key;
        });
    }

    /**
     * What this track is called, with no address in it — the readable half of {@link #toKey()},
     * which leads with a chain of stable ids nobody can read.
     */
    public String label()
    {
        return switch (this.kind)
        {
            case PROPERTY -> this.subject;
            case BONE -> this.subject;
            case BONE_CONSTRAINT -> this.subject + FormUtils.PATH_SEPARATOR + "constraints";
            case MATERIAL_TEXTURE -> this.subject;
            case MATERIAL_PROP -> (this.subject.isEmpty() ? MATERIAL_DEFAULT : this.subject) + "." + this.property;
            case IK_TARGET -> "ik" + FormUtils.PATH_SEPARATOR + this.subject;
            case POLE_TARGET -> "pole" + FormUtils.PATH_SEPARATOR + this.subject;
            case PHYSICS_TARGET -> "physics" + FormUtils.PATH_SEPARATOR + this.subject;
            case IK_CONTROLS, PHYSICS_CONTROLS, WIND_CONTROLS -> this.kind.key;
        };
    }

    @Override
    public String toString()
    {
        return this.toKey();
    }

    private static String prefix(String formPath, String rest)
    {
        return formPath.isEmpty() ? rest : formPath + FormUtils.PATH_SEPARATOR + rest;
    }

    /**
     * Read a saved track id.
     *
     * <p>The leading run of numeric segments is the owning form's path (body part indices — see
     * {@link FormUtils#getPropertyPath}); what follows says which kind of track it is. Matching the
     * rest from its front, rather than searching the whole id, is what keeps a bone named after a
     * namespace from hijacking the id.</p>
     *
     * @return the address, or null if the id spells nothing recognisable (never for an id this
     *         class produced)
     */
    public static TrackId parse(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        int split = formPathLength(id);
        String formPath = split == 0 ? "" : id.substring(0, split - 1);
        String rest = id.substring(split);

        if (rest.isEmpty())
        {
            return null;
        }

        if (rest.startsWith(BONE_PREFIX))
        {
            return bone(formPath, rest.substring(BONE_PREFIX.length()));
        }

        if (rest.startsWith(CONSTRAINT_PREFIX))
        {
            return boneConstraint(formPath, rest.substring(CONSTRAINT_PREFIX.length()));
        }

        /* Before the plain material prefix, whose string it contains. */
        if (rest.startsWith(MATERIAL_TEXTURE_PREFIX))
        {
            return materialTexture(formPath, rest.substring(MATERIAL_TEXTURE_PREFIX.length()));
        }

        if (rest.startsWith(MATERIAL_PROP_PREFIX))
        {
            return parseMaterialProp(formPath, rest.substring(MATERIAL_PROP_PREFIX.length()));
        }

        TrackId target = parseTarget(formPath, rest, IK_TARGETS, TrackKind.IK_TARGET);

        if (target == null)
        {
            target = parseTarget(formPath, rest, POLE_TARGETS, TrackKind.POLE_TARGET);
        }

        if (target == null)
        {
            target = parseTarget(formPath, rest, PHYSICS_TARGETS, TrackKind.PHYSICS_TARGET);
        }

        if (target != null)
        {
            return target;
        }

        for (TrackKind kind : TrackKind.values())
        {
            if (kind.isWholeForm() && rest.equals(kind.key))
            {
                return new TrackId(kind, formPath, "", "");
            }
        }

        return property(formPath, rest);
    }

    /** What kind of track a saved id spells, or null if it spells nothing recognisable. */
    public static TrackKind kindOf(String id)
    {
        TrackId track = parse(id);

        return track == null ? null : track.kind();
    }

    /** The address a saved id spells, but only if it is of this kind — null otherwise. */
    public static TrackId parse(String id, TrackKind kind)
    {
        TrackId track = parse(id);

        return track != null && track.kind() == kind ? track : null;
    }

    /** Length of the leading form-path run, including its trailing separator; 0 when the id starts with the track itself. */
    private static int formPathLength(String id)
    {
        int length = 0;

        while (true)
        {
            int separator = id.indexOf(FormUtils.PATH_SEPARATOR, length);

            if (separator < 0 || separator == length || !isAddress(id, length, separator))
            {
                return length;
            }

            length = separator + 1;
        }
    }

    /**
     * Whether a segment addresses a body part: a stable id, or the list position such an address
     * used to be. Both are accepted because a track key is not versioned — a film converted on load
     * carries ids, but a key built by hand or read from an older clipboard can still be an index.
     */
    private static boolean isAddress(String id, int from, int to)
    {
        String segment = id.substring(from, to);

        if (StableIds.isStableId(segment))
        {
            return true;
        }

        for (int i = 0; i < segment.length(); i++)
        {
            if (!Character.isDigit(segment.charAt(i)))
            {
                return false;
            }
        }

        return true;
    }

    private static TrackId parseTarget(String formPath, String rest, String namespace, TrackKind kind)
    {
        if (!rest.startsWith(namespace))
        {
            return null;
        }

        String subject = rest.substring(namespace.length());

        if (subject.startsWith(FormUtils.PATH_SEPARATOR))
        {
            subject = subject.substring(FormUtils.PATH_SEPARATOR.length());
        }

        return new TrackId(kind, formPath, subject, "");
    }

    /**
     * Split {@code <material>.<property>} where the material name may itself contain dots, by
     * stripping a known property suffix instead of splitting on the last one.
     */
    private static TrackId parseMaterialProp(String formPath, String rest)
    {
        for (String property : MATERIAL_PROPS_ALL)
        {
            if (rest.endsWith("." + property))
            {
                String material = rest.substring(0, rest.length() - property.length() - 1);

                if (material.isEmpty())
                {
                    return null;
                }

                return materialProp(formPath, MATERIAL_DEFAULT.equals(material) ? "" : material, property);
            }
        }

        return null;
    }
}
