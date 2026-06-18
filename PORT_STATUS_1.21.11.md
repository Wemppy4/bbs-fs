# BBS 1.21.11 port — status (autonomous session)

Branch: `port/1.21.11` (based off `1.21.1`). Goal: build-only port to MC 1.21.11 (no runtime testing this session).
See `PORT_PLAN_1.21.11.md` for the full strategy. This file is the live progress + remaining-work breakdown.

## ✅ Done & verified

| Area | Status | Verify |
|---|---|---|
| Toolchain bump | MC 1.21.11, Yarn 1.21.11+build.6, loader 0.19.3, fabric-api 0.141.4+1.21.11, Loom 1.15.5, Gradle 9.2, Java 21 | `./gradlew genSources` → BUILD SUCCESSFUL |
| Access-widener | dead RenderSystem/GlStateManager entries (removed in 1.21.5) commented; framebuffer entries kept (still valid) | `:validateAccessWidener` passes |
| Iris/Sodium/Indium/DH decoupling | 20 files deleted, 11 mixins removed, `BBSRendering` stubbed, `ShaderCurves` trimmed to vanilla | n/a |
| **Core / common / server** (`src/main/java`, ~452 files) | **COMPILES on 1.21.11** | `./gradlew compileJava` → BUILD SUCCESSFUL |
| Client mechanical migration | confirmed renames applied across renderers/film/particles/selectors/camera/network/utils | `compileClientJava` errors 1702 → 1586 |

### Core migration covered (all compiling)
Registration (`EntityType.Builder`+`registryKey`, `AbstractBlock.Settings`, `TypedEntityData` block-entity-data component, `GameRule<Boolean>`+`GameRuleBuilder`), entity attributes de-prefix, `getEntityWorld`/`getEntityPos`/`last*` renames, **persistence rewrite** (`BlockEntity.readData/writeData`, `Entity.readCustomData/writeCustomData` via `ReadView`/`WriteView` incl. `PlayerEntityMixin`), `ActionResult` unification, server-side `damage(ServerWorld,…)`, `parseAndExecute`, `getSelectedSlot`, NBT `Optional` getters, `ModelTransformationMode`→`ItemDisplayContext`, server mixins, `PlayerConfigEntry` permission check, networking payload component changes.

## ⏳ Remaining = the client rendering rewrite (~1586 errors, ~96 files)

This is a genuine architectural rewrite (the reason a green client build is not achievable build-only in one session — it needs **runtime iteration**, which was deferred). The whole client compiles only once the rewrite is complete: the 2D foundation's public API must change (new GUI uses `Matrix3x2fStack`/`GuiRenderState`), which cascades through all ~700 `ui` call sites — there is no partial-compile milestone short of finishing it.

### Error clusters (after the mechanical pass)
| Package | Errors | Dominant cause |
|---|---|---|
| `ui` | 704 | 2D foundation (`Batcher2D`, `graphics/Draw`, `UIRenderingContext`) → new pipeline; `DrawContext.pose()` now `Matrix3x2fStack`; two-phase GUI (1.21.6) |
| `forms` | 342 | form renderers → `RenderPipeline`/`RenderLayer.of`, framebuffers, submit model; `CustomVertexConsumerProvider` (RenderLayer.draw→submit) |
| `client` | 160 | `BBSShaders` (7 custom programs → `RenderPipelines`), `BBSRendering`, item renderers (`BuiltinItemRendererRegistry` removed) |
| `cubic` | 150 | model rendering, `cubic/render/vao/*` (direct GL), `RenderSystem.setShader` |
| `mixin` | 68 | client render mixins lose injection points (`GameRenderer`/`WorldRenderer`/`RenderLayer.draw`→`submit`/entity render-state) |
| `graphics` | 50 | `Draw`, `Framebuffer`/`FramebufferManager` (GL30 → `GpuTexture`/`RenderPass`), `Texture`, `FontRenderer` |
| `particles` | 44 | billboard/appearance render via `Tessellator`/`BufferBuilder`/shader programs |
| `film` / `utils` | ~36 | residual `RenderSystem.*` state calls; `MatrixStackUtils` (`RenderSystem.getProjectionMatrix`/`applyModelViewMatrix`) |

### API axes that must be reworked (all confirmed against 1.21.11 Yarn via javap)
1. **GPU pipeline (1.21.5)** — `RenderSystem.setShader` + `GameRenderer.getXxxProgram()` + `BufferRenderer` are GONE. Immediate draws → build `BufferBuilder`→`BuiltBuffer`→draw via a `RenderLayer` carrying a `RenderPipeline`, or drive a `RenderPass` (`RenderSystem.getDevice()`→`GpuDevice`→`CommandEncoder.createRenderPass`). State (`enableDepthTest`/`enableBlend`/`enableCull`) moves into the `RenderPipeline`.
2. **Custom shaders** — the 7 `BBSShaders` programs → code-declared `RenderPipelines.register(...)`; GLSL `.vsh/.fsh` carry over; uniforms → std140 UBOs (1.21.6). Re-route every `RenderSystem.setShader(() -> program)`.
3. **2D GUI (1.21.6)** — `DrawContext.pose()` returns `Matrix3x2fStack` (2D, not 4×4); two-phase `GuiRenderState`; `Batcher2D` (bypasses `DrawContext`) needs the most work.
4. **Framebuffers (1.21.5/1.21.6)** — `graphics/Framebuffer` GL30 wrapper → `GpuTexture`/`GpuTextureView` + `RenderPass`. Affects `FramebufferFormRenderer`, `BBSRendering` (video export).
5. **EntityRenderer render-state (1.21.2)** — `EntityRenderer<T>` → `EntityRenderer<T, S extends EntityRenderState>`; `render`/`getTexture` signatures changed; `EntityRendererRegistry`/`BlockEntityRenderer` need 2 type args. Affects `ActorEntityRenderer`, `GunProjectileEntityRenderer`, `ModelBlockEntityRenderer`, `MorphRenderer`.
6. **Item model rewrite (1.21.4)** — `BuiltinItemRendererRegistry`/`DynamicItemRenderer` removed. Affects `ModelBlockItemRenderer`, `GunItemRenderer` (move to the `items`/model-override system).
7. **Submit model (1.21.9)** — `RenderLayer.render()`→`submit()`; custom geometry via `SubmitNodeCollector`. Affects render mixins + form renderers.
8. **Font (1.21.6/1.21.9)** — `Font.drawInBatch`→`prepareText`/`GlyphVisitor`/`submitText`. Affects `FontRenderer`.
9. **Fabric API relocations** — `WorldRenderContext` moved+split (extraction vs render); `WorldRenderEvents` relocated; `EntityRendererRegistry`/`BlockEntityRendererFactory` signatures.
10. **Misc confirmed** — `GameProfile` is a record (`name()`/`id()`); `PLAYER_MODEL_PARTS` tracked-data removed; `GlStateManager` moved to `com.mojang.blaze3d.opengl`; `OtherClientPlayerEntity(ClientWorld, GameProfile)`.

### Recommended sequence for the rewrite (needs a 1.21.11 runtime to validate)
1. Foundation, coherently & together: `BBSShaders`→`RenderPipelines`, then `graphics/Draw` + `ui/framework/elements/utils/Batcher2D` + `UIRenderingContext` (2D), then `graphics/Framebuffer`/`Texture`, `forms/renderers/FormRenderType`, `CustomVertexConsumerProvider`, `FontRenderer`.
2. Downstream once foundation is stable: `ui/**`, `forms/renderers/**`, `cubic/render/**`, `particles/**`.
3. Renderers: entity/block-entity/item render-state + item-model migration.
4. Client render mixins: retarget to new injection points or temporarily disable (like Iris/Sodium) with TODOs.
5. Re-enable Iris/Sodium against 1.21.11-matched builds (separate effort).

## How to resume
- 1.21.11 decompiled Yarn jars (ground truth) at `~/.gradle/caches/fabric-loom/1.21.11/net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/{common,clientOnly}-unpicked.jar`. Look up exact signatures with JDK21 `javap` — see `.port/REF.md` (gitignored) for the exact command + the full migration pattern catalogue.
- Iterate `./gradlew compileClientJava` (temporary `-Xmaxerrs 5000` in `build.gradle` shows the full list — revert before release).
- `git log port/1.21.11` has per-phase checkpoints.
