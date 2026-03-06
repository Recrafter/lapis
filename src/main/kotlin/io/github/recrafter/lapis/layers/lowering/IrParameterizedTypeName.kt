package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.extensions.jp.JPParameterizedTypeName
import io.github.recrafter.lapis.extensions.kp.KPParameterizedTypeName
import kotlin.collections.get
import kotlin.jvm.java

class IrParameterizedTypeName(override val kotlin: KPParameterizedTypeName) : IrTypeName(kotlin) {

    override val java: JPParameterizedTypeName by lazy {
        JPParameterizedTypeName.get(
            kotlin.rawType.asIr().java,
            *kotlin.typeArguments.map { it.asIr().box().java }.toTypedArray(),
        )
    }
}
