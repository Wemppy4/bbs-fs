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
