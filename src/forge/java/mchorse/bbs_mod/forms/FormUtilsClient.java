package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.client.events.FormRenderEvents;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.forge.studio.NativeTextureRenderer;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.ui.framework.UIContext;
import java.util.*;

/** Original renderer registry, current-form scope and failure reporting, on Forge. */
public final class FormUtilsClient
{
    private static final Map<Class<?>, IFormRendererFactory<?>> map = new HashMap<>();
    private static final Set<String> reportedRenderFailures = new HashSet<>();
    private static final Deque<Form> currentForm = new ArrayDeque<>();

    /** Register only types whose native geometry is implemented. Further types register when ported. */
    public static void setup()
    {
        register(ModelForm.class, ModelFormRenderer::new);
        register(AnchorForm.class, AnchorFormRenderer::new);
        register(MobForm.class, NativeFormRendererAdapter::new);
        register(BillboardForm.class, NativeFormRendererAdapter::new);
        register(ExtrudedForm.class, NativeFormRendererAdapter::new);
    }

    public static <T extends Form> void register(Class<T> type, IFormRendererFactory<T> factory) { map.put(type, factory); }
    private static IFormRendererFactory<?> findFactory(Class<?> type)
    {
        while (type != null && type != Object.class)
        {
            IFormRendererFactory<?> factory = map.get(type);
            if (factory != null) return factory;
            type = type.getSuperclass();
        }
        return null;
    }

    public static Form getCurrentForm() { return currentForm.peek(); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static FormRenderer<?> getRenderer(Form form)
    {
        if (form == null) return null;
        if (form.getRenderer() instanceof FormRenderer) return (FormRenderer<?>) form.getRenderer();
        IFormRendererFactory factory = findFactory(form.getClass());
        if (factory == null) return null;
        FormRenderer renderer = factory.create(form);
        form.setRenderer(renderer);
        return renderer;
    }

    public static void renderUI(Form form, UIContext context, int x1, int y1, int x2, int y2)
    {
        FormRenderer<?> renderer = getRenderer(form);
        if (renderer != null) renderer.renderUI(context, x1, y1, x2, y2);
    }
    public static void renderPreview(Form form, UIContext context, int x1, int y1, int x2, int y2)
    {
        FormRenderer<?> renderer = getRenderer(form);
        if (renderer != null) renderer.renderPreview(context, x1, y1, x2, y2);
    }

    public static void render(Form form, FormRenderingContext context)
    {
        if (FormRenderLast.postpone(form, context)) return;
        FormRenderer<?> renderer = getRenderer(form);
        if (renderer == null) return;
        NativeTextureRenderer.beginPass();
        currentForm.push(form);
        try
        {
            FormRenderEvents.BEFORE.invoker().onFormRender(form, context);
            try { renderer.render(context); }
            catch (Exception e) { reportRenderFailure(form, e); }
            finally { FormRenderEvents.AFTER.invoker().onFormRender(form, context); }
        }
        finally
        {
            currentForm.pop();
            NativeTextureRenderer.endPass();
        }
    }

    private static void reportRenderFailure(Form form, Exception error)
    {
        StackTraceElement[] trace = error.getStackTrace();
        String key = form.getClass().getName() + "|" + error.getClass().getName() + "|" + (trace.length == 0 ? "" : trace[0]);
        if (reportedRenderFailures.add(key)) BBSMod.LOGGER.error("[BBS form] {} failed to render - further repeats are silenced.", form.getClass().getSimpleName(), error);
    }

    public static IBoneHierarchy getBoneHierarchy(Form form)
    {
        FormRenderer<?> renderer = getRenderer(form);
        return renderer == null ? null : renderer.getBoneHierarchy();
    }
    public static List<String> getBones(Form form)
    {
        FormRenderer<?> renderer = getRenderer(form);
        return renderer == null ? Collections.emptyList() : renderer.getBones();
    }
    public interface IFormRendererFactory<T extends Form> { FormRenderer<T> create(T form); }
    private FormUtilsClient() {}
}
