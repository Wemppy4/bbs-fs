package mchorse.bbs_mod.particles.vanilla;
import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;
/** Saved names and arguments resolve to the native 1.12 particle factory. */
public final class VanillaParticleEffect {
    public final EnumParticleTypes type;
    public final int[] arguments;
    private static final Map<String,EnumParticleTypes> TYPES = new HashMap<>();
    static {
        for(EnumParticleTypes type:EnumParticleTypes.values()) { TYPES.put(type.getParticleName(),type); TYPES.put(type.name().toLowerCase(java.util.Locale.ROOT),type); }
        alias("smoke",EnumParticleTypes.SMOKE_NORMAL); alias("large_smoke",EnumParticleTypes.SMOKE_LARGE);
        alias("bubble",EnumParticleTypes.WATER_BUBBLE); alias("splash",EnumParticleTypes.WATER_SPLASH); alias("fishing",EnumParticleTypes.WATER_WAKE);
        alias("underwater",EnumParticleTypes.SUSPENDED); alias("enchant",EnumParticleTypes.ENCHANTMENT_TABLE);
        alias("enchanted_hit",EnumParticleTypes.CRIT_MAGIC); alias("effect",EnumParticleTypes.SPELL); alias("instant_effect",EnumParticleTypes.SPELL_INSTANT);
        alias("entity_effect",EnumParticleTypes.SPELL_MOB); alias("ambient_entity_effect",EnumParticleTypes.SPELL_MOB_AMBIENT); alias("witch",EnumParticleTypes.SPELL_WITCH);
        alias("falling_water",EnumParticleTypes.WATER_DROP); alias("falling_lava",EnumParticleTypes.LAVA);
        alias("dripping_water",EnumParticleTypes.DRIP_WATER); alias("dripping_lava",EnumParticleTypes.DRIP_LAVA);
        alias("angry_villager",EnumParticleTypes.VILLAGER_ANGRY); alias("happy_villager",EnumParticleTypes.VILLAGER_HAPPY);
        alias("mycelium",EnumParticleTypes.TOWN_AURA); alias("dust",EnumParticleTypes.REDSTONE); alias("item",EnumParticleTypes.ITEM_CRACK);
        alias("block",EnumParticleTypes.BLOCK_CRACK); alias("item_snowball",EnumParticleTypes.SNOWBALL); alias("item_slime",EnumParticleTypes.SLIME);
        alias("explosion",EnumParticleTypes.EXPLOSION_LARGE); alias("explosion_emitter",EnumParticleTypes.EXPLOSION_HUGE);
        alias("firework",EnumParticleTypes.FIREWORKS_SPARK); alias("elder_guardian",EnumParticleTypes.MOB_APPEARANCE);
        alias("sweep_attack",EnumParticleTypes.SWEEP_ATTACK); alias("totem_of_undying",EnumParticleTypes.TOTEM);
    }
    private static void alias(String name,EnumParticleTypes type){TYPES.put(name,type);}
    private VanillaParticleEffect(EnumParticleTypes type,int[] args){this.type=type;this.arguments=args;}
    public static VanillaParticleEffect from(ParticleSettings settings){
        EnumParticleTypes type=TYPES.get(settings.particle.getPath());
        if(type==null)type=EnumParticleTypes.FLAME;
        String raw=settings.arguments.trim(); String[] tokens=raw.isEmpty()?new String[0]:raw.split("\\s+");
        int[] args=new int[type.getArgumentCount()];
        try {
            if(args.length>0 && tokens.length>0){
                if(type==EnumParticleTypes.ITEM_CRACK && tokens[0].contains(":")){Item item=Item.REGISTRY.getObject(new ResourceLocation(tokens[0]));args[0]=Item.getIdFromItem(item);if(args.length>1&&tokens.length>1)args[1]=Integer.parseInt(tokens[1]);}
                else if((type==EnumParticleTypes.BLOCK_CRACK||type==EnumParticleTypes.BLOCK_DUST||type==EnumParticleTypes.FALLING_DUST)&&tokens[0].contains(":")){Block block=Block.REGISTRY.getObject(new ResourceLocation(tokens[0]));int meta=tokens.length>1?Integer.parseInt(tokens[1]):0;args[0]=Block.getStateId(block.getStateFromMeta(meta));}
                else for(int i=0;i<args.length&&i<tokens.length;i++)args[i]=Integer.parseInt(tokens[i]);
            }
        } catch(RuntimeException invalid){ java.util.Arrays.fill(args,0); }
        return new VanillaParticleEffect(type,args);
    }
    public void spawn(World world,double x,double y,double z,double vx,double vy,double vz){world.spawnParticle(type,true,x,y,z,vx,vy,vz,arguments);}
}
