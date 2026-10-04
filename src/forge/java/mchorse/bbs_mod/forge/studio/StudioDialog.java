package mchorse.bbs_mod.forge.studio;

import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Searchable library picker and named-document input, shared by the studio. */
public final class StudioDialog extends StudioGuiScreen
{
    private final GuiScreen parent;
    private final String title, initial;
    private final List<String> choices;
    private final Consumer<String> accept;
    private List<String> filtered = new ArrayList<>();
    private GuiTextField input;
    private int first, left, top, panelWidth;
    private String error = "";
    public StudioDialog(GuiScreen parent, String title, String initial, List<String> choices, Consumer<String> accept)
    {
        this.parent = parent; this.title = title; this.initial = initial; this.choices = choices; this.accept = accept;
    }
    public void initGui()
    {
        panelWidth = Math.min(380, width - 24); left = (width-panelWidth)/2; top = 30;
        input = new GuiTextField(0, fontRenderer, left+10, top+27, panelWidth-20, 20);
        input.setMaxStringLength(512); input.setText(initial); input.setFocused(true); filter();
        buttonList.clear();
        addButton(new GuiButton(1, left+10, height-42, (panelWidth-26)/2, 20, "OK"));
        addButton(new GuiButton(2, left+16+(panelWidth-26)/2, height-42, (panelWidth-26)/2, 20, "Cancel"));
        Keyboard.enableRepeatEvents(true);
    }
    private void filter()
    {
        filtered.clear(); first = 0;
        if (choices != null) for (String choice : choices)
            if (choice.toLowerCase(Locale.ROOT).contains(input.getText().toLowerCase(Locale.ROOT))) filtered.add(choice);
    }
    private void accept(String value)
    {
        try { accept.accept(value); if (mc.currentScreen == this) mc.displayGuiScreen(parent); }
        catch (RuntimeException e) { error = e.getMessage(); }
    }
    protected void actionPerformed(GuiButton button)
    {
        if (button.id == 2) mc.displayGuiScreen(parent);
        else if (choices == null) accept(input.getText());
        else if (!filtered.isEmpty()) accept(filtered.get(0));
    }
    protected void drawStudio(int x, int y, float partial)
    {
        drawDefaultBackground(); drawRect(left, top, left+panelWidth, height-18, 0xEF20232B);
        drawString(fontRenderer, title, left+10, top+10, 0xFFFFFF); input.drawTextBox();
        int rowY = top+55;
        for (int i=first; i<filtered.size() && rowY<height-66; i++, rowY+=18)
        {
            if (x>=left+5 && x<left+panelWidth-5 && y>=rowY-3 && y<rowY+14) drawRect(left+5,rowY-3,left+panelWidth-5,rowY+14,0xFF40506B);
            drawString(fontRenderer,fontRenderer.trimStringToWidth(filtered.get(i),panelWidth-20),left+10,rowY,0xCED5E2);
        }
        drawString(fontRenderer,fontRenderer.trimStringToWidth(error,panelWidth-20),left+10,height-59,0xFF8888);
    }
    protected void mouseClicked(int x,int y,int button) throws IOException
    {
        input.mouseClicked(x,y,button);
        if(button==0 && choices!=null && x>=left && x<left+panelWidth && y>=top+52 && y<height-66)
        {
            int i=first+(y-top-52)/18; if(i<filtered.size()) { accept(filtered.get(i)); return; }
        }
        super.mouseClicked(x,y,button);
    }
    protected void keyTyped(char c,int key) throws IOException
    {
        if(key==Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        if(key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER) { actionPerformed(buttonList.get(0)); return; }
        if(input.textboxKeyTyped(c,key)) filter();
    }
    public void handleMouseInput() throws IOException
    {
        super.handleMouseInput(); int wheel=Mouse.getEventDWheel();
        if(wheel!=0) first=Math.max(0,Math.min(Math.max(0,filtered.size()-1),first+(wheel<0?3:-3)));
    }
    public boolean doesGuiPauseGame() { return false; }
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
