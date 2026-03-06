package io.github.recrafter.lapis.layers.lowering.types

import io.github.recrafter.lapis.extensions.jp.JPTypeVariableName
import io.github.recrafter.lapis.extensions.kp.KPTypeVariableName
import io.github.recrafter.lapis.layers.lowering.asIr

class IrTypeVariable(override val kotlin: KPTypeVariableName) : IrTypeName(kotlin) {

    override val java: JPTypeVariableName by lazy {
        JPTypeVariableName.get(kotlin.name, *kotlin.bounds.map { it.asIr().java }.toTypedArray())
    }

    companion object {
        fun of(name: String, vararg bounds: IrTypeName): IrTypeVariable =
            KPTypeVariableName(name, bounds.map { it.kotlin }).asIr()
    }
}
