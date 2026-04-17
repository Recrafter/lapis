package io.github.recrafter.lapis.phases.builtins

sealed interface Builtin<T> {

    val name: String
    val isInternal: Boolean

    fun generate(typer: BuiltinTyper): T

    companion object {
        val entries: List<Builtin<*>> by lazy {
            TypeAliasBuiltin.entries +
                SimpleBuiltin.entries +
                DescriptorBuiltin.entries +
                LocalVarBuiltin.entries
        }
    }
}
