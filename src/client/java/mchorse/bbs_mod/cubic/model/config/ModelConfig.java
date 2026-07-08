package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.cubic.weld.ModelWeld;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.settings.values.ui.ValueStringMap;
import mchorse.bbs_mod.utils.pose.Pose;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A model's {@code config.json} as an editable value tree. Loading and saving the file is the
 * {@link ValueGroup} machinery's recursive {@link #fromData}/{@link #toData} — no manual parsing.
 *
 * <p>Every block is a first-class value now (slots, armor, item hands, bone maps, the sneaking pose),
 * so the editor binds straight to them. The runtime ({@code ModelInstance}) reads everything through
 * this class — it keeps no config fields of its own. The few runtime forms that get walked every frame
 * (the armor/item {@link ArmorSlot}s) are cached and rebuilt via {@link #rebuild()} on load or edit.</p>
 */
public class ModelConfig extends ValueGroup
{
    public final ValueBoolean procedural = new ValueBoolean("procedural", false);
    public final ValueBoolean culling = new ValueBoolean("culling", true);
    public final ValueBoolean onCpu = new ValueBoolean("on_cpu", false);
    public final ValueString poseGroup = new ValueString("pose_group", "");
    public final ValueString anchor = new ValueString("anchor", "");
    public final ValueLink texture = new ValueLink("texture", null);
    public final ValueFloat uiScale = new ValueFloat("ui_scale", 1F);
    public final ValueVector3f scale = new ValueVector3f("scale", new Vector3f(1F));
    public final ValueFloat bevel = new ValueFloat("bevel", 0F, 0F, 16F);
    public final ValueInt bevelSegments = new ValueInt("bevel_segments", 2, 1, 8);
    public final WeldList welds = new WeldList("welds");
    public final ValueStringKeys disabledBones = new ValueStringKeys("disabledBones");

    public final LookAtValue lookAt = new LookAtValue("look_at");
    public final ValuePose sneakingPose = new ValuePose("sneaking_pose", new Pose());
    public final ItemSlotList itemsMain = new ItemSlotList("items_main");
    public final ItemSlotList itemsOff = new ItemSlotList("items_off");
    public final ArmorSlotsValue armorSlots = new ArmorSlotsValue("armor_slots");
    public final ValueStringMap flippedParts = new ValueStringMap("flipped_parts");
    public final ValueStringMap pickingOverrides = new ValueStringMap("picking_overrides");
    public final ArmorSlotValue fpMain = new ArmorSlotValue("fp_main");
    public final ArmorSlotValue fpOffhand = new ArmorSlotValue("fp_offhand");

    private final List<ModelWeld> weldsCache = new ArrayList<>();
    private final List<ArmorSlot> itemsMainCache = new ArrayList<>();
    private final List<ArmorSlot> itemsOffCache = new ArrayList<>();
    private final Map<ArmorType, ArmorSlot> armorSlotsCache = new HashMap<>();
    private View viewCache;
    private ArmorSlot fpMainCache;
    private ArmorSlot fpOffhandCache;

    public ModelConfig(String id)
    {
        super(id);

        this.add(this.procedural);
        this.add(this.culling);
        this.add(this.onCpu);
        this.add(this.poseGroup);
        this.add(this.anchor);
        this.add(this.texture);
        this.add(this.uiScale);
        this.add(this.scale);
        this.add(this.bevel);
        this.add(this.bevelSegments);
        this.add(this.welds);
        this.add(this.disabledBones);
        this.add(this.lookAt);
        this.add(this.sneakingPose);
        this.add(this.itemsMain);
        this.add(this.itemsOff);
        this.add(this.armorSlots);
        this.add(this.flippedParts);
        this.add(this.pickingOverrides);
        this.add(this.fpMain);
        this.add(this.fpOffhand);

        this.rebuild();
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        this.rebuild();
    }

    @Override
    protected boolean canPersist(BaseValue value)
    {
        /* Optional blocks stay absent from the file when empty, matching how they were authored. */
        if (value == this.lookAt) return this.lookAt.isActive();
        if (value == this.fpMain) return this.fpMain.isActive();
        if (value == this.fpOffhand) return this.fpOffhand.isActive();
        if (value == this.sneakingPose) return !this.sneakingPose.get().isEmpty();
        if (value == this.itemsMain) return this.itemsMain.hasActive();
        if (value == this.itemsOff) return this.itemsOff.hasActive();
        if (value == this.flippedParts) return !this.flippedParts.get().isEmpty();
        if (value == this.pickingOverrides) return !this.pickingOverrides.get().isEmpty();
        if (value == this.armorSlots) return this.armorSlots.hasActive();

        return super.canPersist(value);
    }

    /**
     * Re-derive the per-frame runtime forms (welds, view, armor/item slots) from the value tree. Call
     * after editing a block so the runtime sees the change. The plain maps (flip/picking) and the pose
     * are read straight off their values, so they don't need rebuilding.
     */
    public void rebuild()
    {
        this.weldsCache.clear();

        for (WeldValue weld : this.welds.getAllTyped())
        {
            this.weldsCache.add(weld.toWeld());
        }

        if (this.lookAt.isActive())
        {
            this.viewCache = new View();
            this.viewCache.headBone = this.lookAt.head.get();
            this.viewCache.pitch = this.lookAt.pitch.get();
            this.viewCache.constraint = this.lookAt.headLimit.get();
        }
        else
        {
            this.viewCache = null;
        }

        this.fpMainCache = this.fpMain.isActive() ? this.fpMain.toSlot() : null;
        this.fpOffhandCache = this.fpOffhand.isActive() ? this.fpOffhand.toSlot() : null;

        this.fillSlots(this.itemsMain, this.itemsMainCache);
        this.fillSlots(this.itemsOff, this.itemsOffCache);

        this.armorSlotsCache.clear();
        this.armorSlotsCache.putAll(this.armorSlots.toMap());
    }

    private void fillSlots(ItemSlotList list, List<ArmorSlot> out)
    {
        out.clear();

        for (ArmorSlotValue slot : list.getAllTyped())
        {
            if (slot.isActive())
            {
                out.add(slot.toSlot());
            }
        }
    }

    public List<ModelWeld> getWelds()
    {
        return this.weldsCache;
    }

    public Pose getSneakingPose()
    {
        return this.sneakingPose.get();
    }

    public View getView()
    {
        return this.viewCache;
    }

    public ArmorSlot getFpMain()
    {
        return this.fpMainCache;
    }

    public ArmorSlot getFpOffhand()
    {
        return this.fpOffhandCache;
    }

    public List<ArmorSlot> getItemsMain()
    {
        return this.itemsMainCache;
    }

    public List<ArmorSlot> getItemsOff()
    {
        return this.itemsOffCache;
    }

    public Map<ArmorType, ArmorSlot> getArmorSlots()
    {
        return this.armorSlotsCache;
    }

    public Map<String, String> getFlippedParts()
    {
        return this.flippedParts.get();
    }

    public Map<String, String> getPickingOverrides()
    {
        return this.pickingOverrides.get();
    }

    public Link getTexture()
    {
        return this.texture.get();
    }

    public static class WeldList extends ValueList<WeldValue>
    {
        public WeldList(String id)
        {
            super(id);
        }

        @Override
        protected WeldValue create(String id)
        {
            return new WeldValue(id);
        }
    }

    public static class ItemSlotList extends ValueList<ArmorSlotValue>
    {
        public ItemSlotList(String id)
        {
            super(id);
        }

        @Override
        protected ArmorSlotValue create(String id)
        {
            return new ArmorSlotValue(id);
        }

        public boolean hasActive()
        {
            for (ArmorSlotValue slot : this.getAllTyped())
            {
                if (slot.isActive())
                {
                    return true;
                }
            }

            return false;
        }

        @Override
        public BaseType toData()
        {
            ListType list = new ListType();

            for (ArmorSlotValue slot : this.getAllTyped())
            {
                if (slot.isActive())
                {
                    list.add(slot.toData());
                }
            }

            return list;
        }
    }
}
