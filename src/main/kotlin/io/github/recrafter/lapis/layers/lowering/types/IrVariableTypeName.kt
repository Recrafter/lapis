package io.github.recrafter.lapis.layers.lowering.types

import io.github.recrafter.lapis.extensions.jp.JPVariableType
import io.github.recrafter.lapis.extensions.kp.KPVariableType
import io.github.recrafter.lapis.layers.lowering.asIr

class IrVariableTypeName(override val kotlin: KPVariableType) : IrTypeName(kotlin) {

    override val java: JPVariableType by lazy {
        JPVariableType.get(kotlin.name, *kotlin.bounds.map { it.asIr().java }.toTypedArray())
    }

    companion object {
        fun of(name: String, vararg bounds: IrTypeName): IrVariableTypeName =
            KPVariableType(name, bounds.map { it.kotlin }).asIr()
    }
}
