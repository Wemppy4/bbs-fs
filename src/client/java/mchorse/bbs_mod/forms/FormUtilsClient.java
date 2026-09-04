package mchorse.bbs_mod.forms;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import org.slf4j.Logger;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.forms.renderers.AnchorFormRenderer;
import mchorse.bbs_mod.forms.renderers.BillboardFormRenderer;
import mchorse.bbs_mod.forms.renderers.BlockFormRenderer;
import mchorse.bbs_mod.forms.renderers.ExtrudedFormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.api.client.events.FormRenderEvents;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FramebufferFormRenderer;
import mchorse.bbs_mod.forms.renderers.ItemFormRenderer;
import mchorse.bbs_mod.forms.renderers.LabelFormRenderer;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.ParticleFormRenderer;
import mchorse.bbs_mod.forms.renderers.StructureFormRenderer;
import mchorse.bbs_mod.forms.renderers.TrailFormRenderer;
import mchorse.bbs_mod.forms.renderers.VanillaParticleFormRenderer;
import mchorse.bbs_mod.forms.renderers.VideoFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.model.ModelBaker;
import net.minecraft.client.util.BufferAllocator;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.Stack;

public class FormUtilsClient
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Render failures already reported, keyed by form class, exception class and throw
     * site. Rendering runs every frame, so an unguarded log buries the game in one
     * repeating stack trace — which is why this used to be a silent catch, and why
     * thousands of lines of IK, physics and material code could fail invisibly.
     */
    private static final Set<String> reportedRenderFailures = new HashSet<>();

    private static Map<Class, IFormRendererFactory> map = new HashMap<>();
    private static CustomVertexConsumerProvider customVertexConsumerProvider;
    private static Stack<Form> currentForm = new Stack<>();

    private static CustomVertexConsumerProvider createProvider()
    {
        SequencedMap<RenderLayer, BufferAllocator> layers = new Object2ObjectLinkedOpenHashMap<>();

        assignAllocator(layers, TexturedRenderLayers.getEntitySolid());
        assignAllocator(layers, TexturedRenderLayers.getEntityCutout());
        assignAllocator(layers, TexturedRenderLayers.getBannerPatterns());
        /* TODO(1.21.11 render): the terrain layers are no longer RenderLayer factories —
         * RenderLayer.getSolid/getCutout/getCutoutMipped are gone and chunk terrain draws through
         * the BlockRenderLayer enum's RenderPipelines instead. Forms that render real blocks (the
         * structure form) therefore fall through to the shared fallback buffer here, which flushes
         * on every layer switch and can lose depth between opaque layers. Pre-assignment was an
         * optimisation, so this only costs the pre-sizing until the terrain path is ported. */
        assignAllocator(layers, TexturedRenderLayers.getItemTranslucentCull());
        assignAllocator(layers, TexturedRenderLayers.getBlockTranslucentCull());
        assignAllocator(layers, TexturedRenderLayers.getShieldPatterns());
        assignAllocator(layers, TexturedRenderLayers.getBeds());
        assignAllocator(layers, TexturedRenderLayers.getShulkerBoxes());
        assignAllocator(layers, TexturedRenderLayers.getSign());
        assignAllocator(layers, TexturedRenderLayers.getHangingSign());
        assignAllocator(layers, TexturedRenderLayers.getChest());
        /* TODO(1.21.11 render): the glint layers (armor/item/entity/direct) and the water mask are no
         * longer RenderLayer factories — 1.21.5+ draws glint as a post-process. Pre-assigning an
         * allocator for them was only an optimisation, so dropping them costs nothing but the pre-sizing. */

        for (RenderLayer layer : ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS)
        {
            assignAllocator(layers, layer);
        }

        return new CustomVertexConsumerProvider(new BufferAllocator(1536), layers);
    }

    /**
     * Fills the registry. Called by BBS while it initialises, and followed by the event that
     * lets addons add to it.
     *
     * <p>This used to be a static initialiser, which ran whenever something first touched the
     * class — a moment nobody chose and an addon could not aim at.</p>
     */
    public static void setup()
    {
        register(BillboardForm.class, BillboardFormRenderer::new);
        register(VideoForm.class, VideoFormRenderer::new);
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
        register(StructureForm.class, StructureFormRenderer::new);
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
        /* Built on first use rather than while the mod initialises: it allocates the render
         * layers' buffers, and doing that before the game is ready is the kind of thing that
         * goes wrong in the game instead of in the build. */
        if (customVertexConsumerProvider == null)
        {
            customVertexConsumerProvider = createProvider();
        }

        return customVertexConsumerProvider;
    }

    /**
     * Give a layer its own buffer, and its colour-overlay twin one right behind it: a form drawing
     * with an overlay uses the twin instead (see {@link mchorse.bbs_mod.forms.renderers.utils.FormOverlay}),
     * and a layer that is not in this map falls into the shared buffer, which {@code Immediate.draw()}
     * flushes FIRST — which would put a structure's translucent blocks under its opaque ones.
     */
    private static void assignAllocator(SequencedMap<RenderLayer, BufferAllocator> layers, RenderLayer layer)
    {
        assign(layers, layer);
        assign(layers, mchorse.bbs_mod.forms.renderers.utils.FormOverlay.withOverlay(layer));
    }

    private static void assign(SequencedMap<RenderLayer, BufferAllocator> layers, RenderLayer layer)
    {
        layers.put(layer, new BufferAllocator(layer.getExpectedBufferSize()));
    }

    public static <T extends Form> void register(Class<T> clazz, IFormRendererFactory<T> function)
    {
        map.put(clazz, function);
    }

    /**
     * The renderer registered for a form's own class, or for the nearest class it extends.
     *
     * <p>Without the walk up, extending one of BBS's forms bought nothing: the subclass
     * inherited the shape and the data and then drew as nothing at all, until it registered a
     * renderer that was usually a copy of its parent's.</p>
     */
    private static IFormRendererFactory findFactory(Class clazz)
    {
        while (clazz != null && clazz != Object.class)
        {
            IFormRendererFactory factory = map.get(clazz);

            if (factory != null)
            {
                return factory;
            }

            clazz = clazz.getSuperclass();
        }

        return null;
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

        IFormRendererFactory factory = findFactory(form.getClass());

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
        BBSProfiler.count(BBSProfiler.Section.UI_PREVIEW_RENDERS);
        BBSProfiler.begin(BBSProfiler.Timer.UI_PREVIEWS);

        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            renderer.renderUI(context, x1, y1, x2, y2);
        }

        BBSProfiler.end(BBSProfiler.Timer.UI_PREVIEWS);
    }

    /** The form's picture alone; see {@link FormRenderer#renderPreview}. */
    public static void renderPreview(Form form, UIContext context, int x1, int y1, int x2, int y2)
    {
        BBSProfiler.count(BBSProfiler.Section.UI_PREVIEW_RENDERS);
        BBSProfiler.begin(BBSProfiler.Timer.UI_PREVIEWS);

        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            renderer.renderPreview(context, x1, y1, x2, y2);
        }

        BBSProfiler.end(BBSProfiler.Timer.UI_PREVIEWS);
    }

    public static void render(Form form, FormRenderingContext context)
    {
        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            currentForm.push(form);

            FormRenderEvents.BEFORE.invoker().onFormRender(form, context);

            try
            {
                renderer.render(context);
            }
            catch (Exception e)
            {
                reportRenderFailure(form, e);
            }

            /* After the catch, so a listener that pushed something in BEFORE still gets to
             * pop it when the form's own renderer threw. */
            FormRenderEvents.AFTER.invoker().onFormRender(form, context);

            currentForm.pop();
        }
    }

    /**
     * Reports the first occurrence of each distinct render failure and drops the repeats.
     * The frame is still let through: a form that throws must not take the rest of the
     * scene with it, which is what the swallow was for.
     */
    private static void reportRenderFailure(Form form, Exception e)
    {
        StackTraceElement[] trace = e.getStackTrace();
        String key = form.getClass().getName() + "|" + e.getClass().getName() + "|" + (trace.length == 0 ? "" : trace[0].toString());

        if (reportedRenderFailures.add(key))
        {
            LOGGER.error("[BBS form] {} failed to render - further repeats of this failure are silenced.", form.getClass().getSimpleName(), e);
        }
    }

    public static IBoneHierarchy getBoneHierarchy(Form form)
    {
        FormRenderer renderer = getRenderer(form);

        return renderer == null ? null : renderer.getBoneHierarchy();
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