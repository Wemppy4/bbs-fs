package mchorse.bbs_mod.forge.studio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/** Editor density is independent of the oversized vanilla Auto HUD scale. */
abstract class StudioGuiScreen extends GuiScreen
{
    private float scaleX=1, scaleY=1;
    @Override public void setWorldAndResolution(Minecraft mc,int width,int height)
    {
        int factor=Math.max(1,Math.min(new ScaledResolution(mc).getScaleFactor(),Math.min(mc.displayWidth/800,mc.displayHeight/450)));
        int editorWidth=(int)Math.ceil(mc.displayWidth/(double)factor),editorHeight=(int)Math.ceil(mc.displayHeight/(double)factor);
        scaleX=width/(float)editorWidth;scaleY=height/(float)editorHeight;
        super.setWorldAndResolution(mc,editorWidth,editorHeight);
    }
    @Override public final void drawScreen(int mouseX,int mouseY,float partial)
    {
        GlStateManager.pushMatrix();
        try
        {
            GlStateManager.scale(scaleX,scaleY,1);
            mouseX=Math.round(mouseX/scaleX);mouseY=Math.round(mouseY/scaleY);
            drawStudio(mouseX,mouseY,partial);
            super.drawScreen(mouseX,mouseY,partial);
        }
        finally {GlStateManager.popMatrix();}
    }
    protected abstract void drawStudio(int mouseX,int mouseY,float partial);
}
