package io.github.recrafter.lapis.extensions.kp

import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.lowering.IrModifier
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

fun KPPropertyBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(KPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(KPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(KPModifier.ABSTRACT)
            IrModifier.OVERRIDE -> addModifiers(KPModifier.OVERRIDE)
            IrModifier.FINAL -> addModifiers(KPModifier.FINAL)
            else -> lapisError("Modifier ${it.name.quoted()} is not applicable to Kotlin properties")
        }
    }
}
