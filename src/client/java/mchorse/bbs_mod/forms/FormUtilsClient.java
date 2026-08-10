package mchorse.bbs_mod.forms;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import org.slf4j.Logger;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.renderers.AnchorFormRenderer;
import mchorse.bbs_mod.forms.renderers.BillboardFormRenderer;
import mchorse.bbs_mod.forms.renderers.BlockFormRenderer;
import mchorse.bbs_mod.forms.renderers.ExtrudedFormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FramebufferFormRenderer;
import mchorse.bbs_mod.forms.renderers.ItemFormRenderer;
import mchorse.bbs_mod.forms.renderers.LabelFormRenderer;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.ParticleFormRenderer;
import mchorse.bbs_mod.forms.renderers.TrailFormRenderer;
import mchorse.bbs_mod.forms.renderers.VanillaParticleFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.model.ModelBaker;
import net.minecraft.client.util.BufferAllocator;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Stack;

public class FormUtilsClient
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<Class, IFormRendererFactory> map = new HashMap<>();
    private static CustomVertexConsumerProvider customVertexConsumerProvider;
    private static Stack<Form> currentForm = new Stack<>();
    private static long lastRenderErrorLog;

    static
    {
        register(BillboardForm.class, BillboardFormRenderer::new);
        register(ExtrudedForm.class, ExtrudedFormRenderer::new);
        register(LabelForm.class, LabelFormRenderer::new);
        register(ModelForm.class, ModelFormRenderer::new);
        register(ParticleForm.class, ParticleFormRenderer::new);
        register(BlockForm.class, BlockFormRenderer::new);
        register(ItemForm.class, ItemFormRenderer::new);
        register(AnchorForm.class, AnchorFormRenderer::new);
        register(MobForm.class, MobFormRenderer::new);
        register(VanillaParticleForm.class, VanillaParticleFormRenderer::new);
        register(TrailForm.class, TrailFormRenderer::new);
        register(FramebufferForm.class, FramebufferFormRenderer::new);
    }

    /**
     * Forms must render into buffers of their own rather than into Minecraft's shared entity
     * consumers. Form renderers flush the provider themselves and install a {@link
     * CustomVertexConsumerProvider#hijackVertexFormat(java.util.function.Consumer)} hook that
     * overrides GL state (custom texture, picker shader, blending) per drawn layer. On the shared
     * provider that hook also fires for whatever the world or the GUI had buffered but not yet
     * drawn, so the state lands on somebody else's geometry — e.g. a mob form's custom texture
     * ends up on an unrelated layer instead of the mob, since it only applies to the first drawn
     * layer. Own buffers guarantee that everything drawn while the hook is installed belongs to
     * the form being rendered.
     */
    public static CustomVertexConsumerProvider getProvider()
    {
        if (customVertexConsumerProvider == null)
        {
            SequencedMap<RenderLayer, BufferAllocator> layers = new Object2ObjectLinkedOpenHashMap<>();

            assignAllocator(layers, TexturedRenderLayers.getEntitySolid());
            assignAllocator(layers, TexturedRenderLayers.getEntityCutout());
            assignAllocator(layers, TexturedRenderLayers.getBannerPatterns());
            assignAllocator(layers, TexturedRenderLayers.getItemTranslucentCull());
            assignAllocator(layers, TexturedRenderLayers.getBlockTranslucentCull());
            assignAllocator(layers, TexturedRenderLayers.getShieldPatterns());
            assignAllocator(layers, TexturedRenderLayers.getBeds());
            assignAllocator(layers, TexturedRenderLayers.getShulkerBoxes());
            assignAllocator(layers, TexturedRenderLayers.getSign());
            assignAllocator(layers, TexturedRenderLayers.getHangingSign());
            assignAllocator(layers, TexturedRenderLayers.getChest());
            /* TODO(1.21.11 render): the glint layers (armor/item/entity/direct) and the water mask are no
             * longer RenderLayer factories — 1.21.5+ draws glint as a post-process. Pre-assigning an allocator
             * for them was only an optimisation, so dropping them costs nothing but the pre-sizing. */

            for (RenderLayer layer : ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS)
            {
                assignAllocator(layers, layer);
            }

            customVertexConsumerProvider = new CustomVertexConsumerProvider(new BufferAllocator(1536), layers);
        }

        return customVertexConsumerProvider;
    }

    private static void assignAllocator(SequencedMap<RenderLayer, BufferAllocator> layers, RenderLayer layer)
    {
        layers.put(layer, new BufferAllocator(layer.getExpectedBufferSize()));
    }

    public static <T extends Form> void register(Class<T> clazz, IFormRendererFactory<T> function)
    {
        map.put(clazz, function);
    }

    public static Form getCurrentForm()
    {
        return currentForm.isEmpty() ? null : currentForm.peek();
    }

    public static FormRenderer getRenderer(Form form)
    {
        if (form == null)
        {
            return null;
        }

        if (form.getRenderer() instanceof FormRenderer renderer)
        {
            return renderer;
        }

        IFormRendererFactory factory = map.get(form.getClass());

        if (factory != null)
        {
            FormRenderer formRenderer = factory.create(form);

            form.setRenderer(formRenderer);

            return formRenderer;
        }

        return null;
    }

    public static void renderUI(Form form, UIContext context, int x1, int y1, int x2, int y2)
    {
        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            renderer.renderUI(context, x1, y1, x2, y2);
        }
    }

    public static void render(Form form, FormRenderingContext context)
    {
        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            currentForm.push(form);

            try
            {
                renderer.render(context);
            }
            catch (Exception e)
            {
                /* Containment stays (one broken form must not kill the frame), but silence would
                 * turn every render failure into an invisible form with a clean log — rate-limit
                 * instead of muting. */
                long now = System.currentTimeMillis();

                if (now - lastRenderErrorLog > 1000L)
                {
                    lastRenderErrorLog = now;

                    LOGGER.error("Form '{}' failed to render", form.getFormId(), e);
                }
            }

            currentForm.pop();
        }
    }

    public static List<String> getBones(Form form)
    {
        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            return renderer.getBones();
        }

        return Collections.emptyList();
    }

    public static interface IFormRendererFactory <T extends Form>
    {
        public FormRenderer<T> create(T form);
    }
}