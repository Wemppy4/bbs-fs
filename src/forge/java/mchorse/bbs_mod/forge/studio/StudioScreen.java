package mchorse.bbs_mod.forge.studio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.*;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forge.ClientProxy;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.settings.values.base.*;
import mchorse.bbs_mod.settings.values.core.*;
import mchorse.bbs_mod.settings.values.numeric.*;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.*;
import mchorse.bbs_mod.utils.pose.*;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** Native Forge workspace over the original BBS document model and keyframe channels. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class StudioScreen extends StudioGuiScreen
{
    public final StudioSession session;
    public String tab = "Actor", bone = "", selectedTrack = "";
    private final List<Field> fields = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private int nextButton, left, right, timeline, inspectorY, inspectorScroll, rowScroll, actorScroll;
    private int selectedKey = -1;
    private float startTick, pixelsPerTick = 4;
    private boolean draggingKey, scrubbing;
    private Keyframe dragged;
    private Row draggedRow;
    private BaseType copiedKey;
    private String copiedFactory;
    private GuiTextField tickField;
    private int shownTick = -1;

    public StudioScreen() { session = StudioSession.get(); session.stopRecording(); }
    public void initGui()
    {
        left = Math.min(145, Math.max(108, width/5));
        right = width-Math.min(220, Math.max(158, width/4));
        timeline = height-Math.min(150, Math.max(102, height/3));
        Keyboard.enableRepeatEvents(true); rebuild();
    }
    private void button(String text,int x,int y,int w,Runnable action)
    {
        int id = ++nextButton; actions.put(id, action);
        addButton(new FlatButton(id,x,y,w,18,text));
    }
    private void tool(String text,Runnable action)
    {
        if (inspectorY>=78 && inspectorY+18<timeline-3) button(text,right+6,inspectorY,width-right-12,action);
        inspectorY += 22;
    }
    private void field(String name,String value,Consumer<String> change)
    {
        if (inspectorY>=78 && inspectorY+30<timeline-3)
        {
            GuiTextField input = new GuiTextField(fields.size(),fontRenderer,right+6,inspectorY+11,width-right-12,17);
            input.setMaxStringLength(8192); input.setText(value);
            fields.add(new Field(name,input,value,change));
        }
        inspectorY += 34;
    }
    private void number(String name,double value,java.util.function.DoubleConsumer setter)
    {
        field(name,format(value),text -> setter.accept(number(text)));
    }
    private static double number(String text)
    {
        double value = Double.parseDouble(text);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Enter a finite number");
        return value;
    }
    private static String format(double number)
    {
        return number == Math.rint(number) ? String.valueOf((long) number) : String.format(Locale.ROOT,"%.4f",number).replaceAll("0+$","");
    }
    private void edit(Runnable action) { session.checkpoint(); action.run(); session.changed(); }
    private void pick(String title,List<String> choices,Consumer<String> result)
    {
        mc.displayGuiScreen(new StudioDialog(this,title,"",choices,result));
    }
    private void prompt(String title,String value,Consumer<String> result)
    {
        mc.displayGuiScreen(new StudioDialog(this,title,value,null,result));
    }
    private void rebuild()
    {
        buttonList.clear(); actions.clear(); fields.clear(); nextButton=0;
        int x=5;
        String[] toolbar={"New","Open","Save","Save as","Undo","Redo"};
        Runnable[] operations={
            ()->prompt("New film",session.unique("film"),session::newFilm),
            ()->pick("Open film",session.files(),session::load), session::save,
            ()->prompt("Save film as",session.film.getId(),session::saveAs),
            ()->{session.history(false); selectedKey=-1;}, ()->{session.history(true); selectedKey=-1;}
        };
        for(int i=0;i<toolbar.length;i++) { int w=fontRenderer.getStringWidth(toolbar[i])+14; button(toolbar[i],x,5,w,operations[i]); x+=w+3; }
        button("Close",width-48,5,43,()->mc.displayGuiScreen(null));
        button("+ Actor",5,29,66,()->pick("Model library",ClientProxy.models.getAvailableKeys(),session::addActor));
        button("+ Camera",75,29,72,()->{session.addCamera();tab="Camera";});
        button(session.runtime.playing?"Pause":"Play",151,29,48,()->session.runtime.playing=!session.runtime.playing);
        button(session.runtime.loop?"Loop: on":"Loop",203,29,61,()->session.runtime.loop=!session.runtime.loop);
        button(session.runtime.cameraEnabled?"Camera: on":"Camera",268,29,75,()->session.runtime.camera(!session.runtime.cameraEnabled));
        button("Record",347,29,58,session::startRecording);
        if(width>485) button("Hide UI",409,29,64,()->mc.displayGuiScreen(null));

        tickField=new GuiTextField(900,fontRenderer,left+8,timeline+5,56,17); tickField.setMaxStringLength(7); tickField.setText(String.valueOf(session.runtime.tick));
        button("|<",left+69,timeline+4,25,()->seek(0));
        button("<",left+98,timeline+4,22,()->seek(session.runtime.tick-1));
        button(">",left+124,timeline+4,22,()->seek(session.runtime.tick+1));
        button("+ Key",left+150,timeline+4,46,this::insertKey);
        button("Delete",left+200,timeline+4,48,this::deleteKey);
        if(width>left+320) button("Fit",width-48,timeline+4,43,()->{startTick=0; pixelsPerTick=(width-left-14F)/Math.max(40,session.runtime.duration());});

        String[] tabs={"Actor","Form","Pose","Camera","Key"};
        int tw=(width-right-10)/tabs.length;
        for(int i=0;i<tabs.length;i++) { String name=tabs[i]; button(name,right+5+i*tw,55,tw-1,()->{tab=name;inspectorScroll=0;}); }
        inspectorY=80-inspectorScroll;
        buildRows(); buildInspector(); shownTick=session.runtime.tick;
    }
    private void buildRows()
    {
        rows.clear(); Replay replay=session.replay();
        if(session.selectedClip instanceof KeyframeClip)
        {
            KeyframeClip clip=(KeyframeClip)session.selectedClip;
            for(KeyframeChannel channel:clip.channels) rows.add(new Row("camera/"+channel.getId(),channel,clip.tick.get()));
        }
        else if(replay!=null)
        {
            for(KeyframeChannel channel:replay.keyframes.getChannels())
                if(!channel.isEmpty() || Arrays.asList("x","y","z","yaw","pitch","bodyYaw","headYaw").contains(channel.getId()))
                    rows.add(new Row(channel.getId(),channel,0));
            for(Map.Entry<TrackId,KeyframeChannel> track:replay.properties.tracks.entrySet()) rows.add(new Row(track.getKey().toKey(),track.getValue(),0));
        }
        if(findRow()==null) { selectedTrack=rows.isEmpty()?"":rows.get(0).name; selectedKey=-1; }
    }
    private Row findRow() { for(Row row:rows) if(row.name.equals(selectedTrack)) return row; return null; }
    private void buildInspector()
    {
        Replay replay=session.replay(); Form preview=session.previewForm();
        if(tab.equals("Key")) { keyInspector(); return; }
        if(tab.equals("Camera")) { cameraInspector(); return; }
        if(replay==null) { tool("Add an actor to start",()->pick("Model library",ClientProxy.models.getAvailableKeys(),session::addActor)); return; }
        if(tab.equals("Actor"))
        {
            field("Name",replay.label.get(),v->edit(()->replay.label.set(v)));
            tool(replay.enabled.get()?"Visible: yes":"Visible: no",()->edit(()->replay.enabled.set(!replay.enabled.get())));
            for(KeyframeChannel<Double> channel:Arrays.asList(replay.keyframes.x,replay.keyframes.y,replay.keyframes.z,replay.keyframes.yaw,replay.keyframes.pitch,replay.keyframes.bodyYaw,replay.keyframes.headYaw))
                number(channel.getId()+" (key)",channel.interpolate(session.runtime.tick),v->edit(()->channel.insertInheriting(session.runtime.tick,v)));
            tool("Use player position",()->edit(()->{
                replay.keyframes.x.insertInheriting(session.runtime.tick,mc.player.posX); replay.keyframes.y.insertInheriting(session.runtime.tick,mc.player.posY); replay.keyframes.z.insertInheriting(session.runtime.tick,mc.player.posZ);
                replay.keyframes.yaw.insertInheriting(session.runtime.tick,(double)mc.player.rotationYaw); replay.keyframes.bodyYaw.insertInheriting(session.runtime.tick,(double)mc.player.rotationYaw); replay.keyframes.headYaw.insertInheriting(session.runtime.tick,(double)mc.player.rotationYaw);
            }));
            tool("Duplicate actor",session::duplicateActor); tool("Delete actor",session::deleteActor);
            return;
        }
        if(!(preview instanceof ModelForm))
        {
            tool("Choose model",()->pick("Model library",ClientProxy.models.getAvailableKeys(),v->edit(()->{ModelForm f=new ModelForm();f.model.set(v);replay.form.set(f);})));
            return;
        }
        ModelForm current=(ModelForm)preview, base=(ModelForm)replay.form.get();
        if(tab.equals("Pose"))
        {
            ModelInstance entry=ClientProxy.models.getModel(base.model.get());
            List<String> bones=entry==null?new ArrayList<>():new ArrayList<>(mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(base).getBones()); Collections.sort(bones);
            if(!bones.contains(bone)) bone=bones.isEmpty()?"":bones.get(0);
            tool("Bone: "+bone,()->pick("Bone",bones,v->{bone=v;inspectorScroll=0;}));
            if(bone.isEmpty()) return;
            PoseTransform pose=(PoseTransform)current.pose.get().getOrCreate(bone).copy();
            TrackId id=TrackId.bone("",bone);
            tool("Add pose key",()->edit(()->replay.properties.getOrCreate(base,id).insertInheriting(session.runtime.tick,pose.copy())));
            transformFields(pose,()->{replay.properties.getOrCreate(base,id).insertInheriting(session.runtime.tick,pose.copy());});
            tool(pose.visible?"Bone visible: yes":"Bone visible: no",()->edit(()->{pose.visible=!pose.visible;replay.properties.getOrCreate(base,id).insertInheriting(session.runtime.tick,pose.copy());}));
            number("Pose override",pose.fix,v->edit(()->{pose.fix=(float)v;replay.properties.getOrCreate(base,id).insertInheriting(session.runtime.tick,pose.copy());}));
            tool("Clear bone track",()->edit(()->replay.properties.remove(id)));
            return;
        }
        tool("Model: "+base.model.get(),()->pick("Model library",ClientProxy.models.getAvailableKeys(),v->edit(()->base.model.set(v))));
        field("Texture (asset link)",base.texture.get()==null?"":base.texture.get().toString(),v->edit(()->base.texture.set(v.isEmpty()?null:mchorse.bbs_mod.resources.Link.create(v))));
        tool("Animation actions",()->pick("Action",new mchorse.bbs_mod.cubic.animation.Animator().getActions(),action->animationDialog(base,action)));
        ActionConfig idle=base.actions.get().getConfig("idle").copy();
        number("Idle speed",idle.speed,v->edit(()->{idle.speed=(float)v;base.actions.get().actions.put("idle",idle);}));
        number("Idle offset",idle.tick,v->edit(()->{idle.tick=(int)v;base.actions.get().actions.put("idle",idle);}));
        Transform transform=current.transform.get().copy();
        tool("Add transform key",()->edit(()->replay.properties.getOrCreate(base,TrackId.property("","transform")).insertInheriting(session.runtime.tick,transform.copy())));
        transformFields(transform,()->replay.properties.getOrCreate(base,TrackId.property("","transform")).insertInheriting(session.runtime.tick,transform.copy()));
        tool("Other form properties",()->propertyDialog(base));
        tool("Save form preset",()->prompt("Save form preset",base.model.get().replace('/','_'),name->savePreset(base,name)));
        tool("Load form preset",()->pick("Form presets",presets(),name->loadPreset(replay,name)));
        tool("Reload model assets",()->{ClientProxy.models.reload();session.changed();});
        tool("Add body part",()->pick("Body part model",ClientProxy.models.getAvailableKeys(),v->edit(()->{BodyPart p=new BodyPart("");ModelForm f=new ModelForm();f.model.set(v);p.setForm(f);base.parts.addBodyPart(p);})));
        tool("Animation state from tracks",()->prompt("State name","animation",name->edit(()->{
            AnimationState state=base.states.addState();state.customId.set(name);state.main.set(true);state.looping.set(true);state.duration.set(session.runtime.duration());state.properties.fromData(replay.properties.toData());
        })));
    }
    private void animationDialog(ModelForm form,String action)
    {
        ModelInstance model=ClientProxy.models.getModel(form.model.get());
        if(model==null) return;
        List<String> names=mchorse.bbs_mod.forge.ModelScreen.animationNames(model); names.add(0,"(none)");
        mc.displayGuiScreen(new StudioDialog(this,"Animation for "+action,"",names,name->edit(()->form.actions.get().actions.put(action,new ActionConfig(name.equals("(none)")?"":name)))));
    }
    private void transformFields(Transform transform,Runnable save)
    {
        for(int group=0;group<3;group++) for(int component=0;component<3;component++)
        {
            final int g=group,c=component;
            org.joml.Vector3f vector=g==0?transform.translate:g==1?transform.rotate:transform.scale;
            double value=vector.get(c); if(g==1)value=Math.toDegrees(value);
            number(new String[]{"Position ","Rotation ","Scale "}[g]+"XYZ".charAt(c),value,v->edit(()->{vector.setComponent(c,(float)(g==1?Math.toRadians(v):v));save.run();}));
        }
    }
    private void propertyDialog(ModelForm form)
    {
        List<String> names=new ArrayList<>();
        for(BaseValue value:form.getAll()) if(value instanceof BaseKeyframeFactoryValue && value.isVisible()) names.add(value.getId());
        mc.displayGuiScreen(new StudioDialog(this,"Form property","",names,name->{
            BaseValue value=form.get(name);
            mc.displayGuiScreen(new StudioDialog(this,name,DataToString.toString(value.toData()),null,text->edit(()->value.fromData(parse(text)))));
        }));
    }
    private void cameraInspector()
    {
        Clip clip=session.selectedClip;
        if(clip==null) { tool("Add camera clip",()->session.addCamera()); return; }
        field("Clip title",clip.title.get(),v->edit(()->clip.title.set(v)));
        number("Start tick",clip.tick.get(),v->edit(()->clip.tick.set((int)v)));
        number("Duration",clip.duration.get(),v->edit(()->clip.duration.set((int)Math.max(1,v))));
        number("Layer",clip.layer.get(),v->edit(()->clip.layer.set((int)Math.max(0,v))));
        if(clip instanceof KeyframeClip)
        {
            KeyframeClip k=(KeyframeClip)clip;
            tool("Capture player camera",()->edit(()->session.captureCamera(k,Math.max(0,session.runtime.tick-k.tick.get()))));
            for(KeyframeChannel<Double> channel:k.channels)
                number(channel.getId()+" (key)",channel.interpolate(session.runtime.tick-k.tick.get()),v->edit(()->channel.insertInheriting(Math.max(0,session.runtime.tick-k.tick.get()),v)));
        }
        else for(BaseValue value:clip.getAll())
            if(value instanceof BaseKeyframeFactoryValue && value.isVisible()) field(value.getId(),DataToString.toString(value.toData()),v->edit(()->value.fromData(parse(v))));
        tool("Duplicate clip",()->edit(()->{Clip copy=clip.copy();copy.tick.set(clip.tick.get()+clip.duration.get());session.film.camera.addClip(copy);session.selectedClip=copy;}));
        tool("Delete clip",()->edit(()->{session.film.camera.remove(clip);session.selectedClip=null;}));
    }
    private void keyInspector()
    {
        Row row=findRow(); if(row==null || !row.channel.has(selectedKey)) { tool("Select a key on the timeline",()->{}); return; }
        Keyframe key=row.channel.get(selectedKey);
        number("Key tick",key.getTick()+row.offset,v->edit(()->{key.setTick((float)Math.max(0,v-row.offset));row.channel.sort();selectedKey=row.channel.indexOf(key);}));
        tool("Easing: "+key.getInterpolation().getKey(),()->pick("Interpolation",new ArrayList<>(Interpolations.MAP.keySet()),v->edit(()->key.getInterpolation().setInterp(Interpolations.MAP.get(v)))));
        Object value=key.getValue();
        if(value instanceof Transform) { Transform t=((Transform)value).copy();transformFields(t,()->key.setValue(t.copy())); }
        else field("Value",DataToString.toString(key.getFactory().toData(value)),text->edit(()->key.setValue(key.getFactory().fromData(parse(text)))));
        number("Hold duration",key.getDuration(),v->edit(()->key.setDuration((float)Math.max(0,v))));
        tool(key.isEnabled()?"Key enabled: yes":"Key enabled: no",()->edit(()->key.setEnabled(!key.isEnabled())));
        tool("Copy key",this::copyKey);tool("Paste at cursor",this::pasteKey);tool("Delete key",this::deleteKey);
    }
    private static BaseType parse(String text)
    {
        try { return DataToString.fromString(text); }
        catch(Exception e) { throw new IllegalArgumentException("Invalid value: "+e.getMessage()); }
    }
    private File presetsFolder() { File f=new File(BBSMod.getAssetsFolder(),"forms");f.mkdirs();return f; }
    private List<String> presets()
    {
        List<String> names=new ArrayList<>();File[] files=presetsFolder().listFiles();
        if(files!=null)for(File file:files)if(file.getName().endsWith(".json"))names.add(file.getName().substring(0,file.getName().length()-5));
        Collections.sort(names);return names;
    }
    private void savePreset(Form form,String name)
    {
        try { IOUtils.writeText(new File(presetsFolder(),StudioSession.validName(name)+".json"),DataToString.toString(FormUtils.toData(form)));session.status="Form preset saved"; }
        catch(Exception e) { throw new IllegalArgumentException(e.getMessage()); }
    }
    private void loadPreset(Replay replay,String name)
    {
        try { Form f=FormUtils.fromData(DataToString.mapFromString(IOUtils.readText(new File(presetsFolder(),name+".json"))));if(f==null)throw new IOException("Invalid form");edit(()->replay.form.set(f)); }
        catch(Exception e) { throw new IllegalArgumentException(e.getMessage()); }
    }
    private void insertKey()
    {
        Row row=findRow();if(row==null)return;
        edit(()->{Keyframe key=row.channel.insertInheriting(Math.max(0,session.runtime.tick-row.offset),row.channel.getFactory().copy(row.channel.interpolate(session.runtime.tick-row.offset)));selectedKey=row.channel.indexOf(key);});
        tab="Key";inspectorScroll=0;
    }
    private void deleteKey()
    {
        Row row=findRow();if(row==null || !row.channel.has(selectedKey))return;
        edit(()->row.channel.remove(selectedKey));selectedKey=-1;
    }
    private void copyKey()
    {
        Row row=findRow();if(row==null || !row.channel.has(selectedKey))return;
        copiedKey=row.channel.get(selectedKey).toData();copiedFactory=row.channel.getFactory().getClass().getName();session.status="Key copied";
    }
    private void pasteKey()
    {
        Row row=findRow();if(row==null || copiedKey==null)return;
        if(!row.channel.getFactory().getClass().getName().equals(copiedFactory)) { session.status="Choose a track of the same value type";return; }
        edit(()->{
            Keyframe key=new Keyframe("",row.channel.getFactory());key.fromData(copiedKey);
            Keyframe next=row.channel.insertInheriting(Math.max(0,session.runtime.tick-row.offset),key.getValue());
            next.copyOverExtra(key);selectedKey=row.channel.indexOf(next);
        });
    }
    private void seek(int tick)
    {
        session.runtime.playing=false;session.runtime.seek(Math.max(0,tick));shownTick=-1;
    }
    protected void actionPerformed(GuiButton button)
    {
        try { commitFields(); Runnable action=actions.get(button.id);if(action!=null)action.run(); }
        catch(Exception e) { session.status=e.getMessage();BBSMod.LOGGER.warn("Studio action failed",e); }
        if(mc.currentScreen==this)rebuild();
    }
    private boolean commitFields()
    {
        boolean changed=false;
        for(Field field:new ArrayList<>(fields)) if(!field.original.equals(field.input.getText()))
        {
            field.change.accept(field.input.getText());field.original=field.input.getText();changed=true;
        }
        return changed;
    }
    public void updateScreen()
    {
        for(Field field:fields)field.input.updateCursorCounter();tickField.updateCursorCounter();
        if(session.runtime.tick!=shownTick && !focused() && !draggingKey)rebuild();
    }
    private boolean focused() { if(tickField.isFocused())return true;for(Field f:fields)if(f.input.isFocused())return true;return false; }
    protected void drawStudio(int mouseX,int mouseY,float partial)
    {
        drawRect(0,0,width,51,0xF51A1D25);drawRect(0,51,left,timeline,0xEE20242D);
        drawRect(right,51,width,timeline,0xF0212530);drawRect(0,timeline,width,height,0xFA191C24);
        drawString(fontRenderer,fontRenderer.trimStringToWidth(session.film.getId()+(session.dirty?" *":""),left-12),6,57,0xFFFFFF);
        int y=76,index=0;
        for(Replay replay:session.film.replays.getList())
        {
            if(index++<actorScroll)continue;if(y>timeline-22)break;
            if(replay.getId().equals(session.selectedReplay) && session.selectedClip==null)drawRect(3,y-3,left-3,y+15,0xFF3C526E);
            drawString(fontRenderer,fontRenderer.trimStringToWidth(replay.getName(),left-13),7,y,replay.enabled.get()?0xD6DCE8:0x707783);y+=20;
        }
        if(y<timeline-25) { drawString(fontRenderer,"CAMERA",7,y+3,0x8094AF);y+=21; }
        for(Clip clip:session.film.camera.get())
        {
            if(y>timeline-19)break;
            if(clip==session.selectedClip)drawRect(3,y-3,left-3,y+15,0xFF426153);
            String name=clip.title.get().isEmpty()?clip.getClass().getSimpleName():clip.title.get();
            drawString(fontRenderer,fontRenderer.trimStringToWidth(name,left-13),7,y,0xAEDBC3);y+=20;
        }
        for(Field f:fields) { drawString(fontRenderer,f.label,right+6,f.input.y-11,0xAEB9C9);f.input.drawTextBox(); }
        if(right-left>120)drawString(fontRenderer,session.runtime.cameraEnabled?"FILM CAMERA":"WORLD VIEW",left+8,58,0xFFFFFF);
        drawTimeline(mouseX,mouseY);
        tickField.drawTextBox();
        String status=session.status.isEmpty()?"Space: play  |  Ctrl+S: save  |  Ctrl+Z: undo  |  Wheel: scroll / zoom":session.status;
        drawString(fontRenderer,fontRenderer.trimStringToWidth(status,width-12),6,height-12,0xAAB4C5);
    }
    private void drawTimeline(int mouseX,int mouseY)
    {
        int ruler=timeline+28;
        drawString(fontRenderer,"TRACKS",7,timeline+10,0x8F9CAE);
        int spacing=pixelsPerTick>10?1:pixelsPerTick>3?5:pixelsPerTick>1?20:100;
        for(int t=Math.max(0,((int)startTick/spacing)*spacing);timeX(t)<width;t+=spacing)
        {
            int x=timeX(t);if(x<left)continue;
            drawRect(x,ruler,x+1,height-20,0xFF2D3440);drawString(fontRenderer,String.valueOf(t),x+2,ruler,0x909BAF);
        }
        int rowY=ruler+18;
        for(int i=rowScroll;i<rows.size() && rowY<height-32;i++,rowY+=19)
        {
            Row row=rows.get(i);
            if(row.name.equals(selectedTrack))drawRect(0,rowY-3,width,rowY+15,0x80445976);
            drawString(fontRenderer,fontRenderer.trimStringToWidth(row.name,left-11),6,rowY,0xC5CCDA);
            for(int k=0;k<row.channel.getKeyframes().size();k++)
            {
                Keyframe key=row.channel.get(k);int x=timeX(key.getTick()+row.offset);
                if(x<left+3 || x>width-5)continue;
                int color=!key.isEnabled()?0xFF626773:row.name.equals(selectedTrack)&&k==selectedKey?0xFFFFDA74:0xFF81BCD9;
                for(int h=-4;h<=4;h++)drawRect(x-(4-Math.abs(h)),rowY+4+h,x+(5-Math.abs(h)),rowY+5+h,color);
            }
        }
        int cursor=timeX(session.runtime.tick);
        if(cursor>=left && cursor<width)drawRect(cursor,ruler-2,cursor+1,height-19,0xFFFFAA55);
    }
    private int timeX(float tick) { return left+6+Math.round((tick-startTick)*pixelsPerTick); }
    private int tickAt(int x) { return Math.max(0,Math.round((x-left-6)/pixelsPerTick+startTick)); }
    protected void mouseClicked(int x,int y,int mouseButton) throws IOException
    {
        try { if(commitFields())rebuild(); }
        catch(Exception e) { session.status=e.getMessage();return; }
        for(Field f:fields)f.input.mouseClicked(x,y,mouseButton);tickField.mouseClicked(x,y,mouseButton);
        if(mouseButton==0 && x<left && y>=73 && y<timeline)
        {
            int row=(y-73)/20+actorScroll;
            if(row<session.film.replays.getList().size())
            {
                session.selectedReplay=session.film.replays.getList().get(row).getId();session.selectedClip=null;selectedKey=-1;rowScroll=0;inspectorScroll=0;rebuild();return;
            }
            int clipRow=(y-(76+Math.max(0,session.film.replays.getList().size()-actorScroll)*20+21)+3)/20;
            if(clipRow>=0 && clipRow<session.film.camera.get().size())
            {
                session.selectedClip=session.film.camera.get(clipRow);tab="Camera";selectedKey=-1;rowScroll=0;inspectorScroll=0;rebuild();return;
            }
        }
        if(mouseButton==0 && y>=timeline+27 && y<height-20)
        {
            if(y<timeline+45 && x>=left) { scrubbing=true;seek(tickAt(x));rebuild();return; }
            int rowIndex=rowScroll+(y-timeline-43)/19;
            if(rowIndex>=0 && rowIndex<rows.size())
            {
                Row row=rows.get(rowIndex);selectedTrack=row.name;selectedKey=-1;
                if(x>=left)
                {
                    for(int k=0;k<row.channel.getKeyframes().size();k++) if(Math.abs(timeX(row.channel.get(k).getTick()+row.offset)-x)<7)
                    {
                        selectedKey=k;draggingKey=true;dragged=row.channel.get(k);draggedRow=row;session.checkpoint();tab="Key";inspectorScroll=0;seek((int)(dragged.getTick()+row.offset));break;
                    }
                    if(selectedKey<0){scrubbing=true;seek(tickAt(x));}
                }
                rebuild();return;
            }
        }
        super.mouseClicked(x,y,mouseButton);
    }
    protected void mouseClickMove(int x,int y,int button,long elapsed)
    {
        if(button!=0)return;
        if(draggingKey && dragged!=null)
        {
            dragged.setTick(Math.max(0,tickAt(x)-draggedRow.offset));draggedRow.channel.sort();
            selectedKey=draggedRow.channel.indexOf(dragged);session.runtime.seek(tickAt(x));
        }
        else if(scrubbing)seek(tickAt(x));
    }
    protected void mouseReleased(int x,int y,int button)
    {
        boolean refresh=draggingKey||scrubbing;
        if(draggingKey)session.changed();draggingKey=false;dragged=null;scrubbing=false;if(refresh)rebuild();super.mouseReleased(x,y,button);
    }
    protected void keyTyped(char c,int key) throws IOException
    {
        try
        {
            if(key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER)
            {
                if(tickField.isFocused())seek((int)number(tickField.getText()));else commitFields();rebuild();return;
            }
            for(Field f:fields)if(f.input.textboxKeyTyped(c,key))return;
            if(tickField.textboxKeyTyped(c,key))return;
            if(isCtrlKeyDown())
            {
                if(key==Keyboard.KEY_S)session.save();
                if(key==Keyboard.KEY_Z){session.history(isShiftKeyDown());selectedKey=-1;}
                if(key==Keyboard.KEY_Y){session.history(true);selectedKey=-1;}
                if(key==Keyboard.KEY_C)copyKey();if(key==Keyboard.KEY_V)pasteKey();
                rebuild();return;
            }
            if(key==Keyboard.KEY_SPACE)session.runtime.playing=!session.runtime.playing;
            else if(key==Keyboard.KEY_DELETE)deleteKey();
            else if(key==Keyboard.KEY_I)insertKey();
            else if(key==Keyboard.KEY_LEFT)seek(session.runtime.tick-1);
            else if(key==Keyboard.KEY_RIGHT)seek(session.runtime.tick+1);
            else if(key==Keyboard.KEY_F6 || key==Keyboard.KEY_ESCAPE){commitFields();mc.displayGuiScreen(null);return;}
            rebuild();
        }
        catch(Exception e){session.status=e.getMessage();}
    }
    public void handleMouseInput() throws IOException
    {
        super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel==0)return;
        int x=Mouse.getEventX()*width/mc.displayWidth,y=height-Mouse.getEventY()*height/mc.displayHeight-1;
        scroll(x,y,wheel);
    }
    public void scroll(int x,int y,int wheel)
    {
        if(y>=timeline)
        {
            if(x<left)rowScroll=Math.max(0,Math.min(Math.max(0,rows.size()-1),rowScroll+(wheel<0?2:-2)));
            else if(isShiftKeyDown())startTick=Math.max(0,startTick+(wheel<0?10:-10)/pixelsPerTick*4);
            else {float tick=(x-left-6)/pixelsPerTick+startTick;pixelsPerTick=Math.max(.1F,Math.min(40,pixelsPerTick*(wheel>0?1.25F:.8F)));startTick=Math.max(0,tick-(x-left-6)/pixelsPerTick);}
        }
        else if(x>=right)inspectorScroll=Math.max(0,inspectorScroll+(wheel<0?44:-44));
        else if(x<left)actorScroll=Math.max(0,Math.min(Math.max(0,session.film.replays.getList().size()-1),actorScroll+(wheel<0?1:-1)));
        rebuild();
    }
    public boolean doesGuiPauseGame(){return false;}
    public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}

    private static final class Field
    {
        final String label;final GuiTextField input;String original;final Consumer<String> change;
        Field(String label,GuiTextField input,String original,Consumer<String> change){this.label=label;this.input=input;this.original=original;this.change=change;}
    }
    private static final class Row
    {
        final String name;final KeyframeChannel channel;final int offset;
        Row(String name,KeyframeChannel channel,int offset){this.name=name;this.channel=channel;this.offset=offset;}
    }
    private static final class FlatButton extends GuiButton
    {
        FlatButton(int id,int x,int y,int w,int h,String title){super(id,x,y,w,h,title);}
        public void drawButton(net.minecraft.client.Minecraft mc,int mouseX,int mouseY,float partial)
        {
            if(!visible)return;hovered=mouseX>=x&&mouseY>=y&&mouseX<x+width&&mouseY<y+height;
            drawRect(x,y,x+width,y+height,hovered?0xFF475D79:0xFF303B4D);
            drawCenteredString(mc.fontRenderer,mc.fontRenderer.trimStringToWidth(displayString,width-6),x+width/2,y+(height-8)/2,enabled?0xE2E8F1:0x78818F);
        }
    }
}
