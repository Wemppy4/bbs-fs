# fix/procedural-leg-walk

## Bug
Procedural walk animation (config.json `base_pre`/`base_post` + built-in limb sway in
`ProceduralAnimator`) had frozen / non-oscillating legs. Legs use
`cos(limbPhase * 0.6662F) * 1.4F * limbSpeed` where:
- `limbPhase = target.getLimbPos(transition)` — must be the ever-growing walk phase
- `limbSpeed = target.getLimbSpeed(transition)` — limb swing amount

## Root cause
1.21.11 `LimbAnimator` API rename was ported to the WRONG methods.

Original (1.21.1, `master`):
- `getLimbPos`   -> `limbAnimator.getPos(tickDelta)`      (phase)
- `getLimbSpeed` -> `limbAnimator.getSpeed(tickDelta)`    (amplitude)

1.21.11 `LimbAnimator` (decompiled from minecraft-common named jar):
- `getAnimationProgress(tickDelta)` = `(animationProgress - speed*(1-tickDelta)) * timeScale`  == old getPos  (PHASE)
- `getAmplitude(tickDelta)`         = `min(1, lerp(tickDelta, lastSpeed, speed))`               == old getSpeed (AMPLITUDE, vanilla-clamped)
- `getSpeed()` = raw `speed` field (no interp)

Buggy port wired:
- `getLimbPos`   -> `getAmplitude(tickDelta)`  (WRONG: returns ~constant amplitude, not phase
   -> `cos(limbPhase*0.6662)` never advances -> legs locked at fixed angle)
- `getLimbSpeed` -> `getSpeed()`               (raw, un-interpolated)

## Fix (faithful to original semantics)
MCEntity.java + StubEntity.java:
- `getLimbPos`   -> `getAnimationProgress(tickDelta)`
- `getLimbSpeed` -> `getAmplitude(tickDelta)`

Both flow through IEntity into ProceduralAnimator (legs/arms) and MolangHelper
(`limbSwing`/`limbSwingAmount` Molang queries), so both are corrected.

StubEntity already adapts the forced 3-arg `updateLimbs(speed, 0.4F, 1.0F)` (timeScale=1.0F neutral).

## Status
compileJava green. Runtime not run (per instructions).
