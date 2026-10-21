package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.cubic.weld.ModelWeld;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntBiFunction;

/**
 * A single weld entry in a model's config, mirroring {@link ModelWeld} as an editable value tree:
 * glue the {@code source} bone's face onto the {@code target} bone's so a bending joint stays sealed.
 */
public class WeldValue extends ValueGroup
{
    public final ValueString sourceBone = new ValueString("source_bone", "");
    public final ValueString sourceFace = new ValueString("source_face", "");
    public final ValueString targetBone = new ValueString("target_bone", "");
    public final ValueString targetFace = new ValueString("target_face", "");
    public final ValueFloat maxAngle = new ValueFloat("max_angle", 120F);
    public final ValueFloat seamFalloff = new ValueFloat("seam_falloff", 0.35F, 0F, 1F);

    /** The PARENT bone's share of the joint's deformation; 0.5 (the default) is the old bisector split. */
    public final ValueFloat parentShare = new ValueFloat("parent_share", 0.5F, 0F, 1F);

    /** Off by default so scenes tuned before twist distribution existed keep rendering identically. */
    public final ValueBoolean twist = new ValueBoolean("twist", false);

    /** Off by default: older configs keep their hard crease, and a rounded look is a choice per joint. */
    public final ValueBoolean smooth = new ValueBoolean("smooth", false);

    /** The cubes welded by hand, see {@link ModelWeld#pairs}; none, and the weld takes every cube as it always did. */
    public final Pairs pairs = new Pairs("pairs");

    public WeldValue(String id)
    {
        super(id);

        this.add(this.sourceBone);
        this.add(this.sourceFace);
        this.add(this.targetBone);
        this.add(this.targetFace);
        this.add(this.maxAngle);
        this.add(this.seamFalloff);
        this.add(this.parentShare);
        this.add(this.twist);
        this.add(this.smooth);
        this.add(this.pairs);
    }

    /** A weld without pairs is written the way it was before there were any. */
    @Override
    protected boolean canPersist(BaseValue value)
    {
        if (value == this.pairs) return !this.pairs.get().isEmpty();

        return super.canPersist(value);
    }

    /** ...so data without them means none, not "keep these": a paste of another weld, an undo to before the first pair. */
    @Override
    public void fromData(BaseType data)
    {
        if (data.isMap() && !data.asMap().has(this.pairs.getId()))
        {
            this.pairs.replace(List.of());
        }

        super.fromData(data);
    }

    public ModelWeld toWeld()
    {
        return new ModelWeld(
            this.sourceBone.get(),
            this.sourceFace.get(),
            this.targetBone.get(),
            this.targetFace.get(),
            this.maxAngle.get(),
            this.seamFalloff.get(),
            this.parentShare.get(),
            this.twist.get(),
            this.smooth.get(),
            this.pairs.get()
        );
    }

    /**
     * Point the pairs at their cubes' new numbers after an edit of the model: {@code renumber} gives a
     * bone's cube its number now from the one it had, -1 for a cube that is no longer in that bone,
     * whose pair goes with it. Whether a pair changed.
     */
    public boolean renumberCubes(ToIntBiFunction<String, Integer> renumber)
    {
        List<ModelWeld.Pair> pairs = new ArrayList<>();

        for (ModelWeld.Pair pair : this.pairs.get())
        {
            int source = pair.source() < 0 ? pair.source() : renumber.applyAsInt(this.sourceBone.get(), pair.source());
            int target = pair.target() < 0 ? pair.target() : renumber.applyAsInt(this.targetBone.get(), pair.target());

            if ((pair.source() >= 0 && source < 0) || (pair.target() >= 0 && target < 0))
            {
                continue;
            }

            pairs.add(new ModelWeld.Pair(source, target));
        }

        if (pairs.equals(this.pairs.get()))
        {
            return false;
        }

        this.pairs.replace(pairs);

        return true;
    }

    /** The pairs, written as {@code [[source, target], ...]}. */
    public static class Pairs extends BaseValueBasic<List<ModelWeld.Pair>>
    {
        public Pairs(String id)
        {
            super(id, new ArrayList<>());
        }

        /**
         * Take these pairs without telling anyone, the way {@link ModelConfig#renameBone} edits the data:
         * the edit of the model that moved the cubes carries the config in its own undo step.
         */
        void replace(List<ModelWeld.Pair> pairs)
        {
            this.setOriginalValue(new ArrayList<>(pairs));
        }

        @Override
        public BaseType toData()
        {
            ListType list = new ListType();

            for (ModelWeld.Pair pair : this.value)
            {
                ListType entry = new ListType();

                entry.addInt(pair.source());
                entry.addInt(pair.target());
                list.add(entry);
            }

            return list;
        }

        @Override
        public void fromData(BaseType data)
        {
            this.value.clear();

            if (!data.isList())
            {
                return;
            }

            for (BaseType element : data.asList())
            {
                if (element.isList() && element.asList().size() == 2)
                {
                    ListType entry = element.asList();

                    this.value.add(new ModelWeld.Pair(entry.getInt(0, ModelWeld.Pair.NONE), entry.getInt(1, ModelWeld.Pair.NONE)));
                }
            }
        }

        @Override
        protected List<ModelWeld.Pair> copyValue(List<ModelWeld.Pair> value)
        {
            return value == null ? null : new ArrayList<>(value);
        }
    }
}
