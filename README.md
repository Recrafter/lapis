# Lapis

A Kotlin Symbol Processor (KSP) for Sponge Mixins. Built exclusively for Minecraft modding, Lapis focuses on
intent-based injections and compile-time safety. It provides a Kotlin-first frontend with a type-safe DSL, leverages a
MixinExtras-based backend, and automates the generation of Mixin and AW/AT configurations.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.recrafter/lapis.svg?label=Maven+Central&style=for-the-badge)](https://central.sonatype.com/artifact/io.github.recrafter/lapis) [![License: MIT](https://img.shields.io/static/v1?label=License&style=for-the-badge&message=MIT&color=yellow)](https://spdx.org/licenses/MIT)

---

## Features & Magic: Type-Safe Mixins

Writing raw Java Mixins can be a nightmare. You are constantly copying long JVM descriptors, fighting with mapping
updates, and crying when everything silently breaks in runtime.

Meet **Lapis** — a framework that turns Mixins into a beautifully structured, type-safe paradise using Kotlin Schemas
and KSP under the hood.

### 1. Define Your Schemas (Type-Safe Bytecode Blueprint)

First, we will describe the target classes and class members for which we want to write injections or open access if it
is private:

```kotlin
@Class(AdvancementsScreen::class, side = Side.ClientOnly)
object _AdvancementsScreen {

    // Real method in Java: 'boolean mouseScrolled(double x, double y, double dx, double dy)
    // This is 4 'double' params and 'boolean' return type
    // So, we describe the method signature as a functional type inside the @Method annotation generic
    @Method<(x: Double, y: Double, dx: Double, dy: Double) -> Boolean>
    object mouseScrolled
}

@Class(AdvancementTab::class, side = Side.ClientOnly)
object _AdvancementTab {

    // @MappingName (works like @SerialName from kotlinx-serialization) — maps real name to "widgets", 
    // allowing you to use a much cleaner 'tiles' identifier in your mod code!
    @MappingName("widgets")
    @Access(field = [Op.Get, Op.Set]) // Generates a public getter and setter for this private field
    @Field<Map<AdvancementHolder, AdvancementWidget>>
    object tiles

    @Method<(scrollX: Double, scrollY: Double) -> Unit>
    object scroll
}
```

### 2. Write Your Patch (Pure Kotlin, Zero Boilerplate)

```kotlin
@Patch(_AdvancementsScreen::class, side = Side.ClientOnly)
abstract class AdvancementsScreenPatch(@Origin val screen: AdvancementsScreen) {

    // Forget about shadow rules for static fields or member types.
    // Just copy-paste modifiers from the game code. In your patch, it's ALWAYS an abstract property or function!
    @KShadow(Modifier.PRIVATE, Modifier.FINAL)
    abstract val tabs: Map<AdvancementHolder, AdvancementTab>

    // Forget about unique fields, manual interfaces, or interface injection.
    // Just drop @Extension on any property or function, and it feels like a native part of the class!
    @Extension
    var wasHorizontallyScrolled: Boolean = false
        private set

    // Inside 'mouseScrolled', we intercept the 'scroll' call on the advancement tab.
    @Hook<_AdvancementsScreen.mouseScrolled>(Ats.Call)
    @AtCall<_AdvancementTab.scroll>
    fun invertScrollWhenShiftDown(@Origin original: Lapis.Call<_AdvancementTab.scroll>) {
        if (Minecraft.getInstance().hasShiftDown()) {
            wasHorizontallyScrolled = true
            // Invoke vanilla behavior with custom arguments
            original(scrollX = original.scrollY, scrollY = 0.toDouble())
        } else {
            wasHorizontallyScrolled = false
            original() // Invoke default vanilla behavior unchanged!
        }
    }
}
```

### 3. Relax! (What Lapis Generates for You)

```java
// The @Access annotation on a descriptors within a schema triggers the generation of a 
// @Mixin interface to expose private members of the class:
@Mixin(targets = {"net/minecraft/client/gui/screens/advancements/AdvancementTab"})
public interface _AdvancementTab_Accessor {
    // We use the name from the mappings here to ensure Mixin automatically recognizes 
    // its name and to avoid duplicating synthetic methods at runtime,
    @Accessor
    Map<AdvancementHolder, AdvancementWidget> getWidgets();

    @Accessor
    void setWidgets(Map<AdvancementHolder, AdvancementWidget> newValue);
}
```

```kotlin
// Lapis generates these Kotlin extensions allow you to access an open field 
// without manually casting to the generated interface.
// Here you work with 'widgets' under the alias 'tiles' (thanks to @MappingName)
// Btw, inline functions embed their code at the call site, so there is no overhead!
inline fun AdvancementTab.tiles(): Map<AdvancementHolder, AdvancementWidget> =
    (this as _AdvancementTab_Accessor).getWidgets()

inline fun AdvancementTab.tiles(newValue: Map<AdvancementHolder, AdvancementWidget>) {
    (this as _AdvancementTab_Accessor).setWidgets(newValue)
}
```

```java
// And this is a standard @Mixin class, which is necessary to bridge your @Patch with the Minecraft class in runtime.
// For this purpose, two interfaces are generated under the hood.
// And your mod ID is automatically inserted as a prefix in method names of these interfaces to avoid conflicts with other mods.
@Mixin(targets = {"net/minecraft/client/gui/screens/advancements/AdvancementsScreen"})
public abstract class AdvancementsScreenPatch_Mixin implements
    AdvancementsScreenPatch_ExternalBridge, // auto-generated "Duck" interface (also known as an extension interface) for sharing logic
    AdvancementsScreenPatch_InternalBridge  // auto-generated internal interface for communication between @Mixin and @Patch
{
    // This is where the mixin creates and stores your patch. 
    // This means you don't have to write @Unique—your patch is already an isolated state and can't conflict with anything else!
    @Unique
    private AdvancementsScreenPatch_Impl _lapis_patch;

    // By default, patch initialization is unsafe lazy, but if necessary, 
    // you can enable a simpler eager mode or thread-safe access by changing the strategy in the @Patch annotation argument.
    @Unique
    private AdvancementsScreenPatch_Impl _lapis_getOrInitPatch() {
        if (_lapis_patch == null) {
            _lapis_patch = new AdvancementsScreenPatch_Impl(
                // We have to defeat the compiler, because it is certain that the mixin is not the target class, 
                // but we know what it will actually be at runtime. It's good that you don't have to do this yourself anymore!
                (AdvancementsScreen) (Object) this,
                this
            );
        }
        return _lapis_patch;
    }

    @Override
    public boolean _advancements_fullscreen_getWasHorizontallyScrolled() {
        return _lapis_getOrInitPatch().getWasHorizontallyScrolled();
    }

    // This is what our @KShadow(PRIVATE, FINAL) property turned into:
    @Mutable // because property is var
    @Final // The FINAL modifier has been converted into an annotation according to the mixin rules
    @Shadow // ...and PRIVATE was copied directly into field
    private Map<AdvancementHolder, AdvancementTab> tabs;

    // Okay, we've created @Shadow, but how do we connect it to our @KShadow inside the patch?
    // For this we use InternalBridge, which was described above!
    @Override
    public Map<AdvancementHolder, AdvancementTab> _advancements_fullscreen_getTabs() {
        return tabs;
    }

    @Override
    public void _advancements_fullscreen_setTabs(Map<AdvancementHolder, AdvancementTab> newValue) {
        tabs = newValue;
    }

    // And here's how @Hook turned into an injection!
    // JVM descriptors were automatically obtained from your schema descriptors.
    // The mixin will use this method as an entry point and then call your function from the patch!
    @WrapOperation(
        method = {"mouseScrolled(DDDD)Z"},
        at = {@At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementTab;scroll(DD)V",
            unsafe = true
        )}
    )
    private void invertScrollWhenShiftDown(
        AdvancementTab _lapis_receiver,
        double _argument_scrollX,
        double _argument_scrollY,
        Operation<Void> _lapis_original
    ) {
        _lapis_getOrInitPatch().invertScrollWhenShiftDown(new _AdvancementTab_scroll_Call(_lapis_receiver, _argument_scrollX, _argument_scrollY, _lapis_original));
    }
}
```

```kotlin
// Lapis generates a clean, public extension property for your mod.
// No casting or bridges in your business logic — it feels like a native Minecraft property!
inline val AdvancementsScreen.wasHorizontallyScrolled: Boolean
get() = (this as AdvancementsScreenPatch_ExternalBridge)._advancements_fullscreen_getWasHorizontallyScrolled()

// Behind the scenes, Lapis creates a wrapper for intercepted operations.
// This allows you to destructure arguments and use clean named parameters!
class _AdvancementTab_scroll_Call(
    val _lapis_receiver: AdvancementTab,
    val _argument_scrollX: Double,
    val _argument_scrollY: Double,
    val _lapis_operation: Operation<Void>,
) : Lapis.Call<_AdvancementTab.scroll>

inline val Lapis.Call<_AdvancementTab.scroll>.scrollX: Double
get() = (this as _AdvancementTab_scroll_Call)._argument_scrollX

inline val Lapis.Call<_AdvancementTab.scroll>.scrollY: Double
get() = (this as _AdvancementTab_scroll_Call)._argument_scrollY

inline fun Lapis.Call<_AdvancementTab.scroll>.getReceiver(): AdvancementTab =
    (this as _AdvancementTab_scroll_Call)._lapis_receiver

// Default arguments allow you to pass only those arguments that you need to change.
// 'operator invoke' allow you to call it as a function: `original(...)`
inline operator fun Lapis.Call<_AdvancementTab.scroll>.invoke(
    scrollX: Double = this.scrollX,
    scrollY: Double = this.scrollY,
) {
    (this as _AdvancementTab_scroll_Call)._lapis_operation.call(getReceiver(), scrollX, scrollY)
}

// If you need to call the intercepted method on another instance (receiver object), you can use it like:
// with(anotherTab) { original.call(...) }
context(_receiver: AdvancementTab)
inline fun Lapis.Call<_AdvancementTab.scroll>.call(
    scrollX: Double = this.scrollX,
    scrollY: Double = this.scrollY,
) {
    (this as _AdvancementTab_scroll_Call)._lapis_operation.call(_receiver, scrollX, scrollY)
}
```

### The Escape Hatch?

High-level hooks are awesome, but they shouldn't trap you. Lapis gives you a zero-compromise "escape hatch" — if you
need a specific, low-level, or custom Mixin feature, you can completely bypass the KSP magic and write raw Mixin code.

Lapis will detect standard Mixin/MixinExtras annotations and copy them to Java exactly as they are, acting as a smart,
transparent proxy:

* **Raw Mixins & Shadows**: Drop a standard `@Mixin` right next to your `@Patch`, or use the original `@Shadow` on
  functions and fields right next to your `@KShadow` declarations inside the same class. Lapis will skip its automagic
  and let them pass through unmodified!

* **Raw Injectors**: Need a classic `@Inject`, `@Redirect`, etc.? Write them directly next to your high-level hooks.

#### Type-Safe Strings via `@LapisDesc` (The Hybrid Way)

Even inside the raw escape hatch, you don't have to suffer from string descriptors. You can link your Kotlin schemas to
raw string parameters using `@LapisDesc` with a built-in parameter whitelist (`"field"`, `"method"`, `"target"`):

```kotlin
@Inject(
    method = ["extractRender"], // Looks like a raw string, but...
    at = [At(value = "INVOKE", target = "addTitle")]
)
@LapisDesc<_AdvancementsScreen.extractRenderState>("extractRender") // ...Lapis swaps it safely!
@LapisDesc<_HeaderAndFooterLayout.addTitleHeader>("addTitle")
fun drawBackground(graphics: GuiGraphicsExtractor?, ci: CallbackInfo?) {
}
```

---

## Why Lapis?

* **100% Kotlin in your mods** — even if you absolutely need Mixins!
* **Focus on Logic, Not Bytecode** — stop overthinking how Mixins work under the hood and how to follow their countless
  quirks. Leave that to Lapis, and just write your code!
* **Feature-based structure** — since patches aren't the entry point for Mixin engine, they don't need to be isolated in
  a separate package, and you can store them right next to your regular classes!
* **Single Point of Maintenance** — if Mojang or mappings update a method signature, you only change it *once* in your
  Schema. Your actual Patches don't even need to be touched!
* **Zero Infrastructure Pain** — Lapis automatically generates AW/AT configurations and your `mixin.json` behind the
  scenes.
* **Pure Compile-Time Protection** — Lapis scans the target bytecode during compilation to verify that the instruction
  actually exists. No more runtime surprises!

---

## The Core Philosophy: Shifting Pain to the Compiler

### Why do standard Mixins hurt?

* **No Single Source of Truth (SSOT)**: Cryptic strings like `Lnet/minecraft/class_...;(IIIIDDZ)V` are a ticking time
  bomb. Relying on raw, unchecked strings for critical injection points means your code completely lacks type safety.
  Because there is no SSOT to connect your mod with the game code, you are forced to duplicate the exact same signature
  multiple times—in string descriptors, injection parameters, shadow methods, and Access Widener config files. When a
  single method changes in a minor game update, you have to run across the entire codebase, manually fixing that same
  signature in a dozen different places. Given how frequently Mojang releases snapshots nowadays and how boldly they
  rewrite their code, this is no longer something you can ignore.
* **MCDev is programming-time, not compile-time**: MCDev won’t help you with long-term maintenance; it just helps you
  dig your own grave without pain at the very beginning. After that, it is completely useless—you end up manually
  erasing long cryptic strings just to type new ones and fix injection parameters. It creates a dangerous illusion of
  safety. MCDev simply highlights errors in red, but it stays quiet and never stops the build. The compiler will still
  happily build your mod with typos, and you waste your time building and launching the game only to watch it crash.
* **Mixin is runtime tool, not compile-time**: Mixin operates entirely at runtime, meaning its validation and execution
  happen during class loading rather than when you compile your project. Its complex annotations were designed by
  bytecode experts who optimized for raw low-level performance, completely ignoring user-facing developer experience
  (DevEx). They built the system with the assumption that every modder should intimately understand bytecode internals
  just to use it.
* **High Cognitive Load & Micro-Decisions**: Writing Mixins forces you to waste your mental energy on pointless
  boilerplate instead of your actual mod logic. You are forced to come up with meaningful names for dozens of injection
  parameters you don't even care about. You constantly overthink the internal layout: should you sort @Unique, @Shadow,
  and injection methods in a specific order? Are your methods perfectly synced with your extension interface? What is
  better here—a double cast or inheriting the mixin from a target's subclass? Instead of building features, your brain
  is entirely hijacked by fixing, clean-coding, and micromanaging boilerplate that shouldn't even exist in the first
  place.
* **Too much freedom**: Without modern abstractions, raw and unrestricted access turns flexibility into an architectural
  mess. Combined with the tool's low-level nature, it pushes the industry toward "optimization for the sake of
  optimization". Instead of focusing on future maintainability, developers obsess over writing hyperefficient
  injections, completely sacrificing code quality just to count classes and save a few kilobytes.
* **Decision Fatigue & "The Holy Grail" Cult**: The community treats raw Mixins like a sacred holy grail simply because
  everyone is used to working directly with the "metal". Since nobody bothered to build a high-level tool to hide this
  complexity, it became a cargo cult. I constantly see people in Discord asking, "Hey, how do I inject my logic here?"
  Instead of getting a straightforward, ready-to-use answer, they are forced to learn how bytecode works under the hood.
  It happens every single time—seriously, how do they not get tired of explaining this over and over?
* **Lol, it’s Java after all**: Given everything above, this cargo cult is exactly what keeps the modding industry
  trapped in Java. Instead of moving forward, everyone continues to write Mixins in Java simply because "Minecraft is
  written in Java." This mind-set completely blocks any evolution, forcing developers to stick to outdated, verbose
  paradigms just because it's what they’ve always done.

### The Spark

MixinExtras completely revolutionized the modding ecosystem by proving that injections can be elegant and conflict-safe.
Lapis wouldn't exist without it! To honor this foundation, Lapis uses MixinExtras as its primary backend for hooks,
acting as a high-level, intent-based layer on top of its robust architecture.

### The Lapis Cure

Lapis bridges the gap between low-level bytecode and expressive Kotlin by moving all the complexity from your head
straight to the compiler.

* **Compile-Time Armor**: No more runtime crashes due to typos in strings. If it compiles, it actually works.
* **Intent-Based DSL**: Focus on *what* you want to change, not *how* to construct the bytecode instruction.
* **Boilerplate Exterminator**: Lapis completely automates Interface Injections, Extension properties, and AW/AT
  generation behind the scenes.
* **Built-in Sanity**: Conflict-free injections powered by MixinExtras best practices out of the box.

### 📊 The Upgrade

| Feature          | Standard Mixin                | Lapis                             |
|:-----------------|:------------------------------|:----------------------------------|
| **Descriptors**  | Cryptic Strings               | Type-safe Kotlin References       |
| **Refactoring**  | Update in 2-5 places manually | Update in one single place        |
| **Safety**       | Surprises at runtime          | Hard errors at compile time       |
| **Kotlin Vibes** | Clunky Java-first feel        | Native DSL & Extension properties |

---

## 🚀 Quick Start

> [!NOTE]
> A brief guide on connecting the KSP plugin will be added here shortly. The full documentation and comprehensive Wiki
> will be available with the **1.0.0** release.
>
> You can find the current documentation in our [Wiki →](https://github.com/recrafter/lapis/wiki).

---

## 📄 License

This project is licensed under the [MIT License](https://spdx.org/licenses/MIT).
