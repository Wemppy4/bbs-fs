package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import org.joml.Vector3f;
import java.lang.reflect.*;
import java.util.*;

/** Vanilla 1.12 model trees and rest pivots, obtained from fresh native ModelBase instances. */
public final class VanillaRigs
{
    private static final Map<String,CemHierarchy> rigs = new HashMap<>();
    private static final Map<String,String> fields = new HashMap<>();
    static
    {
        /* Actual MCP stable_39/SRG names; reflection must also work in a release client. */
        String[] names = VanillaRigs.FIELD_NAMES.split(" ");
        for (int i=0;i+1<names.length;i+=2) fields.put(names[i],names[i+1]);
    }

    public static synchronized CemHierarchy of(String entity)
    {
        CemHierarchy known=rigs.get(entity);
        if(known!=null)return known;
        Rig rig=create(entity);
        if(rig==null)return CemHierarchy.NONE;
        Map<String,String> parents=new LinkedHashMap<>();
        Map<String,Vector3f> pivots=new LinkedHashMap<>(),offsets=new LinkedHashMap<>();
        CemPartNames names=CemPartNames.of(entity);
        for(String root:rig.roots)collect(rig,root,null,new Vector3f(),names,parents,pivots,offsets);
        CemHierarchy result=new CemHierarchy(parents,pivots,offsets);
        rigs.put(entity,result);
        return result;
    }
    public static synchronized void clear(){rigs.clear();}

    /** Inspect an existing renderer's model without replacing it or changing its pose. */
    public static Rig inspect(String entity,ModelBase model)
    {
        try{return new Rig(entity,model);}
        catch(IllegalAccessException e){throw new IllegalStateException("Cannot inspect native model "+entity,e);}
    }

    static String entityId(String name)
    {
        switch(name)
        {
            case "zombified_piglin": return "zombie_pigman";
            case "snow_golem": return "snowman";
            case "iron_golem": return "villager_golem";
            case "evoker": return "evocation_illager";
            case "vindicator": return "vindication_illager";
            case "illusioner": return "illusion_illager";
            default:return name;
        }
    }
    static Rig create(String name)
    {
        Render<?> renderer;
        if(name.equals("player")||name.equals("player_slim"))
            renderer=Minecraft.getMinecraft().getRenderManager().getSkinMap().get(name.equals("player_slim")?"slim":"default");
        else
        {
            Class<? extends Entity> type=EntityList.getClass(new ResourceLocation(entityId(name)));
            if(type==null)return null;
            renderer=Minecraft.getMinecraft().getRenderManager().getEntityClassRenderObject(type);
        }
        if(!(renderer instanceof RenderLivingBase))return null;
        ModelBase prototype=((RenderLivingBase<?>)renderer).getMainModel();
        try
        {
            ModelBase model=fresh(prototype,name.equals("player_slim"));
            return model==null?null:new Rig(name,model);
        }
        catch(ReflectiveOperationException e)
        {
            BBSMod.LOGGER.warn("Cannot read native vanilla rig {} ({})",name,prototype.getClass().getName(),e);
            return null;
        }
    }
    private static ModelBase fresh(ModelBase model,boolean slim) throws ReflectiveOperationException
    {
        if(model instanceof ModelPlayer)return new ModelPlayer(0F,slim);
        Constructor<?>[] constructors=model.getClass().getDeclaredConstructors();
        Arrays.sort(constructors,Comparator.comparingInt(Constructor::getParameterCount));
        for(Constructor<?> constructor:constructors)
        {
            Class<?>[] types=constructor.getParameterTypes();Object[] args=new Object[types.length];boolean usable=true;
            for(int i=0;i<types.length;i++)
            {
                if(types[i]==float.class)args[i]=0F;
                else if(types[i]==double.class)args[i]=0D;
                else if(types[i]==boolean.class)args[i]=false;
                else if(types[i]==int.class)args[i]=model instanceof ModelSlime?16:(i==types.length-1?model.textureHeight:model.textureWidth);
                else {usable=false;break;}
            }
            if(usable){constructor.setAccessible(true);return (ModelBase)constructor.newInstance(args);}
        }
        return null;
    }
    private static void collect(Rig rig,String name,String parent,Vector3f origin,CemPartNames names,
        Map<String,String> parents,Map<String,Vector3f> pivots,Map<String,Vector3f> offsets)
    {
        ModelRenderer part=rig.parts.get(name);
        Vector3f absolute=new Vector3f(origin).add(part.rotationPointX,part.rotationPointY,part.rotationPointZ);
        Vector3f pivot=new Vector3f(-absolute.x,24F-absolute.y,absolute.z);
        Vector3f offset=new Vector3f(-part.rotationPointX,-part.rotationPointY,part.rotationPointZ);
        String external=names.optifine(name);
        pivots.putIfAbsent(external,pivot);pivots.putIfAbsent(name,pivot);
        offsets.putIfAbsent(external,offset);offsets.putIfAbsent(name,offset);
        if(parent!=null){parents.putIfAbsent(external,names.optifine(parent));parents.putIfAbsent(name,parent);}
        for(String child:rig.children.getOrDefault(name,Collections.emptyList()))collect(rig,child,name,absolute,names,parents,pivots,offsets);
    }

    /** Native model and identity-preserving traversal, shared by rest-rig and animated seed reads. */
    public static final class Rig
    {
        final ModelBase model;
        final Map<String,ModelRenderer> parts=new LinkedHashMap<>();
        final Map<String,List<String>> children=new LinkedHashMap<>();
        final List<String> roots=new ArrayList<>();
        final IdentityHashMap<ModelRenderer,String> names=new IdentityHashMap<>();
        final IdentityHashMap<ModelRenderer,float[]> rest=new IdentityHashMap<>();
        public ModelBase model(){return model;}
        public Map<String,ModelRenderer> parts(){return Collections.unmodifiableMap(parts);}
        public Map<String,List<String>> children(){return Collections.unmodifiableMap(children);}
        public List<String> roots(){return Collections.unmodifiableList(roots);}
        Rig(String entity,ModelBase model) throws IllegalAccessException
        {
            this.model=model;
            for(Class<?> type=model.getClass();type!=null&&ModelBase.class.isAssignableFrom(type);type=type.getSuperclass())
                for(Field field:type.getDeclaredFields())
                {
                    if(Modifier.isStatic(field.getModifiers()))continue;
                    if(field.getType()!=ModelRenderer.class&&field.getType()!=ModelRenderer[].class)continue;
                    field.setAccessible(true);Object value=field.get(model);
                    String name=fields.getOrDefault(field.getName(),field.getName());
                    if(value instanceof ModelRenderer)put(partName(entity,name,-1),(ModelRenderer)value);
                    else if(value instanceof ModelRenderer[]){ModelRenderer[] array=(ModelRenderer[])value;for(int i=0;i<array.length;i++)put(partName(entity,name,i),array[i]);}
                }
            for(ModelRenderer part:model.boxList)if(!names.containsKey(part))put(part.boxName==null?"part"+names.size():part.boxName,part);
            Set<ModelRenderer> nested=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Map.Entry<String,ModelRenderer> entry:new ArrayList<>(parts.entrySet()))
            {
                ModelRenderer part=entry.getValue();
                if(part.childModels!=null)for(ModelRenderer child:part.childModels)
                {
                    if(!names.containsKey(child))put(child.boxName==null?entry.getKey()+"_child"+names.size():child.boxName,child);
                    children.computeIfAbsent(entry.getKey(),key->new ArrayList<>()).add(names.get(child));nested.add(child);
                }
            }
            for(Map.Entry<String,ModelRenderer> entry:parts.entrySet())if(!nested.contains(entry.getValue()))roots.add(entry.getKey());
            for(ModelRenderer part:parts.values())rest.put(part,new float[]{part.rotationPointX,part.rotationPointY,part.rotationPointZ,part.rotateAngleX,part.rotateAngleY,part.rotateAngleZ,part.offsetX,part.offsetY,part.offsetZ,part.showModel?1:0,part.isHidden?1:0});
        }
        void put(String name,ModelRenderer part)
        {
            if(part==null||names.containsKey(part))return;
            String unique=name;int suffix=2;while(parts.containsKey(unique))unique=name+suffix++;
            names.put(part,unique);parts.put(unique,part);
        }
        void restore()
        {
            for(Map.Entry<ModelRenderer,float[]> e:rest.entrySet())
            {
                ModelRenderer p=e.getKey();float[] v=e.getValue();p.rotationPointX=v[0];p.rotationPointY=v[1];p.rotationPointZ=v[2];p.rotateAngleX=v[3];p.rotateAngleY=v[4];p.rotateAngleZ=v[5];p.offsetX=v[6];p.offsetY=v[7];p.offsetZ=v[8];p.showModel=v[9]!=0;p.isHidden=v[10]!=0;
            }
        }
    }
    private static String partName(String entity,String field,int index)
    {
        if(index>=0)
        {
            if(field.equals("blazeSticks"))return "part"+index;
            if(field.equals("guardianSpines"))return "spike"+index;
            if(field.equals("guardianTail"))return "tail"+index;
            if(field.equals("silverfishWings"))return "layer"+index;
            if(field.equals("silverfishBodyParts")||entity.equals("endermite")&&field.equals("bodyParts"))return "segment"+index;
            if(entity.equals("magma_cube")&&field.equals("segments"))return "cube"+index;
            if(field.toLowerCase(Locale.ROOT).contains("tentacle"))return "tentacle"+index;
            if(entity.equals("wither")&&field.equals("heads"))return "head"+(index+1);
            if(entity.equals("wither")&&field.equals("upperBodyParts"))return "body"+(index+1);
            return field+index;
        }
        if(field.equals("slimeBodies"))return "cube";
        if(field.equals("slimeRightEye"))return "right_eye";
        if(field.equals("slimeLeftEye"))return "left_eye";
        if(field.equals("slimeMouth"))return "mouth";
        if(entity.equals("magma_cube")&&field.equals("core"))return "inside_cube";
        if(field.equals("rightVillagerLeg"))return "right_leg";
        if(field.equals("leftVillagerLeg"))return "left_leg";
        if(field.equals("wingLeft"))return "left_wing";
        if(field.equals("wingRight"))return "right_wing";
        if(field.equals("legLeft"))return "left_leg";
        if(field.equals("legRight"))return "right_leg";
        if(entity.equals("evoker")||entity.equals("vindicator")||entity.equals("illusioner"))
        {
            if(field.equals("leg0"))return "right_leg";
            if(field.equals("leg1"))return "left_leg";
        }
        if(field.equals("wolfHeadMain"))return "head";
        if(field.equals("bipedHeadwear"))return "hat";
        if(field.equals("bipedBodyWear"))return "jacket";
        if(field.equals("bipedCape"))return "cloak";
        if(field.equals("bipedDeadmau5Head"))return "ear";
        if(field.equals("guardianBody"))return "head";
        if(field.equals("villagerNose"))return "nose";
        if(field.equals("witchHat"))return "hat";
        if(field.equals("chin"))return "red_thing";
        if(field.equals("bill"))return "beak";
        if(field.equals("magmaCubeCore"))return "inside_cube";
        if(entity.equals("snow_golem")){if(field.equals("body"))return "upper_body";if(field.equals("bottomBody"))return "lower_body";if(field.equals("leftHand"))return "left_arm";if(field.equals("rightHand"))return "right_arm";}
        if(entity.equals("spider")||entity.equals("cave_spider")){if(field.equals("spiderNeck"))return "body0";if(field.equals("spiderBody"))return "body1";}
        String name=field.replaceFirst("^(biped|ironGolem|ocelot|rabbit|silverfish|spider|villager|wolf|guardian|squid|blaze|bat)","");
        if(name.length()>0)name=Character.toLowerCase(name.charAt(0))+name.substring(1);
        name=name.replace("Armwear","Sleeve").replace("Legwear","Pants").replace("Back","Hind").replace("Thigh","Haunch").replace("HeadMain","Head");
        name=name.replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
        if(name.equals("outer_left_wing"))name="left_wing_tip";
        if(name.equals("outer_right_wing"))name="right_wing_tip";
        if(name.equals("mane"))name="upper_body";
        return CemPartNames.of(entity).vanilla(name);
    }

    private static final String FIELD_NAMES = "field_178740_a standRightSide field_178738_b standLeftSide field_178739_c standWaist field_178737_d standBase field_178690_a bannerSlate field_178688_b bannerStand field_178689_c bannerTop field_82895_a batHead field_82893_b batBody field_82894_c batRightWing field_82891_d batLeftWing field_82892_e batOuterRightWing field_82890_f batOuterLeftWing field_193772_a headPiece field_193773_b footPiece field_193774_c legs field_78116_c bipedHead field_178720_f bipedHeadwear field_78115_e bipedBody field_178723_h bipedRightArm field_178724_i bipedLeftArm field_178721_j bipedRightLeg field_178722_k bipedLeftLeg field_78106_a blazeSticks field_78105_b blazeHead field_78103_a boatSides field_187057_b paddles field_187058_c noWater field_78102_a coverRight field_78100_b coverLeft field_78101_c pagesRight field_78098_d pagesLeft field_78099_e flippingPageRight field_78096_f flippingPageLeft field_78097_g bookSpine field_78234_a chestLid field_78232_b chestBelow field_78233_c chestKnob field_78142_a head field_78140_b body field_78141_c rightLeg field_78138_d leftLeg field_78139_e rightWing field_78136_f leftWing field_78137_g bill field_78143_h chin field_78135_a head field_78133_b creeperArmor field_78134_c body field_78131_d leg1 field_78132_e leg2 field_78129_f leg3 field_78130_g leg4 field_78221_a head field_78219_b spine field_78220_c jaw field_78217_d body field_78218_e rearLeg field_78215_f frontLeg field_78216_g rearLegTip field_78226_h frontLegTip field_78227_i rearFoot field_78224_j frontFoot field_78225_k wing field_78222_l wingTip field_187070_a head field_187071_b jaw field_187060_a rightWing field_187061_b leftWing field_78230_a cube field_78228_b glass field_78229_c base field_178713_d bodyParts field_191213_a base field_191214_b upperJaw field_191215_c lowerJaw field_78128_a body field_78127_b tentacles field_178710_a guardianBody field_178708_b guardianEye field_178709_c guardianSpines field_178707_d guardianTail field_110709_a head field_178711_b upperMouth field_178712_c lowerMouth field_110705_d horseLeftEar field_110706_e horseRightEar field_110703_f muleLeftEar field_110704_g muleRightEar field_110716_h neck field_110717_i horseFaceRopes field_110714_j mane field_110715_k body field_110712_l tailBase field_110713_m tailMiddle field_110710_n tailTip field_110711_o backLeftLeg field_110719_v backLeftShin field_110718_w backLeftHoof field_110722_x backRightLeg field_110721_y backRightShin field_110720_z backRightHoof field_110688_A frontLeftLeg field_110689_B frontLeftShin field_110690_C frontLeftHoof field_110684_D frontRightLeg field_110685_E frontRightShin field_110686_F frontRightHoof field_110687_G muleLeftChest field_110695_H muleRightChest field_110696_I horseSaddleBottom field_110697_J horseSaddleFront field_110698_K horseSaddleBack field_110691_L horseLeftSaddleRope field_110692_M horseLeftSaddleMetal field_110693_N horseRightSaddleRope field_110694_O horseRightSaddleMetal field_110700_P horseLeftFaceMetal field_110699_Q horseRightFaceMetal field_110702_R horseLeftRein field_110701_S horseRightRein field_178717_b head field_191217_a head field_193775_b hat field_191218_b body field_191219_c arms field_191220_d leg0 field_191221_e leg1 field_191222_f nose field_191223_g rightArm field_191224_h leftArm field_78178_a ironGolemHead field_78176_b ironGolemBody field_78177_c ironGolemRightArm field_78174_d ironGolemLeftArm field_78175_e ironGolemLeftLeg field_78173_f ironGolemRightLeg field_110723_a knotRenderer field_191226_i chest1 field_191227_j chest2 field_191225_a main field_78109_a segments field_78108_b core field_78154_a sideModels field_78161_a ocelotBackLeftLeg field_78159_b ocelotBackRightLeg field_78160_c ocelotFrontLeftLeg field_78157_d ocelotFrontRightLeg field_78158_e ocelotTail field_78155_f ocelotTail2 field_78156_g ocelotHead field_78162_h ocelotBody field_192764_a body field_192765_b tail field_192766_c wingLeft field_192767_d wingRight field_192768_e head field_192769_f head2 field_192770_g beak1 field_192771_h beak2 field_192772_i feather field_192773_j legLeft field_192774_k legRight field_178734_a bipedLeftArmwear field_178732_b bipedRightArmwear field_178733_c bipedLeftLegwear field_178731_d bipedRightLegwear field_178730_v bipedBodyWear field_178729_w bipedCape field_178736_x bipedDeadmau5Head field_78150_a head field_78148_b body field_78149_c leg1 field_78146_d leg2 field_78147_e leg3 field_78144_f leg4 field_178698_a rabbitLeftFoot field_178696_b rabbitRightFoot field_178697_c rabbitLeftThigh field_178694_d rabbitRightThigh field_178695_e rabbitBody field_178692_f rabbitLeftArm field_178693_g rabbitRightArm field_178704_h rabbitHead field_178705_i rabbitRightEar field_178702_j rabbitLeftEar field_178703_k rabbitTail field_178700_l rabbitNose field_187063_a plate field_187064_b handle field_187067_b base field_187068_c lid field_187066_a head field_187069_a renderer field_78166_a signBoard field_78165_b signStick field_78171_a silverfishBodyParts field_78169_b silverfishWings field_82896_a skeletonHead field_78200_a slimeBodies field_78198_b slimeRightEye field_78199_c slimeLeftEye field_78197_d slimeMouth field_78196_a body field_78194_b bottomBody field_78195_c head field_78192_d rightHand field_78193_e leftHand field_78209_a spiderHead field_78207_b spiderNeck field_78208_c spiderBody field_78205_d spiderLeg1 field_78206_e spiderLeg2 field_78203_f spiderLeg3 field_78204_g spiderLeg4 field_78212_h spiderLeg5 field_78213_i spiderLeg6 field_78210_j spiderLeg7 field_78211_k spiderLeg8 field_78202_a squidBody field_78201_b squidTentacles field_191229_a leftWing field_191230_b rightWing field_78191_a villagerHead field_78189_b villagerBody field_78190_c villagerArms field_78187_d rightVillagerLeg field_78188_e leftVillagerLeg field_82898_f villagerNose field_82901_h mole field_82902_i witchHat field_82905_a upperBodyParts field_82904_b heads field_78185_a wolfHeadMain field_78183_b wolfBody field_78184_c wolfLeg1 field_78181_d wolfLeg2 field_78182_e wolfLeg3 field_78179_f wolfLeg4 field_78180_g wolfTail field_78186_h wolfMane";
    private VanillaRigs() {}
}
