package io.github.recrafter.lapis.phases.builtins

import io.github.recrafter.lapis.extensions.kp.KPTypeAlias
import io.github.recrafter.lapis.extensions.kp.buildKotlinTypeAlias
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

enum class TypeAliasBuiltin : Builtin<KPTypeAlias> {

    ;

    override val isInternal: Boolean = false

    protected abstract fun getActualTypeName(resolveBuiltin: BuiltinResolver): IrTypeName

    override fun generate(resolveBuiltin: BuiltinResolver): KPTypeAlias =
        buildKotlinTypeAlias(name, getActualTypeName(resolveBuiltin))
}
