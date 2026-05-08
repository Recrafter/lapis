package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

interface IrReturnable {
    val returnTypeName: IrTypeName?
    val isReturn: Boolean get() = returnTypeName != null
}
