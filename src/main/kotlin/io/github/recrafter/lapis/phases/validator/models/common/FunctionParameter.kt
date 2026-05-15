package io.github.recrafter.lapis.phases.validator.models.common

import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter

class FunctionParameter(val name: String, private val type: KSType) {
    fun asIrParameter(): IrParameter = IrParameter(name, type.asIrTypeName())
}
