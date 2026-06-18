# BBS 1.21.1 → 1.21.11 migration reference (for porting agents)

Working dir: `C:\Users\Qualet\Documents\Project\Minecraft\BBS\bbs-fs` (branch `port/1.21.11`).

## Rules
- Edit ONLY the files assigned to you. Do not touch other files.
- Do NOT run gradle (`./gradlew ...`). A parallel process owns the build; running it will collide.
- Match the surrounding code style (this codebase uses Allman braces, 4-space indent).
- Prefer the minimal change that compiles correctly against 1.21.11. Do not refactor unrelated code.
- If a fix is genuinely ambiguous or needs a cross-file change outside your files, note it in your report instead of guessing wildly.

## Your compile errors
The full 1.21.11 `compileJava` error log is at (Bash) `/tmp/bbs_main_compile.log` and (Read) `.port\main_errors.log`.
Grep it for each of your file names to see EXACT errors incl. `symbol:` / `location:` lines, e.g.:
```
grep -n -A4 "ActorEntity.java" /tmp/bbs_main_compile.log
```

## GROUND TRUTH — look up real 1.21.11 Yarn signatures with javap
The Yarn-named MC classes are in two jars. Use JDK 21 javap:
```
JAVAP='/c/Users/Qualet/.jdks/ms-21.0.10/bin/javap'
CP='C:/Users/Qualet/.gradle/caches/fabric-loom/1.21.11/net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/common-unpicked.jar;C:/Users/Qualet/.gradle/caches/fabric-loom/1.21.11/net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/clientOnly-unpicked.jar'
"$JAVAP" -cp "$CP" net.minecraft.item.ItemStack          # list public members
"$JAVAP" -cp "$CP" -p net.minecraft.nbt.NbtCompound      # include private
```
Find a class's new name/package (when "package ... does not exist" / "cannot find symbol"):
```
jar tf 'C:/Users/Qualet/.gradle/caches/fabric-loom/1.21.11/net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/common-unpicked.jar' | grep -i SomeName
jar tf 'C:/Users/Qualet/.gradle/caches/fabric-loom/1.21.11/net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/clientOnly-unpicked.jar' | grep -i SomeName
```
ALWAYS verify a signature with javap before trusting the patterns below.

## Known 1.21.1 → 1.21.11 patterns (verify each with javap)
- **ModelTransformationMode** removed → `net.minecraft.item.ItemDisplayContext` (now in the `item` package, so it's available to common/main code). Enum: NONE, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, HEAD, GUI, GROUND, FIXED, ON_SHELF.
- **Identifier**: `Identifier.of("ns","path")` / `Identifier.of("ns:path")`; no `new Identifier(...)`.
- **EntityAttributes**: GENERIC_ prefix dropped, e.g. `EntityAttributes.GENERIC_MAX_HEALTH` → `EntityAttributes.MAX_HEALTH`. Verify exact field names: `javap EntityAttributes`.
- **Attribute creation**: registering attributes uses `DefaultAttributeContainer.Builder` with `EntityAttributes.*` registry entries.
- **ActionResult unified (1.21.2)**: `TypedActionResult` removed. `Item.use(...)` now returns `ActionResult`; `ItemStack.use*`/`useOnBlock` return `ActionResult`. Use `ActionResult.PASS`/`SUCCESS`/`CONSUME`/`FAIL`; for returning a changed hand stack use `ActionResult.success(stack)`→ now `.withNewHandStack(stack)` on the result. Verify against `net.minecraft.util.ActionResult` and `net.minecraft.item.Item`.
- **Damage is server-side (1.21.2)**: `entity.damage(net.minecraft.server.world.ServerWorld, DamageSource, float)`. Old 2-arg `damage(DamageSource, float)` is gone. Get the ServerWorld from the entity's world (cast/guard) or the action context.
- **getWorld() → getEntityWorld() (1.21.9)** for `Entity`. Verify which is needed; `getWorld()` may still exist on some types.
- **PlayerInventory.selectedSlot** is private → `getSelectedSlot()` / `setSelectedSlot(int)`.
- **NBT getters return Optional (1.21.5)**: `NbtCompound.getInt("k")` returns `OptionalInt`/`Optional<...>`. Use the defaulted overload `getInt("k", default)` where it exists, or `.orElse(default)`. `getCompound("k")` → `getCompoundOrEmpty("k")` (or `getCompound("k")` returns Optional). Array getters (`getIntArray`, `getByteArray`) may lack a defaulted overload — handle the Optional explicitly. ALWAYS javap `net.minecraft.nbt.NbtCompound` for the exact return type of each getter you touch.
- **Persistence rewrite (1.21.6)**: 
  - `Entity`: `writeCustomDataToNbt(NbtCompound)`/`readCustomDataFromNbt(NbtCompound)` → `writeCustomData(net.minecraft.storage.WriteView)` / `readCustomData(net.minecraft.storage.ReadView)`.
  - `BlockEntity`: `writeNbt(NbtCompound, WrapperLookup)`/`readNbt(...)` → `writeData(WriteView)` / `readData(ReadView)`.
  - `ReadView`/`WriteView` are codec/typed accessors: `view.read(key, codec)`, `view.getString(key)`, `view.putString(key, v)`, child views via `view.get(key)` / `view.getReadView` etc. javap `net.minecraft.storage.ReadView` and `WriteView` for exact methods.
- **Item/Block/BE settings**: `FabricItemSettings`/`FabricBlockSettings` removed → `new net.minecraft.item.Item.Settings()` / `net.minecraft.block.AbstractBlock.Settings.create()`. Items/blocks/block-entity-types now REQUIRE a `registryKey(...)`/`RegistryKey`. `EntityType.Builder.build(RegistryKey)` now takes a key. `BlockEntityType.Builder` → `net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder`. Verify all via javap + jar tf.
- **GameRules**: rule registration / `GameRules.Key` / `GameRules.register` signatures changed; verify with javap `net.minecraft.world.GameRules`.
- **Text/sendMessage**: `PlayerEntity.sendMessage(Text)` overloads changed; for server messages use `ServerPlayerEntity.sendMessage(Text, boolean)` or `sendMessageToClient`. Verify.
- **getCommandSource**: `ServerPlayerEntity.getCommandSource()` may now require a `ServerWorld` arg, or moved. Verify with javap.

When a referenced Fabric API class is missing (`package net.fabricmc.fabric.api... does not exist`), search the fabric-api sources jar:
`jar tf 'C:/Users/Qualet/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.141.4+1.21.11/*/fabric-api-0.141.4+1.21.11-sources.jar' | grep -i Name` (path has a hash dir; use a glob or `find`).
