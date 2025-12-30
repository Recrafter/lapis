# Nametag

KSP-based annotation library for declarative Mixins in Kotlin.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.recrafter/nametag.svg?label=Maven+Central&style=for-the-badge)](https://central.sonatype.com/artifact/io.github.recrafter/nametag) [![License: MIT](https://img.shields.io/static/v1?label=License&style=for-the-badge&message=MIT&color=yellow)](https://spdx.org/licenses/MIT)

---

## Annotations

### `@KAccessor`

Marks a Kotlin interface as an accessor definition and specifies its Mixin target.

The processor generates:
- a Java Mixin interface with `@Accessor` / `@Invoker` members,  
- a Kotlin factory object,  
- Kotlin extension functions and properties for the target type.

**Parameters**

| Parameter | Type | Description |
|------------|------|-------------|
| `target` | `KClass<*>` | Target class for the accessor. |
| `widener` | `String?` | Optional widener ID for inaccessible classes. |

If the target class is private or package-restricted, specify only the `widener` first and run Gradle sync.  
The widener configuration will be generated automatically; after that, the class can be referenced normally.

**Example**

```kotlin
@KAccessor(
    widener = "net.minecraft.client.MinecraftClient",
    target = MinecraftClient::class
)
interface MinecraftClientAccessor {
    val window: Window
    fun setScreen(screen: Screen)
}
```

---

## Usage Workflow

1. Define a Kotlin interface annotated with `@KAccessor`.  
2. Specify the `target` class and (optionally) a `widener`.  
3. Declare abstract properties and functions for the fields and methods to expose.  
4. Run the Gradle build - Nametag will generate the Mixin interface, Kotlin factory, and extensions.

---

## Nesting and Wideners

Nested accessor interfaces may reference their parent accessor or widener.  
When referencing nested classes, use **simple inner names** instead of fully qualified outer paths.

Example:

```kotlin
@KAccessor(target = Outer::class)
interface OuterAccessor {
    @KAccessor(target = Inner::class)
    interface InnerAccessor
}
```

---

## Generated Output

Generated files are grouped by purpose:

- `mixins/` - generated Java Mixin interfaces  
- `factory/` - Kotlin factory objects for static members  
- `extensions/` - Kotlin extension APIs for instance members  

All outputs follow standard Mixin conventions:
- Java code uses `@Accessor` and `@Invoker`.  
- Kotlin code exposes idiomatic extensions for field and method access.  
- Static members are placed in generated `Factory` objects.

---

## Future Work: Lexicon Layer

Upcoming versions of Nametag will extend beyond accessors.

The **Lexicon** layer will introduce:
- full Mixin authoring directly in Kotlin,  
- aliasing of class, method, and field names,  
- a unified symbolic mapping layer to replace direct reference mappings.

This system will serve as a new abstraction over mappings inside **Recrafter SDK**, allowing complete integration between accessors, mixins, and aliases.

---

## License

This project is licensed under the [MIT License](https://spdx.org/licenses/MIT).
