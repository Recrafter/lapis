package io.github.recrafter.lapis.layers.generator.builtins

import com.squareup.kotlinpoet.TypeAliasSpec
import io.github.recrafter.lapis.extensions.kp.KPStar
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import kotlin.reflect.KFunction

enum class TypeAliasBuiltin {
    AutoLambda {
        override fun getType(typer: BuiltinTyper): IrTypeName =
            KFunction::class.asIr().parameterizedBy(KPStar.asIr())
    };

    protected abstract fun getType(typer: BuiltinTyper): IrTypeName

    fun generate(typer: BuiltinTyper): TypeAliasSpec =
        TypeAliasSpec.builder(name, getType(typer).kotlin).build()
}
