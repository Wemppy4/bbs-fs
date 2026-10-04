package mchorse.bbs_mod;

import mchorse.bbs_mod.api.client.events.FormRenderEvents;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ForgeFormContextTest
{
    @Test public void reusedPreviewContextDoesNotKeepViewportClockInWorld()
    {
        MatrixStack previewStack = new MatrixStack();
        StencilMap picking = new StencilMap();
        FormRenderingContext context = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, new StubEntity(), previewStack, 240, 0, .25F)
            .inUI().modelRenderer(1234L).stencilMap(picking).color(0xff123456);
        context.world.push();
        context.world.translate(9, 8, 7);

        MatrixStack worldStack = new MatrixStack();
        StubEntity worldEntity = new StubEntity();
        context.set(FormRenderType.ENTITY, worldEntity, worldStack, 64, 1, .75F);

        assertFalse(context.modelRenderer);
        assertEquals(0L, context.modelRendererTick);
        assertFalse(context.ui);
        assertFalse(context.isPicking());
        assertEquals(0xffffffff, context.color);
        assertSame(worldStack, context.stack);
        assertSame(worldEntity, context.entity);
        assertEquals(.75F, context.getTransition(), 0F);
        assertTrue(context.world.isEmpty());
        assertEquals(0F, context.world.peek().getPositionMatrix().m30(), 0F);

        context.set(FormRenderType.PREVIEW, null, previewStack, 240, 0, .5F).modelRenderer(5678L);
        assertTrue(context.modelRenderer);
        assertEquals(5678L, context.modelRendererTick);
    }

    @Test public void sharedContextTraversesActualParentsAndRestoresLightMatricesAndCurrentForm()
    {
        KeyframeFactories.setup();
        BillboardForm parent = new BillboardForm(), child = new BillboardForm();
        parent.lighting.set(.5F);
        parent.transform.get().translate.set(2, 3, 4);
        BodyPart part = new BodyPart("part");
        part.transform.get().translate.set(1, 0, 0);
        part.setForm(child); parent.parts.addBodyPart(part);
        List<Form> visited = new ArrayList<>();
        List<Integer> lights = new ArrayList<>();
        List<Float> x = new ArrayList<>();
        FormUtilsClient.register(BillboardForm.class, form -> new FormRenderer<BillboardForm>(form)
        {
            @Override protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2) {}
            @Override protected void render3D(FormRenderingContext context)
            {
                assertSame(form, FormUtilsClient.getCurrentForm());
                visited.add(form); lights.add(context.light); x.add(context.stack.peek().getPositionMatrix().m30());
            }
        });
        MatrixStack stack = new MatrixStack();
        FormRenderingContext context = new FormRenderingContext().set(FormRenderType.ENTITY, new StubEntity(), stack, 64 | 128 << 16, 0, 0);
        try
        {
            FormUtilsClient.render(parent, context);
            assertEquals(Arrays.asList(parent, child), visited);
            assertEquals(Arrays.asList(152 | 128 << 16, 152 | 128 << 16), lights);
            assertEquals(Arrays.asList(2F, 3F), x);
            assertEquals(64 | 128 << 16, context.light);
            assertTrue(stack.isEmpty()); assertTrue(context.world.isEmpty());
            assertEquals(0, stack.peek().getPositionMatrix().m30(), 0);
            assertNull(FormUtilsClient.getCurrentForm());
        }
        finally { FormUtilsClient.setup(); }
    }

    @Test public void pickingReservesGizmoIdentitiesAndHonoursPickable()
    {
        KeyframeFactories.setup();
        StencilMap map = new StencilMap(); map.setup();
        assertEquals(20, map.objectIndex); assertEquals(19, map.indexMap.size());
        BillboardForm form = new BillboardForm();
        form.pickable.set(false);
        int[] draws = {0};
        FormRenderer<BillboardForm> renderer = new FormRenderer<BillboardForm>(form)
        {
            @Override protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2) {}
            @Override protected void render3D(FormRenderingContext context) { draws[0]++; }
        };
        FormRenderingContext context = new FormRenderingContext().set(FormRenderType.ENTITY, new StubEntity(), new MatrixStack(), 240, 0, 0).stencilMap(map);
        renderer.render(context);
        assertEquals(0, draws[0]); assertEquals(20, map.objectIndex);
        form.pickable.set(true);
        renderer.render(context);
        assertEquals(1, draws[0]); assertEquals(21, map.objectIndex);
        assertTrue(context.stack.isEmpty());
    }
}
