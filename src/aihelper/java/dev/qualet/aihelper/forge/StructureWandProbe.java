package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.structure.*;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.structures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import java.io.File;
import java.util.List;
import java.util.UUID;

/** Exercises the actual dialog, client packet, server template writer and reply without OS input. */
public final class StructureWandProbe
{
    private static String name;
    private static BlockPos oldA,oldB;
    private static StructureRenderData expected;
    private static int generation;
    private static boolean reshaping,confirmed;

    public static JsonObject handle(String action) throws Exception
    {
        Minecraft mc=Minecraft.getMinecraft();
        if("open".equals(action))
        {
            if(mc.world==null||mc.player==null||mc.getIntegratedServer()==null)throw new IllegalStateException("Integrated test world required");
            if(name!=null)throw new IllegalStateException("Clean up the previous wand fixture first");
            oldA=StructureSelection.getA();oldB=StructureSelection.getB();
            name="aihelper/structure_probe_"+UUID.randomUUID().toString().replace("-","");
            BlockPos min=new BlockPos(mc.player.posX,Math.max(0,mc.player.posY-2),mc.player.posZ);
            StructureSelection.setA(min);StructureSelection.setB(min.add(2,1,2));
            StructureSelection.push(EnumFacing.EAST,1);StructureSelection.push(EnumFacing.EAST,-1);
            StructureSelection.move(EnumFacing.NORTH,1);StructureSelection.move(EnumFacing.SOUTH,1);
            reshaping=StructureSelection.getMin().equals(min)&&StructureSelection.getSize().equals(new Vec3i(3,2,3));
            expected=StructurePreview.capture("probe_expected",min,StructureSelection.getSize());
            generation=StructureManager.getGeneration();confirmed=false;
            UIScreen.open(new UIStructureSaveMenu(name,StructureWand::save));
        }
        if(name==null)throw new IllegalStateException("Open wand fixture first");
        if("confirm".equals(action))
        {
            List<UIStructureSavePanel> panels=UIScreen.getCurrentMenu().overlay.getChildren(UIStructureSavePanel.class);
            if(panels.size()!=1)throw new IllegalStateException("Expected actual structure save dialog");
            UIStructureSavePanel panel=panels.get(0);
            if(!panel.name.getText().equals(name)||panel.renderer.form==null)throw new IllegalStateException("Dialog did not preserve capture");
            panel.confirm();confirmed=true;
        }
        JsonObject result=new JsonObject();result.addProperty("ok",true);result.addProperty("name",name);
        result.addProperty("reshaping",reshaping);result.addProperty("confirmed",confirmed);
        File file=new File(StructureManager.getAssetsFolder(),name+".nbt");
        boolean replied=StructureManager.getGeneration()>generation;
        result.addProperty("fileExists",file.isFile());result.addProperty("replyInvalidated",replied);
        boolean recent=false;
        for(Form form:BBSModClient.getFormCategories().getRecentForms().getCategories().get(0).getForms())
            if(form instanceof StructureForm&&((StructureForm)form).structure.get().equals(StructureManager.assetId(name)))recent=true;
        result.addProperty("recentAdded",recent);
        if(file.isFile()&&replied)
        {
            StructureRenderData actual=StructureManager.get(StructureManager.assetId(name));
            result.addProperty("loaded",actual!=null);
            result.addProperty("sameSize",actual!=null&&actual.size.equals(expected.size));
            result.addProperty("sameBlocks",actual!=null&&actual.getBlocks().equals(expected.getBlocks()));
            result.addProperty("listed",StructureManager.getStructureIds().contains(StructureManager.assetId(name)));
        }
        if("cleanup".equals(action))
        {
            if(file.exists()&&!file.delete())throw new IllegalStateException("Cannot remove own probe fixture "+file);
            final String id=StructureManager.assetId(name);
            BBSModClient.getFormCategories().getRecentForms().getCategories().get(0).getDirectForms().removeIf(f->f instanceof StructureForm&&((StructureForm)f).structure.get().equals(id));
            StructureSelection.setA(oldA);StructureSelection.setB(oldB);StructureManager.invalidate();
            expected=null;name=null;result.addProperty("cleaned",true);
        }
        return result;
    }
    private StructureWandProbe(){}
}
