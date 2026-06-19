# morph-picker-forms (temp notes)

Branch: `fix/morph-picker-forms` (off `1.21.11`).
Task: fix Billboard / Extruded / bbs:block / bbs:particle previews in the morph-picker LIST.

## ROOT CAUSE
Morph picker list = form list (UIFormCategory.render -> FormUtilsClient.renderUI -> FormRenderer.renderInUI).
Only ModelForm was ported to the special-element FBO path (BbsFormGuiElementRenderState hardcoded to ModelFormRenderer +
ModelForm-only renderUIPreview). Billboard/Extruded/Block/Particle still do an IMMEDIATE draw in renderInUI, which runs in
the GUI RECORD phase (two-phase GUI 1.21.6+) -> draw is dropped -> blank. Their WORLD render3D draws (getModelLayer /
consumers.draw / getParticlesLayer) are user-confirmed working.

Particle has a SECOND bug: ParticleEmitter.renderUI built POSITION_TEXTURE_COLOR then `built.close()` (dropped the buffer,
old picker-layer no-op) -> draws nothing even in the right phase.

## FIX DESIGN (faithful to original; reuse the working ModelForm FBO path)
1. Generalize special element: BbsFormGuiElementRenderState.renderer ModelFormRenderer -> FormRenderer<?>;
   BbsFormGuiElementRenderer.acquire key -> FormRenderer<?>; import FormRenderer.
2. FormRenderer base: add `public void renderUIPreview(MatrixStack, angle, transition, x1..y2)` (no-op default) +
   shared `submitUIPreview(UIContext, x1..y2)` that does batcher.flush + angle + capture pose(Matrix3x2f) + scissor +
   dc.state.addSpecialElement(new BbsFormGuiElementRenderState(this, ...)).
3. ModelFormRenderer: extract static `getUIPreviewMatrix(angle, y1, y2)` = scale(cellScale,-cellScale,-cellScale)·
   rotateX(PI/8)·rotateY(angle) [cellScale=(y2-y1)/2.5]; renderInUI -> submitUIPreview; renderUIPreview @Override uses helper.
4. Billboard/Extruded/Block: renderInUI -> submitUIPreview; add renderUIPreview mirroring their OLD renderInUI post-ops but
   using base pre-transformed stack + getUIPreviewMatrix (NOT getUIMatrix). Block keeps no applyTransforms (faithful).
5. Particle: renderInUI -> submitUIPreview; renderUIPreview translates base origin from 0.85h up to cell centre
   (translate 0,-0.35*(y2-y1),0) + scale((y2-y1)/2) then emitter.renderUI. Fix ParticleEmitter.renderUI to build
   POSITION_TEXTURE_COLOR_LIGHT + draw via BBSShaders.getParticlesLayer(); ParticleComponentAppearanceBillboard.writeVertexUI
   add .light(MAX). (Tinting.renderUI writes no verts.)

## KEY FACTS
- Base FBO renderer pre-applies translate(w/2, 0.85h, 0)·scale(f,f,-f), f=wsf*scale(1.0). renderUIPreview reconstructs the
  rest of getUIMatrix; the extra -Z in getUIPreviewMatrix cancels base -f to net the original +Z handedness.
- Fog NOT a concern: ModelForm preview already draws via RenderLayer.draw->bindDefaultUniforms(global Fog) in the same GUI
  prepare phase and works; getModelLayer/getParticlesLayer use the same Fog UBO binding.
- MODEL + PARTICLES pipelines both `withCull(false)` -> -Z flip won't cull; matches original disableCull for particle UI.
- ModelPreviewRenderer.ACTIVE (set by base renderer) only affects cubic ModelInstance.render; irrelevant to other forms.

## STATUS: implementing
