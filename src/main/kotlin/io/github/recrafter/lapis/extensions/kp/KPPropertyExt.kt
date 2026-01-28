package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.layers.lowering.IrTypeName

fun KPPropertyBuilder.setReceiverType(type: IrTypeName) {
    receiver(type.kotlin)
}
