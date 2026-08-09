package mchorse.bbs_mod.client.renderer.item;

import com.mojang.serialization.MapCodec;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.forms.FormRenderCapture;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Model-block item rendering on the 1.21.5+ item-model system: renders the block's BBS
 * {@link Form} (as the 1.21.1 {@code ModelBlockItemRenderer} did) by capturing the immediate
 * form pipeline and replaying it into the item command queue — see {@link FormRenderCapture}.
 */
public class ModelBlockSpecialRenderer implements SpecialModelRenderer<ModelBlockSpecialRenderer.Key>
{
    private static int generation;

    /**
     * The per-stack render data plus the GUI cache key. Vanilla stores whatever {@link #getData}
     * returns in the item render state's model key, and the 1.21.6+ GUI re-renders a cached item
     * atlas entry only when that key changes — the record's equality IS the invalidation rule.
     * Outside the editor the generation is 0 and the key is stable (atlas caching works as for any
     * item); while the transform editor is open on this item's properties every lookup mints a
     * fresh generation, so the hotbar re-renders each frame and edits show live.
     */
    public record Key(ModelBlockItemRenderer.Item item, int generation)
    {}

    @Override
    public Key getData(ItemStack stack)
    {
        ModelBlockItemRenderer.Item item = BBSModClient.getModelBlockItemRenderer().get(stack);

        if (item == null)
        {
            return null;
        }

        if (UIModelBlockEditorMenu.isEditing(item.entity.getProperties()))
        {
            /* Keep the entry alive while it is being edited: with the GUI atlas re-rendering every
             * frame the render() call refreshes expiration anyway, but an item that is briefly not
             * drawn (menu covering the hotbar slot, etc.) must not expire mid-edit — the menu holds
             * a reference to THESE properties, and a respawned entry would re-read the stale NBT. */
            item.expiration = 20;

            return new Key(item, ++generation);
        }

        return new Key(item, 0);
    }

    @Override
    public void render(Key key, ItemDisplayContext displayContext, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int outlineColor)
    {
        if (key == null)
        {
            return;
        }

        ModelBlockItemRenderer.Item item = key.item();
        ModelProperties properties = item.entity.getProperties();
        Form form = properties.getForm(displayContext);

        if (form != null)
        {
            item.expiration = 20;

            FormRenderCapture.submitForm(form, properties.getTransform(displayContext), item.formEntity, displayContext, matrices, queue, light, overlay);
        }
    }

    @Override
    public void collectVertices(Consumer<Vector3fc> consumer)
    {
        FormRenderCapture.collectItemBounds(consumer);
    }

    public static class Unbaked implements SpecialModelRenderer.Unbaked
    {
        public static final MapCodec<ModelBlockSpecialRenderer.Unbaked> CODEC = MapCodec.unit(new ModelBlockSpecialRenderer.Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakeContext context)
        {
            return new ModelBlockSpecialRenderer();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> getCodec()
        {
            return CODEC;
        }
    }
}
