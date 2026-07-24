# Lapis

A Kotlin Symbol Processor (KSP) for Sponge Mixins. Built exclusively for Minecraft modding, Lapis focuses on intent-based injections and compile-time safety. It provides a Kotlin-first frontend with a type-safe DSL, leverages a MixinExtras-based backend, and automates the generation of Mixin and AW/AT configurations.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.recrafter/lapis.svg?label=Maven+Central&style=for-the-badge)](https://central.sonatype.com/artifact/io.github.recrafter/lapis) [![License: MIT](https://img.shields.io/static/v1?label=License&style=for-the-badge&message=MIT&color=yellow)](https://spdx.org/licenses/MIT)

---

## 🚀 Features & Magic: Type-Safe Mixins Without Brain Damage

Writing raw Java Mixins can be a nightmare. You are constantly copying long JVM descriptors, fighting with mapping updates, and crying when everything silently breaks in runtime. 

Meet **Lapis** — a framework that turns Mixins into a beautifully structured, type-safe paradise using Kotlin Schemas and KSP under the hood.

### 1. Define Your Schemas (Type-Safe Blueprint)
First, map your target classes and methods using simple Kotlin objects. No raw strings, just pure autocomplete-friendly definitions:

```kotlin
@Class(AdvancementsScreen::class, side = Side.ClientOnly)
object _AdvancementsScreen {
    @Method<(x: Double, y: Double, dx: Double, dy: Double) -> Boolean>
    object mouseScrolled
}

@Class(AdvancementTab::class, side = Side.ClientOnly)
object _AdvancementTab {
    @Method<(scrollX: Double, scrollY: Double) -> Unit>
    object scroll
}
```

### 2. Write Your Patch (Pure Kotlin, Zero Boilerplate)
Now, write your logic using high-level abstractions like `@Hook`. Want to redirect or modify an invoke call? Just pass your schema directly into the generic slot!

```kotlin
@Patch(_AdvancementsScreen::class, side = Side.ClientOnly)
abstract class AdvancementsScreenPatch(@Origin val screen: AdvancementsScreen) {

    // Forget about Mixin shadow rules, static fields, or member types. 
    // Just copy-paste modifiers from the game code. In your patch, it's ALWAYS an abstract property or function.
    @KShadow(Modifier.PRIVATE, Modifier.FINAL)
    abstract val tabs: Map<AdvancementHolder, AdvancementTab>

    // Forget about manual interfaces, unique fields, or clunky Interface Injection.
    // Just drop @Extension on any property or function, and it seamlessly becomes a part of the game class!
    @Extension
    var wasHorizontallyScrolled: Boolean = false

    // Inside 'mouseScrolled', we intercept the call to 'scroll' on the advancement tab.
    @Hook<_AdvancementsScreen.mouseScrolled>(Ats.Call)
    @AtCall<_AdvancementTab.scroll>(ordinal = [0])
    fun invertScrollWhenShiftDown(@Origin original: Lapis.Call<_AdvancementTab.scroll>) {
        if (Minecraft.getInstance().hasShiftDown()) {
            wasHorizontallyScrolled = true
            // Intercept and swap vanilla behavior with our custom arguments
            original(scrollX = original.scrollY, scrollY = 0.toDouble())
        } else {
            wasHorizontallyScrolled = false
            original() // Invoke default vanilla behavior unchanged!
        }
    }
}
```

### 3. Sit Back and Relax (What Lapis Generates for You)
KSP takes your clean Kotlin patch, automatically resolves descriptors, manages bridges/extensions, wraps original operations, and spits out a 100% compliant, fully optimized Java Mixin:

```java
@Mixin(
        targets = {"net/minecraft/client/gui/screens/advancements/AdvancementsScreen"}
)
public abstract class AdvancementsScreenPatch_Mixin implements AdvancementsScreenPatch_ExternalBridge, AdvancementsScreenPatch_InternalBridge {

    @Unique
    private AdvancementsScreenPatch_Impl _lapis_patch;

    @Final
    @Shadow
    private Map<AdvancementHolder, AdvancementTab> tabs;

    @Unique
    private AdvancementsScreenPatch_Impl _lapis_getOrInitPatch() {
        if (_lapis_patch == null) {
            _lapis_patch = new AdvancementsScreenPatch_Impl((AdvancementsScreen) (Object) this, this);
        }
        return _lapis_patch;
    }

    @Override
    public boolean _advancements_fullscreen_getWasHorizontallyScrolled() {
        return _lapis_getOrInitPatch().getWasHorizontallyScrolled();
    }

    @Override
    public Map<AdvancementHolder, AdvancementTab> _advancements_fullscreen_getTabs() {
        return tabs;
    }

    @WrapOperation(
            method = {"mouseScrolled(DDDD)Z"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementTab;scroll(DD)V", ordinal = 0, unsafe = true)}
    )
    private void invertScrollWhenShiftDown_ordinal0(AdvancementTab _lapis_receiver,
            double _argument_scrollX, double _argument_scrollY, Operation<Void> _lapis_original) {
        _lapis_getOrInitPatch().invertScrollWhenShiftDown(new _AdvancementTab_scroll_Call(_lapis_receiver, _argument_scrollX, _argument_scrollY, _lapis_original));
    }
}
```

### 🪟 The Ultimate Escape Hatch: Raw Mixin Power

High-level hooks are awesome, but they shouldn't trap you. Lapis gives you a zero-compromise "escape hatch" — if you need a specific, low-level, or custom Mixin feature, you can completely bypass the KSP magic and write raw Mixin code. 

Lapis will detect standard Mixin/MixinExtras annotations and copy them to Java exactly as they are, acting as a smart, transparent proxy!

* **Raw Mixins & Shadows**: Drop a standard `@Mixin` right next to your `@Patch`, or use the original `@Shadow` on functions and fields right next to your `@KShadow` declarations inside the same class. Lapis will skip its automagic and let them pass through unmodified.
* **Raw Injectors**: Need a classic `@Inject`, `@Redirect`, `@ModifyConstant`, `@WrapOperation`, etc.? Write them directly next to your high-level hooks. They will be detected and copied line-for-line.

#### 🧠 Type-Safe Strings via `@LapisDesc` (The Hybrid Way)
Even inside the raw escape hatch, you don't have to suffer from string descriptors. You can link your Kotlin schemas to raw string parameters using `@LapisDesc` with a built-in parameter whitelist (`"method"`, `"target"`, `"field"`):

```kotlin
@Inject(
    method = ["extractRender"], // Looks like a raw string, but...
    at = [At(value = "INVOKE", target = "addTitle", shift = At.Shift.AFTER)]
)
@LapisDesc<_AdvancementsScreen.extractRenderState>("extractRender") // ...Lapis swaps it safely!
@LapisDesc<_HeaderAndFooterLayout.addTitleHeader>("addTitle")
fun drawBackground(graphics: GuiGraphicsExtractor?, ci: CallbackInfo?) {}
```

* Lapis safely ignores metadata keys like `id` or `value = "INVOKE"`, but swaps your custom aliases with exact, refactor-safe JVM descriptors at compile time. You get 100% of the raw Mixin flexibility with 0% of the string-typo anxiety!

---

## ✨ Why Lapis is Awesome

* **100% Kotlin in your mods** — even if you absolutely need Mixins!
* **Pure Compile-Time Protection** — Lapis scans the target bytecode during compilation to verify that the instruction actually exists. No more runtime surprises!
* **Single Point of Maintenance** — if Mojang or mappings update a method signature, you only change it *once* in your Schema. Your actual Patches don't even need to be touched!
* **Zero Infrastructure Pain** — Lapis automatically generates AW/AT configurations and your `mixin.json` behind the scenes.
* **Focus on Logic, Not Bytecode** — stop overthinking how Mixins work under the hood and how to follow their countless quirks. Leave that to Lapis, and just write your code!

---

## 🛑 The Core Philosophy: Shifting Pain to the Compiler

### Why do standard Mixins hurt?
* **Maintenance Hell**: A single target change forces you to sync descriptors in 5 different places. Miss one? Enjoy your broken mod.
* **String Nightmares**: Cryptic strings like `Lnet/minecraft/class_...;()V` are a ticking time bomb. While tools like the **MCDev** plugin make *writing* them easier at first, long-term maintenance still suffers. The tooling is built around an outdated tech stack, meaning the Kotlin compiler will still happily build your mod with a typo, leaving you to chase runtime crashes after deployment.
* **Decision Fatigue & "The Holy Grail" Cult**: Too much freedom, yet zero modern abstractions. The modding community treats raw Mixins like some sacred holy grail. I constantly see people in modloader Discords asking, *"Hey, how do I inject my logic here?"*, and instead of getting a clean, intent-based answer, they are forced to learn the "secret layout" of low-level bytecode interception. Mixins are visually overcomplicated by a cryptic set of annotations, and historically, nobody built a proper abstraction layer over them. The engine developers decide how the annotations look, completely ignoring the modder’s DevEx, while developers end up reinventing the wheel and copy-pasting the same clunky boilerplate between different mods and examples.
* **Lol, it’s Java after all**: At the end of the day, you are forced to write clunky, verbose, Java-centric boilerplate inside your beautiful, expressive Kotlin codebase just to make the Mixin engine happy.

### The Spark ⚡
MixinExtras completely revolutionized the modding ecosystem by proving that injections can be elegant and conflict-safe. Lapis wouldn't exist without it! To honor this foundation, Lapis uses MixinExtras as its primary backend, acting as a high-level, intent-based layer on top of its robust architecture.

### The Lapis Cure 💎
Lapis bridges the gap between low-level bytecode and expressive Kotlin by moving all the complexity from your head straight to the compiler.

* **Compile-Time Armor**: No more runtime crashes due to typos in strings. If it compiles, it actually works.
* **Intent-Based DSL**: Focus on *what* you want to change, not *how* to construct the bytecode instruction.
* **Boilerplate Exterminator**: Lapis completely automates Interface Injections, Extension properties, and AW/AT generation behind the scenes.
* **Built-in Sanity**: Conflict-free injections powered by MixinExtras best practices out of the box.

### 📊 The Upgrade

| Feature | Standard Mixin | Lapis |
| :--- | :--- | :--- |
| **Descriptors** | Cryptic Strings | Type-safe Kotlin References |
| **Refactoring** | Update in 2-5 places manually | Update in one single place |
| **Safety** | Surprises at runtime | Hard errors at compile time |
| **Kotlin Vibes** | Clunky Java-first feel | Native DSL & Extension properties |

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
