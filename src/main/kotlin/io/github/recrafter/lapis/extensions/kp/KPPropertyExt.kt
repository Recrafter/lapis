package io.github.recrafter.lapis.extensions.kp

import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget
import io.github.recrafter.lapis.extensions.common.Builder
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

inline fun <reified A : Annotation> KPPropertyBuilder.addAnnotation(
    useSiteTarget: UseSiteTarget? = null,
    builder: Builder<KPAnnotationBuilder> = {}
) {
    addAnnotation(buildKotlinAnnotation<A>(useSiteTarget, builder))
}

fun KPPropertyBuilder.setGetter(builder: Builder<KPFunctionBuilder> = {}) {
    getter(buildKotlinGetter(builder))
}

fun KPPropertyBuilder.setSetter(builder: Builder<KPFunctionBuilder> = {}) {
    mutable(true)
    setter(buildKotlinSetter(builder))
}

fun KPPropertyBuilder.setReceiverType(typeName: IrTypeName) {
    receiver(typeName.kotlin)
}

val List<KPProperty>.format: String
    get() = joinToString { "%N" }
