package io.github.recrafter.lapis.phases.lowering.types

import io.github.recrafter.lapis.extensions.jp.JPTypeVariableName
import io.github.recrafter.lapis.extensions.kp.KPTypeVariableName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.asIrTypeVariableName

class IrTypeVariableName(override val kotlin: KPTypeVariableName) : IrTypeName(kotlin) {

    override val java: JPTypeVariableName by lazy {
        JPTypeVariableName.get(kotlin.name, *kotlin.bounds.map { it.asIrTypeName().java }.toTypedArray())
    }

    companion object {
        fun of(name: String, vararg bounds: IrTypeName): IrTypeVariableName =
            KPTypeVariableName(name, bounds.map { it.kotlin }).asIrTypeVariableName()
    }
}
