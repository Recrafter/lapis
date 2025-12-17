package io.github.recrafter.nametag.accessors.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class OpenFunction(
    val target: String = "",
    val isStatic: Boolean = false,
)
