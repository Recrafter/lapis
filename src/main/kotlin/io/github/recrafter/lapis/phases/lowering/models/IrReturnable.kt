package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

interface IrReturnable {
    val returnTypeName: IrTypeName?

    val isReturnable: Boolean
        get() = returnTypeName != null
}
