package io.github.recrafter.lapis.layers.builtins

sealed interface Builtin<T> {

    val name: String
    val isInternal: Boolean

    fun generate(typer: BuiltinTyper): T
}
