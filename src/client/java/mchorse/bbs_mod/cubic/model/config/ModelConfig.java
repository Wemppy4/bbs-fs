package mchorse.bbs_mod.cubic.model.config;

import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.cubic.weld.ModelWeld;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueData;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
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
 * <p>The flat settings (procedural, scale, texture...) are first-class values. The richer blocks
 * that aren't surfaced in an editor yet (poses, armor, item slots, flip/picking maps) ride along
 * as raw {@link ValueData} so they round-trip losslessly; their typed runtime forms are derived
 * lazily into the caches below and rebuilt whenever the tree is reloaded or edited via {@link
 * #rebuild()}. The runtime ({@code ModelInstance}) reads everything through this class — it keeps
 * no config fields of its own.</p>
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
    public final WeldList welds = new WeldList("welds");
    public final ValueStringKeys disabledBones = new ValueStringKeys("disabledBones");

    /* Richer blocks kept raw until an editor promotes them; rebuilt into the caches below. */
    public final ValueData lookAt = new ValueData("look_at");
    public final ValueData sneakingPose = new ValueData("sneaking_pose");
    public final ValueData itemsMain = new ValueData("items_main");
    public final ValueData itemsOff = new ValueData("items_off");
    public final ValueData armorSlots = new ValueData("armor_slots");
    public final ValueData flippedParts = new ValueData("flipped_parts");
    public final ValueData pickingOverrides = new ValueData("picking_overrides");
    public final ValueData fpMain = new ValueData("fp_main");
    public final ValueData fpOffhand = new ValueData("fp_offhand");

    private final List<ModelWeld> weldsCache = new ArrayList<>();
    private final List<ArmorSlot> itemsMainCache = new ArrayList<>();
    private final List<ArmorSlot> itemsOffCache = new ArrayList<>();
    private final Map<ArmorType, ArmorSlot> armorSlotsCache = new HashMap<>();
    private final Map<String, String> flippedPartsCache = new HashMap<>();
    private final Map<String, String> pickingOverridesCache = new HashMap<>();
    private Pose sneakingPoseCache = new Pose();
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

    /**
     * Re-derive the typed runtime forms (welds, poses, armor...) from the raw values. Call after
     * editing any of the raw {@link ValueData} blocks so the runtime sees the change.
     */
    public void rebuild()
    {
        this.weldsCache.clear();

        for (WeldValue weld : this.welds.getAllTyped())
        {
            this.weldsCache.add(weld.toWeld());
        }

        this.sneakingPoseCache = new Pose();

        BaseType pose = this.sneakingPose.get();

        if (pose != null && pose.isMap())
        {
            this.sneakingPoseCache.fromData(pose.asMap());
        }

        BaseType look = this.lookAt.get();

        if (look != null && look.isMap())
        {
            this.viewCache = new View();
            this.viewCache.fromData(look.asMap());
        }
        else
        {
            this.viewCache = null;
        }

        this.fpMainCache = this.toSlot(this.fpMain.get());
        this.fpOffhandCache = this.toSlot(this.fpOffhand.get());

        this.fillSlots(this.itemsMain.get(), this.itemsMainCache);
        this.fillSlots(this.itemsOff.get(), this.itemsOffCache);
        this.fillStringMap(this.flippedParts.get(), this.flippedPartsCache);
        this.fillStringMap(this.pickingOverrides.get(), this.pickingOverridesCache);

        this.armorSlotsCache.clear();

        BaseType armor = this.armorSlots.get();

        if (armor != null && armor.isMap())
        {
            for (String key : armor.asMap().keys())
            {
                try
                {
                    ArmorType type = ArmorType.valueOf(key.toUpperCase());
                    ArmorSlot slot = new ArmorSlot();

                    slot.fromData(armor.asMap().getMap(key));
                    this.armorSlotsCache.put(type, slot);
                }
                catch (Exception e)
                {}
            }
        }
    }

    private ArmorSlot toSlot(BaseType data)
    {
        if (data == null)
        {
            return null;
        }

        ArmorSlot slot = new ArmorSlot();

        slot.fromData(data);

        return slot;
    }

    private void fillSlots(BaseType data, List<ArmorSlot> out)
    {
        out.clear();

        if (data != null && data.isList())
        {
            for (BaseType type : data.asList())
            {
                ArmorSlot slot = new ArmorSlot();

                slot.fromData(type);
                out.add(slot);
            }
        }
    }

    private void fillStringMap(BaseType data, Map<String, String> out)
    {
        out.clear();

        if (data != null && data.isMap())
        {
            for (String key : data.asMap().keys())
            {
                String value = data.asMap().getString(key);

                if (!value.trim().isEmpty())
                {
                    out.put(key, value);
                }
            }
        }
    }

    public List<ModelWeld> getWelds()
    {
        return this.weldsCache;
    }

    public Pose getSneakingPose()
    {
        return this.sneakingPoseCache;
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
        return this.flippedPartsCache;
    }

    public Map<String, String> getPickingOverrides()
    {
        return this.pickingOverridesCache;
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
}
