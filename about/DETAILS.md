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

> [!NOTE]
> A brief guide on connecting the KSP plugin will be added here shortly. The full documentation and comprehensive Wiki
> will be available with the **1.0.0** release.
>
> You can find the current documentation in our [Wiki →](https://github.com/recrafter/lapis/wiki).
