# Gizmo render fix (1.21.11) — branch fix/gizmo-render

## Bug
Gizmo (axes / rotation rings / scale cubes / planes / sphere / infinite line / rotate-pie) renders
NOTHING in all three UIs that draw it.

## Callers of Gizmo.INSTANCE.render / renderStencil
- VISUAL `render(stack)`:
  - `BaseFilmController.renderAxes` (film/replay editor, world AFTER_ENTITIES)
  - `UIPickableFormRenderer.renderAxes` (form editor preview panel)
  - `UIModelBlockPanel.renderWorldGizmo` (model block panel, via block-entity renderer)
- STENCIL `renderStencil(stack, map)` (picking):
  - `BaseFilmController.renderAxes` (when stencilMap != null)
  - `UIModelBlockPanel.renderGizmoStencil`
  - `UIPickableFormRenderer` — picking block is DISABLED/commented out (blocked on StencilFormFramebuffer)

## ROOT CAUSE
During the 1.21.11 port the ENTIRE gizmo geometry was stubbed out: the 1.21.5 GPU rewrite removed
`RenderSystem.setShader` / `GameRenderer.getPositionColorProgram` / `BufferRenderer.drawWithGlobalProgram`
/ `RenderSystem.depthFunc` / `VertexBuffer` / `RenderSystem.getProjectionMatrix`. Every draw method
(`drawAxes` visual, `drawRotateHandles`, `drawCachedRing/Sphere/RingBillboard`, `drawInfiniteLine`,
`drawRotatePie`) became a TODO no-op. So the gizmo emits no geometry.

The REPLACEMENT path already exists and is runtime-proven: `mchorse.bbs_mod.graphics.Draw` registers two
POSITION_COLOR/TRIANGLES `RenderPipeline`s (depth + no-depth) and submits immediate buffers through a
`RenderLayer` (`getPositionColorNoDepthLayer()`). `Draw.coolerAxes` (the gizmos-DISABLED else-branch of
`Gizmo.render`) ALREADY uses this path. The gizmo was simply never migrated onto it.

## FIX (faithful to original 1.21.1 = git show 1.21.1:.../Gizmo.java; full dump in .port/ORIG_Gizmo_1_21_1.java)
Re-emit the gizmo geometry through `Draw`'s flush path, reproducing the original geometry/constants exactly.
- Added `Draw.begin()` (POSITION_COLOR/TRIANGLES BufferBuilder) + `Draw.flushNoDepth(BufferBuilder)`
  (submit through the no-depth layer = the original `depthFunc(GL_ALWAYS)` always-on-top behaviour).
- Forced deviation (VertexBuffer removed): the cached ring/sphere VBOs are gone — draw the rings/sphere
  IMMEDIATELY every frame via `Draw.arc3D` / `Draw.sphere` with the original params
  (ring radius=0.22*scale, thicknessRing=0.02*scale*thickness, sphere radius=0.22*scale, 24x24).
- Drop the `modelView()` folding from the draws: the new `RenderLayer.draw` path already applies the active
  global model-view (same as the move/scale handles always did, and `coolerAxes`), and we bake `stack.peek()`
  CPU-side. Folding globalMV again would double-apply it. `modelView()` is KEPT only for `captureRenderMatrix`
  (pick math `lastRenderMatrix`), unchanged.
- Deleted dead helpers: `updateVbos`, `drawCachedSphere`, `drawCachedRing` x2, `drawCachedRingBillboard`,
  fields `lastScale`/`lastThickness`.

## STENCIL pass left STUBBED (intentional, out of scope)
`StencilFormFramebuffer.apply()` binds a dedicated FBO via raw GL, but the new `RenderLayer.draw` renders
into `RenderSystem.outputColorTextureOverride`, NOT that FBO (see port-1-21-11-picking-shaders). So porting
the stencil geometry now would LEAK red id-boxes into the visible target. Picking stays the separately
tracked blocked subsystem. Stencil `drawAxes(stack, map, ...)` kept non-drawing (removed the dead
drawCachedRing/updateVbos calls so it compiles after their deletion).

## Why model-view is correct in all 3 UIs
Forms render correctly in all three panels (per project memory), in the SAME pass/coordinate space the gizmo
is drawn right after. So the active model-view is valid there and the baked stack.peek() lands the gizmo
correctly — identical to how `coolerAxes` and the form geometry already work.

## Status
Implemented; build verification pending. Runtime NOT run (per task). Commits scoped via explicit paths.
