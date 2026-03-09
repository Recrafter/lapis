package io.github.recrafter.lapis.layers.lowering.types

import io.github.recrafter.lapis.extensions.jp.JPVariableType
import io.github.recrafter.lapis.extensions.kp.KPVariableType
import io.github.recrafter.lapis.layers.lowering.asIr

class IrVariableType(override val kotlin: KPVariableType) : IrType(kotlin) {

    override val java: JPVariableType by lazy {
        JPVariableType.get(kotlin.name, *kotlin.bounds.map { it.asIr().java }.toTypedArray())
    }

    companion object {
        fun of(name: String, vararg bounds: IrType): IrVariableType =
            KPVariableType(name, bounds.map { it.kotlin }).asIr()
    }
}
