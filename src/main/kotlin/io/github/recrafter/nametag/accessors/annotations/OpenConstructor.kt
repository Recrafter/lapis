package io.github.recrafter.nametag.accessors.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class OpenConstructor(val target: String = "<init>")
