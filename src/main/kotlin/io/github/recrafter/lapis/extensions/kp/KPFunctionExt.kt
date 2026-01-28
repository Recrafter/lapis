package io.github.recrafter.lapis.extensions.kp

import com.squareup.kotlinpoet.UNIT
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.IrTypeName

fun KPFunctionBuilder.setReturnType(type: IrTypeName?) {
    returns(type?.kotlin ?: UNIT)
}

fun KPFunctionBuilder.setReceiverType(type: IrTypeName) {
    receiver(type.kotlin)
}

fun KPFunctionBuilder.addParameters(parameters: List<IrParameter>) {
    parameters.forEach { parameter ->
        addParameter(parameter.name, parameter.type.kotlin)
    }
}
