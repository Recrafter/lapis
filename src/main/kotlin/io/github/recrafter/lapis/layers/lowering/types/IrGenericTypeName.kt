package io.github.recrafter.lapis.layers.lowering.types

import io.github.recrafter.lapis.extensions.jp.JPGenericType
import io.github.recrafter.lapis.extensions.kp.KPGenericType
import io.github.recrafter.lapis.layers.lowering.asIr

class IrGenericTypeName(override val kotlin: KPGenericType) : IrTypeName(kotlin) {

    override val java: JPGenericType by lazy {
        JPGenericType.get(
            kotlin.rawType.asIr().java,
            *kotlin.typeArguments.map { it.asIr().box().java }.toTypedArray(),
        )
    }
}
