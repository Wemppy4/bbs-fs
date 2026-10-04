package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.forms.forms.SplineForm;

import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.List;

public class ExtraFormSection extends FormSection
{
    private static final List<String> mobAnimalsIds = Arrays.asList("minecraft:axolotl", "minecraft:bat", "minecraft:bee", "minecraft:camel", "minecraft:cat", "minecraft:chicken", "minecraft:cod", "minecraft:cow", "minecraft:dolphin", "minecraft:donkey", "minecraft:fox", "minecraft:frog", "minecraft:glow_squid", "minecraft:goat", "minecraft:horse", "minecraft:llama", "minecraft:mooshroom", "minecraft:mule", "minecraft:ocelot", "minecraft:panda", "minecraft:parrot", "minecraft:pig", "minecraft:polar_bear", "minecraft:pufferfish", "minecraft:rabbit", "minecraft:salmon", "minecraft:sheep", "minecraft:skeleton_horse", "minecraft:sniffer", "minecraft:squid", "minecraft:tropical_fish", "minecraft:turtle", "minecraft:wolf", "minecraft:zombie_horse");
    private static final List<String> mobNeutralIds = Arrays.asList("minecraft:allay", "minecraft:enderman", "minecraft:iron_golem", "minecraft:piglin", "minecraft:piglin_brute", "minecraft:snow_golem", "minecraft:strider", "minecraft:villager", "minecraft:wandering_trader");
    private static final List<String> mobHostileIds = Arrays.asList("minecraft:blaze", "minecraft:cave_spider", "minecraft:creeper", "minecraft:drowned", "minecraft:elder_guardian", "minecraft:ender_dragon", "minecraft:endermite", "minecraft:evoker", "minecraft:ghast", "minecraft:guardian", "minecraft:hoglin", "minecraft:husk", "minecraft:illusioner", "minecraft:magma_cube", "minecraft:phantom", "minecraft:pillager", "minecraft:ravager", "minecraft:silverfish", "minecraft:skeleton", "minecraft:slime", "minecraft:spider", "minecraft:stray", "minecraft:vex", "minecraft:vindicator", "minecraft:warden", "minecraft:witch", "minecraft:wither", "minecraft:wither_skeleton", "minecraft:zoglin", "minecraft:zombie", "minecraft:zombie_villager", "minecraft:zombified_piglin");
    private static final List<String> mobMiscIds = Arrays.asList("minecraft:armor_stand", "minecraft:arrow", "minecraft:boat", "minecraft:end_crystal", "minecraft:lightning_bolt", "minecraft:minecart", "minecraft:shulker_bullet", "minecraft:spectral_arrow", "minecraft:trident");

    private FormCategory mobsAnimals;
    private FormCategory mobsNeutral;
    private FormCategory mobsHostile;
    private FormCategory mobsMisc;
    private FormCategory extra;
    private List<FormCategory> categories;

    public ExtraFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        FormCategory extra = new FormCategory(UIKeys.FORMS_CATEGORIES_EXTRA, this.parent.preferences.visible("extra")).icon(Icons.SHAPES);
        AnchorForm anchor = new AnchorForm();
        BillboardForm billboard = new BillboardForm();
        LabelForm label = new LabelForm();
        ExtrudedForm extruded = new ExtrudedForm();
        BlockForm block = new BlockForm();
        ItemForm item = new ItemForm();
        StructureForm structure = new StructureForm();
        VanillaParticleForm vanillaParticle = new VanillaParticleForm();
        TrailForm trail = new TrailForm();
        VideoForm video = new VideoForm();

        billboard.texture.set(Link.assets("textures/error.png"));
        extruded.texture.set(Link.assets("textures/error.png"));
        block.blockState.set(Blocks.GRASS.getDefaultState());
        item.stack.set(new ItemStack(Items.STICK));

        addReady(extra, anchor);
        addReady(extra, new SplineForm());
        addReady(extra, billboard);
        addReady(extra, label);
        addReady(extra, extruded);
        addReady(extra, block);
        addReady(extra, item);
        addReady(extra, structure);
        addReady(extra, vanillaParticle);
        addReady(extra, trail);
        addReady(extra, video);
        addReady(extra, new FramebufferForm());

        this.mobsAnimals = new FormCategory(UIKeys.FORMS_CATEGORIES_MOBS_ANIMALS, this.parent.preferences.visible("mobs_animals")).icon(Icons.CHICKEN);
        this.mobsNeutral = new FormCategory(UIKeys.FORMS_CATEGORIES_MOBS_NEUTRAL, this.parent.preferences.visible("mobs_neutral")).icon(Icons.PLAYER);
        this.mobsHostile = new FormCategory(UIKeys.FORMS_CATEGORIES_MOBS_HOSTILE, this.parent.preferences.visible("mobs_hostile")).icon(Icons.SKULL);
        this.mobsMisc = new FormCategory(UIKeys.FORMS_CATEGORIES_MOBS_MISC, this.parent.preferences.visible("mobs_misc")).icon(Icons.MORE);
        this.extra = extra;

        this.fillMobs(this.mobsAnimals, mobAnimalsIds);
        this.fillMobs(this.mobsNeutral, mobNeutralIds);
        this.fillMobs(this.mobsHostile, mobHostileIds);
        this.fillMobs(this.mobsMisc, mobMiscIds);

        this.categories = Arrays.asList(this.extra, this.mobsAnimals, this.mobsNeutral, this.mobsHostile, this.mobsMisc);
    }

    private void fillMobs(FormCategory category, List<String> ids)
    {
        for (String mobId : ids)
        {
            switch (mobId)
            {
                case "minecraft:iron_golem": mobId = "minecraft:villager_golem"; break;
                case "minecraft:snow_golem": mobId = "minecraft:snowman"; break;
                case "minecraft:evoker": mobId = "minecraft:evocation_illager"; break;
                case "minecraft:vindicator": mobId = "minecraft:vindication_illager"; break;
                case "minecraft:illusioner": mobId = "minecraft:illusion_illager"; break;
                case "minecraft:zombified_piglin": mobId = "minecraft:zombie_pigman"; break;
                case "minecraft:end_crystal": mobId = "minecraft:ender_crystal"; break;
            }

            if (!EntityList.getEntityNameList().contains(new ResourceLocation(mobId)))
            {
                continue;
            }

            MobForm form = new MobForm();

            form.mobID.set(mobId);
            category.addForm(form);
        }
    }

    private static void addReady(FormCategory category, Form form)
    {
        if (BBSMod.getForms().getTypeSilent(form) != null && FormUtilsClient.getRenderer(form) != null)
        {
            category.addForm(form);
        }
    }

    @Override
    public List<FormCategory> getCategories()
    {
        return this.categories;
    }
}
