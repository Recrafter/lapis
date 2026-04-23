package io.github.recrafter.lapis.phases.builtins

import io.github.recrafter.lapis.extensions.kp.KPTypeAlias
import io.github.recrafter.lapis.extensions.kp.buildKotlinTypeAlias
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import kotlin.reflect.KFunction

enum class TypeAliasBuiltin : Builtin<KPTypeAlias> {

    AutoLambda {
        override fun getActualTypeName(typer: BuiltinTyper): IrTypeName =
            KFunction::class.asIrClassName().parameterizedByStar()
    };

    override val isInternal: Boolean = false

    protected abstract fun getActualTypeName(typer: BuiltinTyper): IrTypeName

    override fun generate(typer: BuiltinTyper): KPTypeAlias =
        buildKotlinTypeAlias(name, getActualTypeName(typer))
}
