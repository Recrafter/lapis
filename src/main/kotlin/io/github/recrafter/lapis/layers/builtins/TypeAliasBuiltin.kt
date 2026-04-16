package io.github.recrafter.lapis.layers.builtins

import io.github.recrafter.lapis.extensions.kp.KPStar
import io.github.recrafter.lapis.extensions.kp.KPTypeAlias
import io.github.recrafter.lapis.extensions.kp.buildKotlinTypeAlias
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import kotlin.reflect.KFunction

enum class TypeAliasBuiltin : Builtin<KPTypeAlias> {

    AutoLambda {
        override fun getActualTypeName(typer: BuiltinTyper): IrTypeName =
            KFunction::class.asIr().parameterizedBy(KPStar.asIr())
    };

    override val isInternal: Boolean = false

    protected abstract fun getActualTypeName(typer: BuiltinTyper): IrTypeName

    override fun generate(typer: BuiltinTyper): KPTypeAlias =
        buildKotlinTypeAlias(name, getActualTypeName(typer))
}
