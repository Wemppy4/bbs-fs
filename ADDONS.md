# Writing an addon for BBS

BBS has an addon API: a package of contracts an addon can build against, and a set of events BBS
posts while it registers its own things, which is where an addon registers its own.

## What is and isn't a contract

Everything in `mchorse.bbs_mod.api` and its sub-packages is a contract. Nothing else is.

The rest of BBS moves without notice — a class gets renamed, a field goes private, a method grows
a parameter — and an addon that reached into it breaks **silently, in the game rather than on the
build**. That is not a threat, it is just what a mixin into another mod's internals costs.

The whole public surface of the API is dumped into [`api/bbs-api.txt`](api/bbs-api.txt), which
lives in this repository. Any change to it shows up in the diff of the commit that made it, and
`gradlew apiCheck` (which `build` depends on) fails when the two drift apart. `BBSApi.VERSION` is
bumped whenever a change in there is one an addon cannot survive.

## Getting in

An addon implements `mchorse.bbs_mod.api.BBSAddonMod` — an empty marker — and declares it in its
`fabric.mod.json`:

```json
"entrypoints": {
    "bbs-addon": ["com.example.MyAddon"],
    "bbs-client-addon": ["com.example.client.MyAddonClient"]
}
```

`bbs-addon` is read on both sides, at the very top of BBS's initialization. `bbs-client-addon` is
read on the client only: the client-side events live in BBS's client source set, and a class that
so much as mentions one of them cannot be loaded on a dedicated server. Keep the two apart.

Methods annotated with `@Subscribe` and taking exactly one argument are then called with the
events of that argument's type:

```java
public class MyAddon implements BBSAddonMod
{
    @Subscribe
    public void onSettings(RegisterSettingsEvent event)
    {
        BBSApi.requireVersion("myaddon", 1);
        /* ... */
    }
}
```

Subscribers are collected from the whole class hierarchy, an override replaces the method it
overrides, and an override that drops `@Subscribe` unsubscribes it. A subscriber that throws is
logged and the rest keep running — a broken addon stays distinguishable from an absent one.

Subscribing to a base event type receives every event derived from it: `BaseRegisterSettingsEvent`
is called for both the common and the client settings events.

## Versioning

Declare the BBS version your addon needs in `fabric.mod.json`, so the loader refuses a mismatch
before any code runs:

```json
"depends": { "bbs": ">=2.6" }
```

and call `BBSApi.requireVersion("myaddon", <api version>)` from your entry point for the case
where it got in anyway. Without it, a mismatch reads as "the game crashes on the first right
click" rather than "this addon does not fit this BBS build".

## Building against BBS

There is no public Maven repository. Publish BBS into your local one:

```
gradlew publishToMavenLocal
```

and depend on it:

```groovy
repositories {
	mavenLocal()
}

dependencies {
	modImplementation("mchorse:bbs:${bbs_version}") { transitive = false }
}
```

`transitive = false` keeps BBS's own Sodium/Iris/glsl-transformer out of your build. Note the
consequence: your dev client then runs BBS without Sodium and Iris, and BBS's mixins into them are
skipped with a warning, so nothing that depends on shaders is exercised there.

Keep `yarn_mappings` and `loader_version` in lockstep with the BBS build you compile against — a
mismatch breaks the dev environment in ways that look like anything but a mapping mismatch.

**The Loom remap cache trap.** Loom caches the remapped mod by its Maven coordinates, and BBS's
version does not change while it is being worked on. So a rebuilt BBS is silently ignored, and it
looks exactly like your changes to BBS not existing. See `docs/addon-template/build.gradle` for a
build script that notices and clears the cache by itself.

## The registration events, in order

BBS fills a registry, then posts the event for it. Subscribe to the event, register into what it
hands you, and your things sit next to BBS's own.

Both sides, from `BBSMod`:

| Event | What it is for |
| --- | --- |
| `RegisterSourcePacksEvent` | your own assets, addressable as `yourmod:...` |
| `RegisterKeyframeFactoriesEvent` | your own keyframe value types |
| `RegisterFormsEvent` | your own forms |
| `RegisterCameraClipsEvent` | your own camera and overlay clips |
| `RegisterActionClipsEvent` | your own action clips |
| `RegisterSettingsEvent` | your own settings file and categories |
| `BBSReadyEvent` | everything is registered; look, don't register |

The client only, from `BBSModClient`:

| Event | What it is for |
| --- | --- |
| `RegisterL10nEvent` | your own language files, before the first load |
| `RegisterModelLoadersEvent` | your own model format |
| `RegisterFormSectionsEvent` | your own tab in the form palette |
| `RegisterKeybindsEvent` | your own key combos, as a class of them |
| `RegisterClientSettingsEvent` | your own client settings |
| `RegisterFormRenderersEvent` | how your forms draw |
| `RegisterFormEditorsEvent` | how your forms are edited |
| `RegisterClipPanelsEvent` | how your clips are edited |
| `RegisterKeyframeEditorsEvent` | how your keyframe values are edited |
| `RegisterValueWidgetsEvent` | how your settings value types are drawn |
| `RegisterClipRenderersEvent` | how your clips draw their strip on a timeline |
| `RegisterTrackStylesEvent` | the colour and icon of your track properties |
| `RegisterImportersEvent` | what happens to a file of your type dragged into the assets |
| `RegisterDashboardPanelsEvent` | your own dashboard panel (posted when the dashboard is first opened) |
| `BBSClientReadyEvent` | the client half is registered |

No registry fills itself in a static initialiser any more, so "before" and "after" mean something:
whatever you register in one of these events is in place before BBS uses it, and BBS's own entries
are in place before you are called.

## Columns of the clip palette

The palette a new clip is picked from is split into columns, one search field over all of them.
BBS's own camera clips sit in `overwrite`, `modifiers` and `misc`; its action clips in `general`,
`blocks` and `items` (the ids are in `ClipCategories`). An addon can put its clips into
one of those or add a column of its own:

```java
@Subscribe
public void registerClips(RegisterCameraClipsEvent event)
{
    Link column = Link.create("yourmod:gadgets");

    event.factory.category(column);
    event.factory.register(Link.create("yourmod:gadget"), GadgetClip.class, new ClipFactoryData(Icons.GEAR, 0x33aa77).in(column));
}
```

Columns appear in the order they were added. The title is the language key
`bbs.ui.camera.clip_categories.yourmod:gadgets`. A clip type that names no column, or one that
nobody added, lands in "Other", which is shown only when something is in it.

## Types named in the API

The API package holds the events and the entry points. The types they hand you — `Form`, `Clip`,
`FormArchitect`, the factory interfaces — live where they always did, and are covered by the same
promise by being named in a signature that `api/bbs-api.txt` records. Reaching for a type the API
never mentions is reaching into BBS's internals.

## Extending one of BBS's forms

A renderer and an editor panel are looked up by the form's own class first, then by the classes it
extends. So a form extending, say, `BillboardForm` draws and edits like one until you register
something of your own for it — and you register only the part you actually changed.

## Naming things

Give every id you register a namespace: `yourmod:gadget`, not `gadget`. Two reasons. It keeps you
out of BBS's way and out of other addons'. And it is what makes your data survive your addon being
switched off: BBS keeps unknown namespaced keys, unknown forms, unknown clips and unknown tracks
verbatim and writes them back out unchanged, so a user who removes your addon for an evening still
has their scene when they put it back. An un-namespaced key of yours is indistinguishable from one
of BBS's own that was removed, and is dropped on save.

## Adding to forms you did not write

`RegisterFormModifiersEvent` runs a step of yours on every form BBS makes:

```java
event.register((form) -> form.add(new ValueFloat("myaddon:wobble", 0F)));
```

Everything else follows from the value existing. It is saved and read like any other; the timeline
finds a track for it, because the track catalogue walks a form's values rather than a list of
names; its keyframes work, because the value carries its own factory. Give the track a colour with
`RegisterTrackStylesEvent` if you care how it looks.

The palette's forms are templates and are built directly, so they are not modified. Everything
that lands in a scene is copied, and a copy is read through the factory — so what a user actually
animates always has your value.

## The life of a film

`FilmEvents` has the four moments a scene has: `CREATED`, `TICK_BEFORE`, `TICK_AFTER`,
`RENDER_AFTER`, `SHUTDOWN`. `FormRenderEvents.BEFORE` and `.AFTER` wrap the drawing of one form —
every form, anywhere BBS draws one.

These are plain Fabric events, not the addon bus: two of them run every tick and every frame, and
the bus finds its subscribers by reflection. Register your listeners once, from `BBSReadyEvent` or
`BBSClientReadyEvent`.

Whatever you build in `CREATED`, let go in `SHUTDOWN`. A listener that skips it leaks for the rest
of the session. Every controller comes through — the film playing in the world, the editor's
preview, the recorder — so ask the controller which it is when that matters.

`BBSModClient.getFilms().getControllers()` lists the films playing right now.

## Vanilla fields BBS widened

BBS widens access to a handful of vanilla fields (the ones the item-model predicates read, and the
game's framebuffer). An access widener applies only to the mod that declares it, so yours would
need a second copy of BBS's — and two copies drift. Call
`mchorse.bbs_mod.api.compat.VanillaAccess` and `…api.client.compat.ClientVanillaAccess` instead;
a method needs no widener.

## A worked example

[`docs/addon-template`](docs/addon-template) is a complete, buildable addon: entry points on both
sides, a version check, its own assets source, a form built on top of one of BBS's, a value added
to every form, a settings category, a track style and film listeners. Its `build.gradle` also
carries the Loom cache workaround described above.

Build it with BBS's own wrapper:

```
gradlew publishToMavenLocal          # in the BBS repository
gradlew -p docs/addon-template build
```

## What is still a mixin

Honest list of what the API does not cover yet, so nobody hunts for a method that isn't there:

- **Audio.** A clip that plays sound extends `AudioClip`; there is no interface for it, because
  the audio path — the renderer, the export, the waveform on the timeline — is written against
  that class. This is what `VideoClip` does.
- **Track kinds.** The kinds of track a film can animate are an enum. An addon's own kind of track
  (as opposed to its own animated property, which the modifier above covers) has no way in yet.
- **Per-instance runtime attachments.** Additional saved values can use form modifiers; typed
  runtime fields and construction of direct `new Form()` templates still need addon-owned storage.

## API version 2: editor and evaluated scene extensions

Version 2 is additive: version 1 addons remain supported. An addon using these hooks should
call `BBSApi.requireVersion(modId, 2)` before registration. All client callbacks run on the
client thread. They must not retain borrowed matrices/UI contexts for background work.

### Editor controls and display

- `RegisterFormPanelsEvent`: register a consumer of each `UIForm<?>`. Add panels with
  `registerPanel`; they receive the normal edit lifecycle. This runs before Material/General,
  while the editor has no assigned form yet. Filter by editor type, not `editor.form`.
- `RegisterReplayActionsEvent`: create a control in actor properties from the film panel and
  a supplier of the displayed replay. The supplier follows selection; at multi-selection it
  refers to the first replay. Resolve it on activation and capture that replay for a dialog.
- `RegisterTrackStylesEvent.registerLabel(property, IKey)` or the four-argument `register`
  gives a property a live localized label. It affects display only, never saved `TrackId`s.
- `TimelineEvents.OVERLAY`: draw over either clip or keyframe timeline. `toX` accepts absolute
  film ticks, including fractions; BBS accounts for the clip's offset, zoom and scroll.
- `FormPreviewEvents.OVERLAY`: draw after either plain or pickable form preview. The picking
  pass has finished, but the preview has not returned to 2D. Restore any changed GL state.

### Transactions and lifecycle

`FilmEditEvents.CHANGED` receives `(film, values, cause)`, once per film per completed editor
batch, with `EDIT`, `UNDO` or `REDO`. Undo/redo fires after the values are restored. The value
list is immutable; its elements are live values. Intermediate drag samples, loading and
playback do not post edits. Filter changes by their paths/owners before invalidating caches.

`Films.reset()` detaches and shuts down every controller, including the recorder, so normal
`FilmEvents.SHUTDOWN` cleanup also runs on world reset. Duplicate controller references close
once. Addon-owned resources outside controllers still need their own teardown.

### Evaluated transforms, bones and attachments

`FormPoseEvents` exposes the same external pose stages used by rendering and matrix walks:

- `TRANSFORM`: modify the temporary local `Transform` after its saved animation and overlays.
  Both stack and matrix application use it. Scale stays whatever the listener leaves there.
  `FormRenderer.createTransform()` remains the unmodified animation/overlay result;
  `createEvaluatedTransform(transition)` includes contributions.
- `PARENT_FRAME`: observe the frame above each form before its own transform, with entity,
  body-part path and transition. Called for model forms as well as other forms.
- `MODEL_POSE`: after animation/IK, before built-in chains in `RENDER` and before bone capture
  in `MATRICES`. The latter has no world base matrix. Modify evaluated bones, not saved keys.
- `CLAIM_CHAIN`: return true to exclude one chain from the built-in solver. Any claimant wins;
  evaluate ownership from the form's current settings, which participate in cache invalidation.
- `PIVOT_OFFSETS`: request offsets in the default pivot-frame walk. Explicit callers of the
  overload with an `applyStretch` argument keep control of that choice.
- `ANCHOR`: return a temporary resolved anchor; never modify the saved anchor. Do not return null.
- `ACTOR_BEFORE`: prepare per-actor state before its anchors and render matrices are resolved.

These hooks may run repeatedly for a single displayed frame. **Do not step a simulation in
pose callbacks.** A simulation evaluating its animation targets must scope out its own
contributions (with `try/finally`) while retaining physical ancestors it needs. Ownership of
that evaluation scope and the simulation cache belongs to the addon. Listeners compose in
registration order; multiple solvers must agree which bones they own.

### Viewport tools

A form panel can implement `FormEditorTool`: return its selected `UIPropTransform` and its
origin for LOCAL/PARENT/WORLD space. Returning null relinquishes control. The states editor
has priority. BBS routes gizmo placement, input and transform hotkeys through the active panel;
the transform's callbacks provide edit/undo boundaries.

`RegisterFilmToolsEvent` creates a `FilmEditorTool` per film controller. BBS attaches it to
the UI tree. It can supply an edit target, start a gizmo gesture, consume input, update a HUD
or draw in the world. Null target/start results keep the built-in tool. Target overrides are
not queried when editing is blocked. Input is dispatched in registration order; tools must
honor the controller's current editing state. Clean up active gestures on removal or stop.
`FilmGizmoEvents.DRAW` supplies identical context for visual and stencil placement; the first
listener returning true owns the draw. Balance matrix pushes in both paths.

### Structure fragments

`StructureRenderEvents.RENDER` runs after the source structure is loaded, including during
picking. Return true after drawing a replacement to suppress the original. Use
`StructureRenderPart` for each fragment: it has an independent renderer/cache, applies a
local matrix and uses the normal block, shader, transparency and block-entity paths.
`createData` copies blocks, positions and block entity NBT. Never modify shared source data.
Recreate parts when source data or biome changes; resource rebakes remain automatic.
Part rendering does not re-enter `StructureRenderEvents`. Store per-renderer caches with a
static `RenderAttachment<T>` key. The state lives on the renderer, not in a global map, and
may safely refer back to its owner. It is not saved/copied. Native resources still require
explicit scene teardown; attachments do not introduce automatic disposal.

### Migrating addon property names

Register `FormPropertyAliases.register("old_addon_mass", "addon:mass")` on both sides before
loading documents. Form values, disabled-track names, legacy track maps and structured track
lists then read the old spelling and write the canonical one. Bone/material names are not
renamed. If both spellings exist, the canonical one wins, independent of input order.
Unrecognized value factories remain preserved as raw tracks with the migrated address.

A legacy document must be opened and saved with the updated addon installed once. Afterwards
namespaced values survive saving without the addon. BBS cannot infer ownership of arbitrary
old unnamespaced keys when the addon that registers their aliases is absent.

### Track categories and numeric shortcuts

Subscribe to `RegisterTrackCategoriesEvent` in your `bbs-client-addon` entry point:

```java
@Subscribe
public void onTrackCategories(RegisterTrackCategoriesEvent event)
{
    event.register(new TrackCategory("myaddon:effects", Icons.PARTICLE,
        IKey.constant("Effects"), IKey.constant("Effect tracks")),
        (track, owned) -> owned && track.kind() == TrackKind.PROPERTY
            && track.subject().startsWith("myaddon:"));
}
```

`TrackCategory` and `TrackCategories` are in `api.client.editor`. IDs must be namespaced
and unique. Rules receive a `TrackId` (including the owning form path) and whether the
track belongs to a form. Addon rules run before built-in classification; first match wins.
Registration order is the button order after built-in categories. Register during this
startup event, before keybind settings load; late registration is rejected.

Both film and animation-state editors use the registry. Addon buttons appear only when
the selected part has matching tracks; animation states still exclude solver tracks.
All Tracks includes them too. Empty active categories fall back to Form.

Shortcuts target **visible positions**, excluding All Tracks: `1` through `9`, then `0`.
BBS creates as many configurable shortcut slots as registered categories; slots past ten
start unbound. They live in the existing replay-editor keybind settings and preserve old
`tab_1` through `tab_5` overrides. No addon key handler is needed. Hiding/reappearing tabs
preserves registry order, including wrapped rows. `RegisterKeybindsEvent.register(KeyCombo)`
also supports individual dynamically created combos for other addon actions.
