## Installation

```kotlin
dependencies {
    // Check latest version on the Maven Central badge
    ksp("io.github.recrafter:lapis:<version>")
}

ksp {
    // Required: your mod id, used to prefix generated names
    arg("lapis.modId", "advancements_fullscreen")

    // Required: base package for generated Kotlin
    arg("lapis.packageName", "io.github.diskria.advancements_fullscreen")

    // Required: refmap name used in mixin config
    arg("lapis.refmapFileName", "advancements_fullscreen_refmap.json")

    // Optional: Minecraft jars for backend validation (path-separated)
    // arg("lapis.minecraftJars", "/path/to/client.jar:/path/to/server.jar")
}
```

Lapis is a modern Kotlin‑first KSP processor: you write clean annotated Kotlin, it generates Mixins, descriptor impls and inline extensions so your mod code looks like idiomatic Kotlin instead of handwritten JVM boilerplate.

---

## Core pieces

At a high level, you work with three concepts:

- **Patches**: `@LaPatch` + `LapisPatch<T>` — attach state and behavior to existing Minecraft classes.
- **Descriptors**: `@LaDescriptors` + `LapisDescriptor<…>` — describe methods as strongly‑typed function descriptors.
- **Hooks**: `@LaHook` + descriptor types — inject behavior into existing methods (wrap calls, wrap bodies, change literals).

Below we walk through a real mod (`advancements_fullscreen`) and show, for each feature, **what you write** and **what Lapis generates**, one piece at a time.

---

## Descriptors: strongly‑typed handles to vanilla methods

Descriptors live in `@LaDescriptors` containers and extend `LapisDescriptor<FunctionType>`.  
Example: `GuiGraphics_.blit` from your mod:

```kotlin
@LaDescriptors(GuiGraphics::class)
object GuiGraphics_ {

    @LaMethod
    abstract class blit : LapisDescriptor<
        GuiGraphics.(
            renderPipeline: RenderPipeline,
            identifier: Identifier,
            x: Int, y: Int,
            u: Float, v: Float,
            width: Int, height: Int,
            textureWidth: Int, textureHeight: Int,
        ) -> Unit
    >(GuiGraphics::blit)
}
```

**What you write**

- A `@LaDescriptors(GuiGraphics::class)` container.
- An abstract nested class `blit`:
  - Extends `LapisDescriptor<GuiGraphics.(...) -> Unit>`.
  - References the real method via `(GuiGraphics::blit)` in the super‑constructor call.

**What Lapis generates (descriptor impl + extensions)**  
Kotlin (in your generated package):

```kotlin
public class _GuiGraphics__blit_Impl(
    public val _receiver: GuiGraphics,
    public val renderPipeline: RenderPipeline,
    public val identifier: Identifier,
    public val x: Int,
    public val y: Int,
    public val u: Float,
    public val v: Float,
    public val width: Int,
    public val height: Int,
    public val textureWidth: Int,
    public val textureHeight: Int,
    public val _operation: Operation<Unit>,
) : GuiGraphics_.blit()

public val GuiGraphics_.blit.renderPipeline: RenderPipeline
    get() = (this as _GuiGraphics__blit_Impl).renderPipeline

public val GuiGraphics_.blit.identifier: Identifier
    get() = (this as _GuiGraphics__blit_Impl).identifier

public fun GuiGraphics_.blit.getReceiver(): GuiGraphics =
    (this as _GuiGraphics__blit_Impl)._receiver

public fun GuiGraphics_.blit.invoke(
    _receiver: GuiGraphics = this.getReceiver(),
    renderPipeline: RenderPipeline = this.renderPipeline,
    identifier: Identifier = this.identifier,
    x: Int = this.x,
    y: Int = this.y,
    u: Float = this.u,
    v: Float = this.v,
    width: Int = this.width,
    height: Int = this.height,
    textureWidth: Int = this.textureWidth,
    textureHeight: Int = this.textureHeight,
) {
    (this as _GuiGraphics__blit_Impl)._operation.call(
        _receiver, renderPipeline, identifier,
        x, y, u, v, width, height, textureWidth, textureHeight
    )
}
```

These descriptor impls and extensions are what you use inside hooks like `overrideWindowBackgroundRender` to:

- inspect parameters (`original.renderPipeline`, `original.identifier`, etc.),
- call the original operation (`original.invoke(...)`),
- access the receiver (`original.getReceiver()`).

---

## Patch class: `@LaPatch` + `LapisPatch<T>`

We’ll build the `AdvancementsScreenPatch` step by step. The minimal patch declaration:

```kotlin
@LaPatch(AdvancementsScreen::class, LapisPatchSide.ClientOnly)
abstract class AdvancementsScreenPatch : LapisPatch<AdvancementsScreen>()
```

**What you write**

- `@LaPatch(target = AdvancementsScreen::class, side = LapisPatchSide.ClientOnly)`.
- Abstract class `AdvancementsScreenPatch : LapisPatch<AdvancementsScreen>()`.

**What Lapis generates**

Kotlin implementation class:

```kotlin
package io.github.diskria.advancements_fullscreen

import io.github.diskria.advancements_fullscreen.client.patch.AdvancementsScreenPatch
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen

public class _AdvancementsScreenPatch_Impl(
    override val instance: AdvancementsScreen,
) : AdvancementsScreenPatch()
```

Java mixin that owns the patch instance and implements the extension interface:

```java
package io.github.diskria.advancements_fullscreen.mixin;

import io.github.diskria.advancements_fullscreen._AdvancementsScreenPatch_Extension;
import io.github.diskria.advancements_fullscreen._AdvancementsScreenPatch_Impl;
import io.github.diskria.advancements_fullscreen.client.patch.AdvancementsScreenPatch;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AdvancementsScreen.class)
public class _AdvancementsScreenPatch_Mixin implements _AdvancementsScreenPatch_Extension {
    @Unique
    private AdvancementsScreenPatch patch;

    @Unique
    private AdvancementsScreenPatch getOrInitPatch() {
        if (patch == null) {
            patch = new _AdvancementsScreenPatch_Impl((AdvancementsScreen) (Object) this);
        }
        return patch;
    }

    // methods delegating to getOrInitPatch() are shown in the shared‑state section
}
```

Mixin config entry is generated automatically:

```json
{
  "required": true,
  "package": "io.github.diskria.advancements_fullscreen.mixin",
  "refmap": "advancements_fullscreen_refmap.json",
  "client": [
    "_AdvancementsScreenPatch_Mixin",
    // other generated mixins...
  ]
}
```

---

## Static field access: `@LaAccess @LaStatic @LaField`

```kotlin
@LaPatch(AdvancementsScreen::class, LapisPatchSide.ClientOnly)
abstract class AdvancementsScreenPatch : LapisPatch<AdvancementsScreen>() {

    @LaAccess
    @LaStatic
    @LaField
    abstract val WINDOW_LOCATION: Identifier
}
```

**What you write**

- A single abstract property marked with `@LaAccess @LaStatic @LaField`.

**What Lapis generates**

Java accessor mixin (excerpt from your build):

```java
package io.github.diskria.advancements_fullscreen.mixin;

import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementsScreen.class)
public interface _AdvancementsScreenPatch_Accessor {
    @Accessor("WINDOW_LOCATION")
    static Identifier getWINDOW_LOCATION() {
        throw new IllegalStateException();
    }

    // other fields/methods of this accessor are used by other features
}
```

Patch implementation uses the accessor to implement the abstract property:

```kotlin
public class _AdvancementsScreenPatch_Impl(
    override val instance: AdvancementsScreen,
) : AdvancementsScreenPatch() {

    override val WINDOW_LOCATION: Identifier
        get() = _AdvancementsScreenPatch_Accessor.getWINDOW_LOCATION()
}
```

You can now read `WINDOW_LOCATION` inside your patch as if it were a normal Kotlin property.

---

## Instance field access: `@LaAccess @LaField`

```kotlin
@LaPatch(AdvancementsScreen::class, LapisPatchSide.ClientOnly)
abstract class AdvancementsScreenPatch : LapisPatch<AdvancementsScreen>() {

    @LaAccess
    @LaField
    abstract val tabs: Map<AdvancementHolder, AdvancementTab>
}
```

**What Lapis generates**

Accessor mixin method (same `_AdvancementsScreenPatch_Accessor`):

```java
@Accessor("tabs")
Map<AdvancementHolder, AdvancementTab> getTabs();
```

Patch implementation wiring:

```kotlin
override val tabs: Map<AdvancementHolder, AdvancementTab>
    get() = (instance as _AdvancementsScreenPatch_Accessor).getTabs()
```

From your patch logic you can now iterate `tabs.values` in pure Kotlin while Lapis handles the Mixins.

---

## Method access: `@LaAccess @LaMethod`

Show just one method: `renderInside`.

```kotlin
@LaPatch(AdvancementsScreen::class, LapisPatchSide.ClientOnly)
abstract class AdvancementsScreenPatch : LapisPatch<AdvancementsScreen>() {

    @LaAccess
    @LaMethod
    abstract fun renderInside(guiGraphics: GuiGraphics, x: Int, y: Int)
}
```

**What Lapis generates**

Accessor mixin methods:

```java
@Invoker("renderInside")
void invokeRenderInside(GuiGraphics guiGraphics, int x, int y);
```

Patch implementation override:

```kotlin
override fun renderInside(
    guiGraphics: GuiGraphics,
    x: Int,
    y: Int,
) {
    (instance as _AdvancementsScreenPatch_Accessor).invokeRenderInside(guiGraphics, x, y)
}
```

Extensions file (sugar for calling this from other code):

```kotlin
public fun AdvancementsScreen.renderInside(
    guiGraphics: GuiGraphics,
    x: Int,
    y: Int,
) {
    (this as _AdvancementsScreenPatch_Accessor).invokeRenderInside(guiGraphics, x, y)
}
```

---

## Shared state and a computed property

```kotlin
@LaPatch(AdvancementsScreen::class, LapisPatchSide.ClientOnly)
abstract class AdvancementsScreenPatch : LapisPatch<AdvancementsScreen>() {

    var fullscreenWindowWidth: Int = 0

    val horizontalTabWidth: Int
        get() = AdvancementTabType.LEFT.width
}
```

**What Lapis generates**

Extension interface (from your build):

```java
package io.github.diskria.advancements_fullscreen;

public interface _AdvancementsScreenPatch_Extension {
    int advancements_fullscreen__getFullscreenWindowWidth();
    void advancements_fullscreen__setFullscreenWindowWidth(int newValue);

    int advancements_fullscreen__getHorizontalTabWidth();
}
```

Mixin implementation for these members:

```java
@Mixin(AdvancementsScreen.class)
public class _AdvancementsScreenPatch_Mixin implements _AdvancementsScreenPatch_Extension {
    @Unique
    private AdvancementsScreenPatch patch;

    @Unique
    private AdvancementsScreenPatch getOrInitPatch() { /* ... */ }

    @Override
    public int advancements_fullscreen__getFullscreenWindowWidth() {
        return getOrInitPatch().getFullscreenWindowWidth();
    }

    @Override
    public void advancements_fullscreen__setFullscreenWindowWidth(int newValue) {
        getOrInitPatch().setFullscreenWindowWidth(newValue);
    }

    @Override
    public int advancements_fullscreen__getHorizontalTabWidth() {
        return getOrInitPatch().getHorizontalTabWidth();
    }
}
```

Kotlin extensions that the rest of your mod uses:

```kotlin
public var AdvancementsScreen.fullscreenWindowWidth: Int
    get() = (this as _AdvancementsScreenPatch_Extension)
        .advancements_fullscreen__getFullscreenWindowWidth()
    set(value) {
        (this as _AdvancementsScreenPatch_Extension)
            .advancements_fullscreen__setFullscreenWindowWidth(value)
    }

public val AdvancementsScreen.horizontalTabWidth: Int
    get() = (this as _AdvancementsScreenPatch_Extension)
        .advancements_fullscreen__getHorizontalTabWidth()
```

This is the core “modern Kotlin API over Mixins” effect: patch fields and computed properties become normal extension properties.

---

## Shared function: `setScreenSize`

```kotlin
fun setScreenSize(screenWidth: Int, screenHeight: Int) {
    tabs.values.forEach { it.isCentered = false }
    updateFullscreenUI(screenWidth, screenHeight)
}
```

**What Lapis generates**

Extension interface method:

```java
void advancements_fullscreen__setScreenSize(int screenWidth, int screenHeight);
```

Mixin implementation:

```java
@Override
public void advancements_fullscreen__setScreenSize(int screenWidth, int screenHeight) {
    getOrInitPatch().setScreenSize(screenWidth, screenHeight);
}
```

Kotlin extension:

```kotlin
public fun AdvancementsScreen.setScreenSize(
    screenWidth: Int,
    screenHeight: Int,
): Unit =
    (this as _AdvancementsScreenPatch_Extension)
        .advancements_fullscreen__setScreenSize(screenWidth, screenHeight)
```

---

## Hooks

Hooks combine:

- a descriptor (from `@LaDescriptors`),
- a patch function with `@LaHook`,
- parameter annotations like `@LaTarget`, `@LaOrdinal`, `@LaLiteral`.

Lapis uses these to generate descriptor impls and Mixin Extras injections; you only write Kotlin.

### InvokeMethod: invert scroll direction

Descriptor for the target call:

```kotlin
@LaDescriptors(AdvancementTab::class)
object AdvancementTab_ {

    @LaMethod
    abstract class scroll : LapisDescriptor<
        AdvancementTab.(scrollX: Double, scrollY: Double) -> Unit
    >(AdvancementTab::scroll)
}
```

Hook in the patch:

```kotlin
@LaHook(
    kind = LapisHookKind.InvokeMethod,
    method = AdvancementsScreen_.mouseScrolled::class,
)
fun invertScrollWhenShiftDown(
    @LaTarget original: AdvancementTab_.scroll,
    @LaOrdinal(0) ordinal: Int,
) {
    if (Minecraft.getInstance().hasShiftDown()) {
        original.invoke(scrollX = original.scrollY, scrollY = 0.0)
    } else {
        original.invoke()
    }
}
```

At the call site you work with `original` as a strongly‑typed descriptor instance; Lapis wires this up to a `@WrapOperation`‑style injection using Mixin Extras.

### MethodBody: run vanilla init then recompute fullscreen layout

Descriptor:

```kotlin
@LaDescriptors(AdvancementsScreen::class)
object AdvancementsScreen_ {

    @LaMethod
    abstract class init : LapisDescriptor<
        AdvancementsScreen.() -> Unit
    >(AdvancementsScreen::init)
}
```

Hook:

```kotlin
@LaHook(
    kind = LapisHookKind.MethodBody,
    method = AdvancementsScreen_.init::class,
)
fun calculateOnInit(
    @LaTarget original: AdvancementsScreen_.init,
) {
    original.invoke()
    updateFullscreenUI(instance.width, instance.height)
}
```

You control when to call the original method (`original.invoke()`) and when to run your code, without touching raw `@At` coordinates.

### Literal: replace constants with fullscreen sizes

Descriptor:

```kotlin
@LaDescriptors(AdvancementsScreen::class)
object AdvancementsScreen_ {

    @LaMethod
    abstract class render : LapisDescriptor<
        AdvancementsScreen.(guiGraphics: GuiGraphics, x: Int, y: Int, color: Float) -> Unit
    >(AdvancementsScreen::render)
}
```

Hook that overrides a single literal:

```kotlin
@LaHook(
    kind = LapisHookKind.Literal,
    method = AdvancementsScreen_.render::class,
)
fun overrideWindowWidth(
    @LaLiteral(int = AdvancementsScreen.WINDOW_WIDTH) original: Int,
    @LaOrdinal(0) ordinal: Int,
): Int = fullscreenWindowWidth
```

Each such hook maps to a `@ModifyConstant`‑style injection targeted by literal value and ordinal, letting you declaratively remap all the magic numbers of the vanilla UI into your fullscreen layout.

---

## Summary

- **Descriptors** give you typed handles to vanilla methods and drive hook generation.
- **Patches** give you Kotlin classes with state and helpers that Lapis wires into Mixins and extension APIs.
- **Hooks** let you wrap calls, bodies and constants using descriptors instead of raw JVM signatures.

All together, you write concise, modern Kotlin like in the `advancements_fullscreen` mod, and Lapis generates:

- descriptor impls like `_GuiGraphics__blit_Impl`,
- extension interfaces like `_AdvancementsScreenPatch_Extension`,
- accessor mixins like `_AdvancementsScreenPatch_Accessor`,
- patch impls like `_AdvancementsScreenPatch_Impl`,
- Kotlin extension files and the mixin config.

You keep full type‑safety and Kotlin ergonomics while Lapis handles the low‑level Mixin plumbing.
