package io.github.recrafter.lapis.phases.builtins

import io.github.recrafter.lapis.phases.lowering.types.IrClassName

typealias BuiltinResolver = (Builtin<*>) -> IrClassName

sealed interface Builtin<T> {

    val name: String
    val isInternal: Boolean

    fun generate(resolveBuiltin: BuiltinResolver): T

    companion object {
        val entries: List<Builtin<*>> by lazy {
            TypeAliasBuiltin.entries +
                SimpleBuiltin.entries +
                DescriptorWrapperBuiltin.entries +
                LocalVarImplBuiltin.entries
        }
    }
}
