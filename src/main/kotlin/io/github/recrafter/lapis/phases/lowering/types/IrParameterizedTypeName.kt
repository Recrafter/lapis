package io.github.recrafter.lapis.phases.lowering.types

import io.github.recrafter.lapis.extensions.jp.JPParameterizedTypeName
import io.github.recrafter.lapis.extensions.kp.KPParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.asIr
import io.github.recrafter.lapis.phases.lowering.asIrTypeName

class IrParameterizedTypeName(override val kotlin: KPParameterizedTypeName) : IrTypeName(kotlin) {

    override val java: JPParameterizedTypeName by lazy {
        JPParameterizedTypeName.get(
            kotlin.rawType.asIr().java,
            *kotlin.typeArguments.map { it.asIrTypeName().box().java }.toTypedArray(),
        )
    }
}
