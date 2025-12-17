package io.github.recrafter.nametag.accessors.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class OpenProperty(
    val target: String = "",
    val isStatic: Boolean = false,
)
