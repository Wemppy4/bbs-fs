package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.utils.IOUtils;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.util.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Small first-stage model workspace. Data, bones, animation and geometry remain BBS FS. */
public class ModelScreen extends GuiScreen
{
    public final ModelForm form = new ModelForm();
    private final BlockPos target;
    private List<String> models;
    private int first;
    private float yaw=25, zoom=1;
    private boolean playing = true;
    private float frame;
    private String bone="", status="";
    private GuiTextField search;
    private final mchorse.bbs_mod.forms.entities.StubEntity previewEntity = new mchorse.bbs_mod.forms.entities.StubEntity();
    public ModelScreen(ModelTileEntity tile) {
        target=tile==null?null:tile.getPos();
        if (tile!=null) form.fromData(tile.form.toData());
        else form.model.set("player/steve");
    }
    private String animation() { return form.actions.get().getConfig("idle").name; }
    public void initGui() {
        search=new GuiTextField(100,fontRenderer,8,28,140,18);
        search.setMaxStringLength(100);
        models=ClientProxy.models.getAvailableKeys();
        buttons(); Keyboard.enableRepeatEvents(true);
    }
    private void buttons() {
        buttonList.clear();
        addButton(new GuiButton(1,8,height-48,68,20,"Reload"));
        addButton(new GuiButton(2,80,height-48,68,20,"Close"));
        addButton(new GuiButton(3,158,height-74,78,20,playing?"Pause":"Play"));
        addButton(new GuiButton(4,240,height-74,Math.max(80,width-248),20,"Animation >"));
        addButton(new GuiButton(5,158,height-48,78,20,"Save form"));
        addButton(new GuiButton(6,240,height-48,78,20,"Load form"));
        addButton(new GuiButton(7,322,height-48,Math.max(64,width-330),20,target==null?"Place":"Apply"));
        addButton(new GuiButton(8,158,50,Math.max(100,width-230),20,"Bone >"));
        addButton(new GuiButton(9,width-66,50,28,20,"-"));
        addButton(new GuiButton(10,width-36,50,28,20,"+"));
    }
    private File preset() { return new File(BBSMod.getAssetsFolder().getParentFile(),"settings/forge-model-form.json"); }
    protected void actionPerformed(GuiButton button) throws IOException {
        ModelInstance entry=ClientProxy.models.getModel(form.model.get());
        try {
            switch(button.id) {
                case 1: ClientProxy.models.reload(); models=ClientProxy.models.getAvailableKeys(); first=0; break;
                case 2: mc.displayGuiScreen(null); return;
                case 3: frame=mc.world==null?0:mc.world.getTotalWorldTime(); playing=!playing; break;
                case 4:
                    if(entry!=null) { List<String> names=animationNames(entry); names.add(0,""); form.actions.get().actions.put("idle", new mchorse.bbs_mod.cubic.animation.ActionConfig(names.get((names.indexOf(animation())+1)%names.size()))); } break;
                case 5: preset().getParentFile().mkdirs(); IOUtils.writeText(preset(),DataToString.toString(form.toData())); status="Form saved"; break;
                case 6: form.fromData(DataToString.mapFromString(IOUtils.readText(preset()))); status="Form loaded"; break;
                case 7:
                    if(mc.player==null) break;
                    CommonProxy.NETWORK.sendToServer(new ModelEditPacket(target,form));
                    mc.displayGuiScreen(null); return;
                case 8:
                    if(entry!=null) { List<String> bones=new ArrayList<>(mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form).getBones()); Collections.sort(bones); if(!bones.isEmpty()) bone=bones.get((bones.indexOf(bone)+1)%bones.size()); } break;
                case 9: case 10:
                    if(!bone.isEmpty()) form.pose.get().getOrCreate(bone).rotate.z+=(button.id==10?1:-1)*(float)Math.toRadians(10); break;
                default: break;
            }
            buttons();
        } catch(Exception e) { status=e.getMessage(); BBSMod.LOGGER.warn("Model workspace action failed",e); }
    }
    public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground(); drawRect(4,4,152,height-4,0xDD181A20); drawRect(156,4,width-4,height-4,0xDD252932);
        drawString(fontRenderer,"BBS FS | Models",8,12,0xFFFFFF);
        search.drawTextBox();
        int y=52;
        for(int i=first;i<models.size() && y<height-54;i++,y+=16) {
            String id=models.get(i);
            if(id.equals(form.model.get())) drawRect(6,y-2,150,y+13,0xFF45577A);
            drawString(fontRenderer,fontRenderer.trimStringToWidth(id,138),8,y,id.equals(form.model.get())?0xFFFFFF:0xB8BBC6);
        }
        drawString(fontRenderer,fontRenderer.trimStringToWidth(form.model.get(),width-170),164,12,0xFFFFFF);
        drawString(fontRenderer,"Animation: "+(animation().isEmpty()?"rest":animation()),164,28,0xBBC4D0);
        ModelInstance entry=ClientProxy.models.getModel(form.model.get());
        if(entry!=null) {
            float size=Math.min((width-160)/2.5F,(height-150)/2.1F)*zoom;
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(156+(width-156)/2F,height-85,100);
                GlStateManager.scale(size,-size,size);
                GlStateManager.rotate(12,1,0,0); GlStateManager.rotate(yaw,0,1,0);
                GlStateManager.enableDepth();
                long tick=playing && mc.world!=null?mc.world.getTotalWorldTime():(long)frame;
                previewEntity.setAge((int)tick);
                mchorse.bbs_mod.forms.FormUtilsClient.render(form,new mchorse.bbs_mod.forms.renderers.FormRenderingContext().set(
                    mchorse.bbs_mod.forms.renderers.FormRenderType.ENTITY,previewEntity,new mchorse.bbs_mod.graphics.MatrixStack(),0xF000F0,10<<16,playing?partial:0).inUI().modelRenderer(tick));
                GlStateManager.disableDepth();
            } finally { GlStateManager.popMatrix(); }
            drawString(fontRenderer,"Bone: "+bone,164,76,0xBBC4D0);
        } else drawString(fontRenderer,"Model error (see log)",164,90,0xFF7777);
        drawString(fontRenderer,status.isEmpty()?"Q/E: turn | Wheel: zoom":status,158,height-20,0xA8ADBB);
        super.drawScreen(mouseX,mouseY,partial);
    }
    protected void mouseClicked(int x,int y,int button) throws IOException {
        search.mouseClicked(x,y,button);
        if(button==0 && x>=6 && x<150 && y>=50 && y<height-54) {
            int index=first+(y-50)/16;
            if(index>=0 && index<models.size()) { form.model.set(models.get(index)); form.actions.get().actions.clear(); bone=""; form.pose.get().transforms.clear(); }
        }
        super.mouseClicked(x,y,button);
    }
    protected void keyTyped(char c,int key) throws IOException {
        if(search.textboxKeyTyped(c,key)) {
            models=new ArrayList<>();
            for(String id:ClientProxy.models.getAvailableKeys()) if(id.toLowerCase(Locale.ROOT).contains(search.getText().toLowerCase(Locale.ROOT))) models.add(id);
            first=0; return;
        }
        if(key==Keyboard.KEY_Q) yaw-=15;
        if(key==Keyboard.KEY_E) yaw+=15;
        super.keyTyped(c,key);
    }
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel=Mouse.getEventDWheel();
        if(wheel!=0) {
            int x=Mouse.getEventX()*width/mc.displayWidth;
            if(x<152) first=Math.max(0,Math.min(Math.max(0,models.size()-1),first+(wheel<0?1:-1)));
            else zoom=Math.max(0.2F,Math.min(4,zoom+(wheel>0?0.1F:-0.1F)));
        }
    }
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    @Override public void updateScreen() {
        previewEntity.setWorld(mc.world); previewEntity.setForm(form);
        if(playing)form.update(previewEntity);
    }
    public boolean doesGuiPauseGame() { return false; }
    public mchorse.bbs_mod.forms.entities.IEntity getPreviewEntity() { return previewEntity; }
    public static List<String> animationNames(ModelInstance model)
    {
        List<String> names=model.animations==null?new ArrayList<>():new ArrayList<>(model.animations.animations.keySet());
        Collections.sort(names); return names;
    }
}
