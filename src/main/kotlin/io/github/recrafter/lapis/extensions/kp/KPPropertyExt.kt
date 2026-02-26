package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.layers.lowering.IrTypeName

inline fun <reified A : Annotation> KPPropertyBuilder.addAnnotation(builder: KPAnnotationBuilder.() -> Unit = {}) {
    addAnnotation(buildKotlinAnnotation<A>(builder))
}

fun KPPropertyBuilder.setGetter(builder: KPFunctionBuilder.() -> Unit = {}) {
    getter(buildKotlinGetter(builder))
}

fun KPPropertyBuilder.setSetter(builder: KPFunctionBuilder.() -> Unit = {}) {
    mutable(true)
    setter(buildKotlinSetter(builder))
}

fun KPPropertyBuilder.setReceiverType(type: IrTypeName) {
    receiver(type.kotlin)
}
