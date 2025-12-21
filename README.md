# Nametag

KSP-based annotation library for declarative Mixins in Kotlin.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.recrafter/nametag.svg?label=Maven+Central&style=for-the-badge)](https://central.sonatype.com/artifact/io.github.recrafter/nametag) [![License: MIT](https://img.shields.io/static/v1?label=License&style=for-the-badge&message=MIT&color=yellow)](https://spdx.org/licenses/MIT)

---

## Annotations

### `@KAccessor`

Marks a Kotlin interface as an accessor definition and specifies the target class.  
The processor generates a corresponding Java mixin interface and Kotlin extensions for the given targets.

The `widener` parameter is optional.  
If the target class is not accessible (for example, it is private),  
first specify only the `widener` and run **Gradle sync**.  
Crafter will generate the necessary **AW/AT** configuration to widen the class,  
after which the class can be referenced normally in the `target` parameter.

```kotlin
@KAccessor(
    widener = "net.minecraft.client.MinecraftClient",
    target = MinecraftClient::class
)
interface MinecraftClientAccessor {

    @Open("window")
    val window: Window

    @Open("setScreen")
    fun setScreen(screen: Screen)
}
```

### `@Open`

Declares a property or function inside a `@KAccessor` interface that should be opened in the target class.

When used on a property, it generates a Mixin `@Accessor` for the specified field.  
When used on a function, it generates a Mixin `@Invoker` for the specified method.

If the `target` parameter is omitted, the processor infers the target name from the member name  
(similar to the standard Mixin behavior, where `getFoo` maps to the field `foo`).

```kotlin
@Open
val currentScreen: Screen
```

## Usage workflow

1. Declare a Kotlin interface annotated with `@KAccessor`.
2. Specify the `target` class and, if needed, the `widener`.
3. Annotate members with `@Open` to expose fields or methods.
4. Run the Gradle build. Crafter KSP will generate Java Mixin interfaces and Kotlin extensions automatically.

---

## License

This project is licensed under the [MIT License](https://spdx.org/licenses/MIT).
