package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.extensions.kp.KPProperty
import io.github.recrafter.lapis.extensions.kp.buildKotlinProperty
import io.github.recrafter.lapis.phases.lowering.IrVisibilityModifier
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

open class IrParameter(
    val name: String,
    val typeName: IrTypeName,
)

class IrSetterParameter(
    typeName: IrTypeName,
) : IrParameter("newValue", typeName)

fun IrParameter.toKotlinConstructorProperty(
    visibility: IrVisibilityModifier = IrVisibilityModifier.PUBLIC
): KPProperty =
    buildKotlinProperty(name, typeName, visibility = visibility) {
        initializer(name)
    }

val List<IrParameter>.format: String
    get() = joinToString { "%N" }
