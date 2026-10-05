package dev.qualet.aihelper.forge;

import com.google.gson.*;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.forms.structure.StructureManager;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.*;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.*;
import java.nio.file.*;
import java.io.InputStream;
import java.util.*;

/** Real widget ordering and renderer boundaries, with no corrective GL wrapper around the draw. */
public final class NativeUiRenderProbe extends UIBaseMenu
{
    private final Form[] forms = new Form[4];
    private final UIFormRenderer viewport = new UIFormRenderer()
    {
        @Override protected void renderUserModel(UIContext context)
        {
            super.renderUserModel(context);
            try
            {
                java.lang.reflect.Field queued = mchorse.bbs_mod.forms.FormTranslucentQueue.class.getDeclaredField("commands"); queued.setAccessible(true);
                queuedDraws = ((List<?>) queued.get(null)).size();
            }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
    };
    private final List<Path> files = new ArrayList<>();
    private JsonObject frame = new JsonObject();
    private int frames;
    private int queuedDraws;
    private final boolean oldQueue = mchorse.bbs_mod.BBSSettings.translucencyQueue.get();
    private final boolean oldFreeze = mchorse.bbs_mod.BBSSettings.freezeModels.get();
    private boolean reference;
    private boolean materials;
    private String structure;

    private NativeUiRenderProbe() throws Exception
    {
        ModelForm model = new ModelForm(); model.model.set("player/steve"); model.texture.set(new Link(Link.COLOR, "ffffffff")); forms[0] = model;
        MobForm mob = new MobForm(); mob.mobID.set("minecraft:player"); mob.texture.set(new Link(Link.COLOR, "ffffffff")); forms[1] = mob;
        BlockForm block = new BlockForm(); block.blockState.set(Blocks.GRASS.getDefaultState()); forms[2] = block;
        StructureForm form = new StructureForm(); forms[3] = form; viewport.form = form;
        viewport.grid = true; viewport.setDistance(18F); viewport.setPosition(0F, 3F, 0F); viewport.setRotation(25F, 25F);
        this.install("igloo/top"); this.install("endcity/base_floor"); this.select("igloo/top");
    }

    private void install(String name) throws Exception
    {
        Path directory = Minecraft.getMinecraft().getIntegratedServer().getWorld(0).getSaveHandler().getWorldDirectory().toPath().resolve("structures");
        Path file = directory.resolve("aihelper_ui_" + name.replace('/', '_') + ".nbt");
        if (Files.exists(file)) throw new IllegalStateException("Fixture already exists: " + file);
        Files.createDirectories(directory);
        String resource = name.equals("igloo/top") ? "igloo/igloo_top" : name;
        try (InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation("minecraft", "structures/" + resource + ".nbt")).getInputStream())
        { Files.copy(stream, file); }
        files.add(file); StructureManager.invalidate();
    }
    private void select(String name)
    {
        structure = name; ((StructureForm) forms[3]).structure.set("minecraft:aihelper_ui_" + name.replace('/', '_'));
    }
    @Override public boolean canPause() { return false; }
    @Override public void onClose(UIBaseMenu next)
    {
        mchorse.bbs_mod.BBSSettings.translucencyQueue.set(oldQueue);
        mchorse.bbs_mod.BBSSettings.freezeModels.set(oldFreeze);
        for (Path file : files) try { Files.deleteIfExists(file); } catch (Exception e) { throw new IllegalStateException(e); }
        StructureManager.invalidate();
    }
    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        JsonObject result = new JsonObject(); result.addProperty("ok", true); JsonArray samples = new JsonArray();
        render.batcher.box(0, 0, width, height, 0xff101010);
        render.batcher.text("Actual UI rendering / " + structure + (reference ? " / forced reference depth" : " / native boundary"), 8, 6, -1);
        render.batcher.flush();
        int cell = width / 5, top = 28, h = height / 3;
        RenderHelper.enableGUIStandardItemLighting();
        result.add("lights", lights());
        for (int i = 0; i < forms.length; i++)
        {
            JsonObject sample = new JsonObject(); sample.addProperty("kind", forms[i].getClass().getSimpleName());
            sample.add("before", state());
            try
            {
                FormUtilsClient.getRenderer(forms[i]).renderPreview(context, i * cell + 8, top, (i + 1) * cell - 8, top + h);
                sample.add("pixels", pixels(i * cell + 8, top, cell - 16, h));
                if(forms[i] instanceof BillboardForm||forms[i] instanceof ExtrudedForm)
                    sample.add("textureUniforms",textureUniforms());
                if (forms[i] instanceof ModelForm)
                {
                    sample.addProperty("modelExists", ((ModelFormRenderer) FormUtilsClient.getRenderer(forms[i])).getModel() != null);
                    JsonArray lights = new JsonArray(); int program = mchorse.bbs_mod.client.BBSShaders.getModel().getId();
                    for (int n = 0; n < 2; n++)
                    {
                        FloatBuffer b = BufferUtils.createFloatBuffer(4); int location = GL20.glGetUniformLocation(program, "Light" + n + "_Direction");
                        if (location >= 0) GL20.glGetUniform(program, location, b);
                        JsonArray vector = new JsonArray(); for (int k = 0; k < 3; k++) vector.add(b.get(k)); lights.add(vector);
                    }
                    sample.add("shaderLights", lights);
                }
            }
            catch (Throwable e) { sample.addProperty("error", e.toString()); }
            sample.add("after", state()); samples.add(sample);
        }
        RenderHelper.disableStandardItemLighting();
        render.batcher.text("Block item after text", 4 * cell + 4, top, -1);
        if (reference) { GlStateManager.depthFunc(GL11.GL_LEQUAL); GlStateManager.depthMask(true); }
        result.add("itemBefore", state());
        context.batcher.getContext().getMatrices().push();
        context.batcher.getContext().getMatrices().translate(4 * cell + 10, top + 24, 0);
        context.batcher.getContext().getMatrices().scale(4, 4, 4);
        try { context.batcher.getContext().drawItem(new ItemStack(Blocks.GRASS), 0, 0); }
        finally { context.batcher.getContext().getMatrices().pop(); }
        result.add("itemPixels", pixels(4 * cell + 10, top + 24, 64, 64)); result.add("itemAfter", state());
        render.batcher.flush();
        if(materials)
        {
            JsonArray inventory=new JsonArray();
            net.minecraft.util.NonNullList<ItemStack> entries=net.minecraft.util.NonNullList.create();
            net.minecraft.item.Item.getItemFromBlock(mchorse.bbs_mod.forge.CommonProxy.MODEL_BLOCK)
                .getSubItems(mchorse.bbs_mod.forge.CommonProxy.BBS_TAB,entries);
            entries.add(new ItemStack(mchorse.bbs_mod.forge.CommonProxy.GUN_ITEM));
            entries.add(mchorse.bbs_mod.forge.CommonProxy.BBS_TAB.createIcon());
            /* A white front-lit quad is an analytical reference for the original gui_light:front. */
            mchorse.bbs_mod.forge.ModelTileEntity tile=new mchorse.bbs_mod.forge.ModelTileEntity();
            BillboardForm white=new BillboardForm();white.texture.set(new Link(Link.COLOR,"ffffffff"));
            tile.getProperties().setForm(white);
            ItemStack referenceItem=new ItemStack(mchorse.bbs_mod.forge.CommonProxy.MODEL_BLOCK);
            net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();
            tag.setTag("BlockEntityTag",tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));referenceItem.setTagCompound(tag);entries.add(referenceItem);
            int y=top+h+32,index=0;
            for(ItemStack entry:entries)
            {
                int x=16+index*82;
                context.batcher.getContext().getMatrices().push();
                context.batcher.getContext().getMatrices().translate(x,y,0);context.batcher.getContext().getMatrices().scale(4,4,4);
                try{context.batcher.getContext().drawItem(entry,0,0);}finally{context.batcher.getContext().getMatrices().pop();}
                JsonObject item=new JsonObject();item.addProperty("item",entry.getItem().getRegistryName().toString());
                item.add("pixels",pixels(x,y,64,64));item.add("textureUniforms",textureUniforms());inventory.add(item);index++;
            }
            result.add("inventory",inventory);
        }
        else
        {
        viewport.area.set(8, top + h + 12, width - 16, height - top - h - 20);
        result.add("viewportBefore", state());
        try { viewport.render(context); } catch (Throwable e) { result.addProperty("viewportError", e.toString()); }
        result.add("viewportAfter", state());
        result.add("viewportPixels", pixels(viewport.area.x, viewport.area.y, viewport.area.w, viewport.area.h));
        result.addProperty("queuedDraws", queuedDraws);
        if (viewport.form instanceof MobForm)
        {
            try
            {
                java.lang.reflect.Field shader = NativeFormDraw.class.getDeclaredField("program"); shader.setAccessible(true);
                int program = shader.getInt(null); JsonObject uniforms = new JsonObject();
                for (String uniform : new String[]{"Diffuse", "Light0", "Light1", "NormalTransform"})
                {
                    int location = GL20.glGetUniformLocation(program, uniform); FloatBuffer b = BufferUtils.createFloatBuffer(16);
                    if (location >= 0) GL20.glGetUniform(program, location, b);
                    int count = uniform.equals("Diffuse") ? 1 : uniform.equals("NormalTransform") ? 9 : 3;
                    JsonArray values = new JsonArray(); for (int n = 0; n < count; n++) values.add(b.get(n)); uniforms.add(uniform, values);
                }
                result.add("nativeUniforms", uniforms);
            }
            catch (Exception e) { result.addProperty("uniformError", e.toString()); }
        }
        }
        render.batcher.box(width - 28, height - 28, width - 8, height - 8, 0xffff00ff); render.batcher.flush();
        result.add("sentinel", pixels(width - 28, height - 28, 20, 20));
        result.add("samples", samples); result.addProperty("glError", GL11.glGetError()); result.addProperty("frames", ++frames);
        result.addProperty("structureVertices", ((StructureFormRenderer) FormUtilsClient.getRenderer(forms[3])).getBakedVertexCount());
        frame = result;
    }
    private static JsonArray lights()
    {
        JsonArray a = new JsonArray();
        for (int n = 0; n < 2; n++) { FloatBuffer b = BufferUtils.createFloatBuffer(4); GL11.glGetLight(GL11.GL_LIGHT0 + n, GL11.GL_POSITION, b); JsonArray v = new JsonArray(); for (int i = 0; i < 3; i++) v.add(b.get(i)); a.add(v); }
        return a;
    }
    private static JsonObject textureUniforms()
    {
        try
        {
        java.lang.reflect.Field shader=mchorse.bbs_mod.forge.studio.NativeTextureRenderer.class.getDeclaredField("program");shader.setAccessible(true);
        int program=shader.getInt(null);JsonObject result=new JsonObject();
        for(String name:new String[]{"NormalMatrix","Light0","Light1"})
        {
            FloatBuffer b=BufferUtils.createFloatBuffer(16);int location=GL20.glGetUniformLocation(program,name);
            if(location>=0)GL20.glGetUniform(program,location,b);
            JsonArray values=new JsonArray();for(int i=0;i<(name.equals("NormalMatrix")?9:3);i++)values.add(b.get(i));result.add(name,values);
        }
        return result;
        }
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static JsonObject state()
    {
        JsonObject out = new JsonObject();
        out.addProperty("depth", GL11.glIsEnabled(GL11.GL_DEPTH_TEST)); out.addProperty("depthMask", GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)); out.addProperty("depthFunc", GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        out.addProperty("cull", GL11.glIsEnabled(GL11.GL_CULL_FACE));
        out.addProperty("matrixMode", GL11.glGetInteger(GL11.GL_MATRIX_MODE)); out.addProperty("projectionDepth", GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH)); out.addProperty("modelviewDepth", GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH));
        out.addProperty("maxProjectionDepth", GL11.glGetInteger(GL11.GL_MAX_PROJECTION_STACK_DEPTH));
        out.addProperty("program", GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)); out.addProperty("fbo", GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)); out.addProperty("activeTexture", GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        for (int p : new int[]{GL11.GL_PROJECTION_MATRIX, GL11.GL_MODELVIEW_MATRIX}) { FloatBuffer b = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(p, b); JsonArray a = new JsonArray(); for (int i = 0; i < 16; i++) a.add(b.get(i)); out.add(p == GL11.GL_PROJECTION_MATRIX ? "projection" : "modelview", a); }
        return out;
    }
    private static JsonObject pixels(int x, int y, int w, int h)
    {
        float s = BBSModClient.getGUIScale(); int pw = Math.max(1, (int) (w * s)), ph = Math.max(1, (int) (h * s));
        ByteBuffer b = BufferUtils.createByteBuffer(pw * ph * 4); GL11.glReadPixels(Math.round(x * s), Minecraft.getMinecraft().displayHeight - Math.round((y + h) * s), pw, ph, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, b);
        int visible = 0, max = 0; long sum = 0, hash = 0;
        for (int i = 0; i < b.limit(); i += 4) { int r = b.get(i) & 255, g = b.get(i+1) & 255, blue = b.get(i+2) & 255; if (r != 16 || g != 16 || blue != 16) { visible++; sum += r + g + blue; max = Math.max(max, Math.max(r, Math.max(g, blue))); } hash = 31 * hash + (r << 16 | g << 8 | blue); }
        JsonObject out = new JsonObject(); out.addProperty("visible", visible); out.addProperty("max", max); out.addProperty("mean", visible == 0 ? 0 : sum / (visible * 3D)); out.addProperty("hash", hash); return out;
    }
    public static JsonObject handle(JsonObject request) throws Exception
    {
        if (request.has("editor"))
        {
            StructureEditor menu = new StructureEditor(); UIScreen.open(menu);
            StructureForm form = new StructureForm(); form.structure.set(request.get("editor").getAsString());
            menu.palette.editor.edit(form); menu.palette.list.setVisible(false); menu.palette.editor.setVisible(true);
            menu.palette.editor.renderer.setDistance(8); menu.palette.editor.renderer.setPosition(0, 1, 0);
            return menu.frame;
        }
        if (UIScreen.getCurrentMenu() instanceof StructureEditor)
        {
            StructureEditor editor = (StructureEditor) UIScreen.getCurrentMenu();
            if (request.has("structure")) ((StructureForm) editor.palette.editor.renderer.form).structure.set(request.get("structure").getAsString());
            if (request.has("picker"))
            {
                Object panel = editor.palette.editor.getChildren(mchorse.bbs_mod.ui.forms.editors.panels.UIStructureFormPanel.class).get(0);
                java.lang.reflect.Method method = panel.getClass().getDeclaredMethod("openStructurePicker"); method.setAccessible(true); method.invoke(panel);
            }
            return editor.frame;
        }
        if (request.has("open") && request.get("open").getAsBoolean()) UIScreen.open(new NativeUiRenderProbe());
        if (!(UIScreen.getCurrentMenu() instanceof NativeUiRenderProbe)) throw new IllegalStateException("Open native UI rendering probe first");
        NativeUiRenderProbe probe = (NativeUiRenderProbe) UIScreen.getCurrentMenu();
        if(request.has("materials"))
        {
            probe.materials=true;
            mchorse.bbs_mod.BBSSettings.freezeModels.set(true);
            BillboardForm billboard=new BillboardForm();billboard.texture.set(new Link(Link.COLOR,"ffffffff"));probe.forms[0]=billboard;
            ExtrudedForm extruded=new ExtrudedForm();extruded.texture.set(new Link(Link.COLOR,"ffffffff"));probe.forms[1]=extruded;
            MobForm cow=new MobForm();cow.mobID.set("minecraft:cow");probe.forms[2]=cow;
        }
        if (request.has("structure")) probe.select(request.get("structure").getAsString());
        if (request.has("reference")) probe.reference = request.get("reference").getAsBoolean();
        if (request.has("model")) ((ModelForm) probe.forms[0]).model.set(request.get("model").getAsString());
        if (request.has("viewportMob"))
        {
            MobForm mob = (MobForm) probe.forms[1]; mob.mobID.set(request.get("viewportMob").getAsString());
            mob.mobNBT.set("{Size:1}"); probe.viewport.form = mob; probe.viewport.setPosition(0F, .5F, 0F); probe.viewport.setDistance(3F);
        }
        if (request.has("queue")) mchorse.bbs_mod.BBSSettings.translucencyQueue.set(request.get("queue").getAsBoolean());
        probe.frame.addProperty("ok", true);
        return probe.frame;
    }
    private static final class StructureEditor extends UIBaseMenu
    {
        final mchorse.bbs_mod.ui.forms.UIFormPalette palette = new mchorse.bbs_mod.ui.forms.UIFormPalette(null);
        JsonObject frame = new JsonObject();
        StructureEditor() { palette.full(main); main.add(palette); }
        @Override public boolean canPause() { return false; }
        @Override public void renderMenu(UIRenderingContext render, int x, int y)
        {
            JsonObject result = new JsonObject(); result.addProperty("ok", true); result.add("before", state());
            super.renderMenu(render, x, y); render.batcher.flush(); result.add("after", state());
            result.add("screenPixels", pixels(0, 0, width, height)); result.addProperty("glError", GL11.glGetError());
            result.addProperty("structure", ((StructureForm) palette.editor.renderer.form).structure.get());
            frame = result;
        }
    }
}
