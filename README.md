# Lapis

A Kotlin Symbol Processor (KSP) for Sponge Mixins. Built exclusively for Minecraft modding, Lapis focuses on intent-based injections and compile-time safety. It provides a Kotlin-first frontend with a type-safe DSL, leverages a MixinExtras-based backend, and automates the generation of Mixin and AW/AT configurations.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.recrafter/lapis.svg?label=Maven+Central&style=for-the-badge)](https://central.sonatype.com/artifact/io.github.recrafter/lapis) [![License: MIT](https://img.shields.io/static/v1?label=License&style=for-the-badge&message=MIT&color=yellow)](https://spdx.org/licenses/MIT)

---

## Problem

[Mixin](https://github.com/spongepowered/Mixin) is a powerful tool that enables on-the-fly code modification at runtime;
however, it was designed for the broader Java ecosystem, **not just** Minecraft modding. As a result, it provides a
fairly low-level interface that relies on imperative logic and implementation-heavy annotations.

**Redundancy & Maintenance Hell**: Modding for years with standard Mixins reveals a pattern of constant duplication. A
single logic change often requires updating method descriptors in multiple places: the injection point, the parameter
list, Shadow methods, Accessors, and AW/AT configurations. This manual synchronization is fragile; missing a single
descriptor during a version migration or mapping update leads to a broken mod.

**The "Descriptor" Nightmare**: Relying on long, cryptic strings (like `Lnet/minecraft/class_...;()V`) makes code
unreadable and error-prone. While IDE plugins help generate these, they only provide "coding-time" assistance. They
don't prevent the project from building successfully even if a descriptor is wrong, leading to frustrating runtime
crashes that only appear after the mod is deployed.

**Decision Fatigue**: There is too much "freedom of choice" in how to achieve the same result. Whether it's choosing
between an Accessor or an AW, or implementing a common pattern like Interface Injection to expose Mixin logic,
developers often end up copy-pasting the same boilerplate or "reinventing the wheel".

## Inspiration

[MixinExtras](https://github.com/LlamaLad7/MixinExtras) revolutionized the ecosystem by bringing Minecraft-specific
modding realities into the Mixin world. It introduced conflict-safe injections while maintaining strict compatibility
with the original Mixin framework.

Seeing the elegance of MixinExtras was my primary inspiration; it revealed the true potential of what modern injections
could look like. Lapis wouldn't exist without it. To honor this foundation, I chose MixinExtras as the primary backend
for my code generation, aiming to provide a high-level, intent-based layer on top of its robust architecture.

## Solution

Lapis is the result of moving the complexity from the developer's head to the compiler. It bridges the gap between
low-level Mixins and expressive Kotlin.

### Key Features

- **Compile-time Safety**: No more runtime crashes due to typos in descriptors. If it builds, it works.
- **Intent-Based DSL**: Write what you want to change, not how to find the bytecode instruction.
- **Automatic Boilerplate**: Lapis handles Interface Injections, Extension properties, and AW/AT generation for you.
- **Built-in Best Practices**: Optimized for conflict-free injections using MixinExtras by default.

| Feature          | Standard Mixin       | Lapis                   |
|:-----------------|:---------------------|:------------------------|
| **Descriptors**  | Strings              | Type-safe references    |
| **Maintenance**  | Update in 2-5 places | Update in one place     |
| **Safety**       | Runtime crashes      | Compile-time errors     |
| **Kotlin-first** | No                   | Native DSL & Extensions |

---

## Quick Start

### Schemas

Schemas describe the target environment once. They support static members, custom bytecode mappings, and recursive nesting to target anonymous or local classes effortlessly.

```kotlin
@Schema("net.minecraft.client.gui.screens.advancements.AdvancementsScreen")
object _AdvancementsScreen {
    @Static object WINDOW_INSIDE_X : Lapis.Field<Int>
    object tabs : Lapis.Field<Map<AdvancementHolder, AdvancementTab>>

    @MappingName("repositionElements")
    object updateUI : Lapis.Method<() -> Unit>

    // Targeting an anonymous class (e.g., RandomState$1)
    @AnonymousSchema(1, delegate = DensityFunction.Visitor::class)
    object NoiseFlattener {
        object newInstance : Lapis.Constructor<(RandomState) -> Unit>
    }

    // Targeting a local class inside a method
    @LocalSchema(1, "NoiseWiringHelper", delegate = DensityFunction.Visitor::class)
    object NoiseWiringHelper {
        object visitNoise : Lapis.Method<(DensityFunction.NoiseHolder) -> DensityFunction.NoiseHolder>
    }
}
```

### Patches

A Patch is a Kotlin class linked to a Schema. Lapis handles all the heavy lifting—bridges, wrappers, and type-safe delegates—keeping your logic clean.

```kotlin
@Patch(_AdvancementsScreen::class, Side.ClientOnly)
abstract class AdvancementsScreenPatch(@Origin val screen: AdvancementsScreen) {

    // Example: Inverting scroll direction when Shift is pressed
    @Hook(_AdvancementsScreen.mouseScrolled::class, At.Call)
    @AtCall(_AdvancementTab.scroll::class, ordinal = [0])
    fun invertScroll(@Origin original: Lapis.Call<_AdvancementTab.scroll>) {
        if (Minecraft.getInstance().hasShiftDown()) {
            // Invoke ORIGINAL logic with modified, type-safe arguments
            original(scrollX = original.scrollY, scrollY = 0.toDouble())
        } else {
            original()
        }
    }

    // Example: Patching an anonymous class defined in the Schema
    @Patch(_AdvancementsScreen.NoiseFlattener::class)
    abstract class NoiseFlattenerPatch {
        @Hook(_AdvancementsScreen.NoiseFlattener.newInstance::class, At.Tail)
        fun onInit() {
            // Your logic here
        }
    }
}
```

### Generated Examples

```java
@Mixin(
        targets = {"net.minecraft.client.gui.screens.advancements.AdvancementWidget"}
)
public class io_github_diskria_advancements_fullscreen_client_patch_AdvancementWidgetPatch_Mixin {
    @Unique
    private AdvancementWidgetPatch _lapis_patch;

    @Unique
    private AdvancementWidgetPatch _lapis_getOrInitPatch() {
        if (_lapis_patch == null) {
            _lapis_patch = new io_github_diskria_advancements_fullscreen_client_patch_AdvancementWidgetPatch_Impl((AdvancementWidget) (Object) this);
        }
        return _lapis_patch;
    }

    @ModifyVariable(
            method = {"extractHover(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIFII)V"},
            name = {"topSide"},
            at = @At(value = "STORE", ordinal = 0, unsafe = true)
    )
    private boolean fixHoverOutOfScreen_ordinal0(boolean _lapis_value,
            @Local(name = {"titleTop"}) int _local_titleTop,
            @Local(name = {"titleBarBottom"}) int _local_titleBarBottom,
            @Local(name = {"descriptionTextHeight"}) int _local_descriptionTextHeight,
            @Local(name = {"descriptionHeight"}) int _local_descriptionHeight) {
        return _lapis_getOrInitPatch().fixHoverOutOfScreen(_local_titleTop, _local_titleBarBottom, _local_descriptionTextHeight, _local_descriptionHeight);
    }
}
```

> [!NOTE]
> A brief guide on connecting the KSP plugin will be added here shortly. The full documentation and comprehensive Wiki
> will be available with the **1.0.0** release.
>
> You can find the current documentation in our [Wiki →](https://github.com/recrafter/lapis/wiki).

---

## License

This project is licensed under the [MIT License](https://spdx.org/licenses/MIT).
