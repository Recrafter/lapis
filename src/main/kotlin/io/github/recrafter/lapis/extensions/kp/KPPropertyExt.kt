package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

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

fun KPPropertyBuilder.setReceiverType(typeName: IrTypeName) {
    receiver(typeName.kotlin)
}

fun KPPropertyBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(KPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(KPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(KPModifier.ABSTRACT)
            IrModifier.STATIC -> addAnnotation<JvmStatic>()
            IrModifier.OVERRIDE -> addModifiers(KPModifier.OVERRIDE)
            else -> lapisError("Modifier ${it.name.quoted()} is not applicable to Kotlin properties")
        }
    }
}
