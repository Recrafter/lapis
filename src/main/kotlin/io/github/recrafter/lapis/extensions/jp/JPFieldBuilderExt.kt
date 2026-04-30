package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.lowering.IrModifier

inline fun <reified A : Annotation> JPFieldBuilder.addAnnotation(builder: Builder<JPAnnotationBuilder> = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun JPFieldBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(JPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(JPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(JPModifier.ABSTRACT)
            IrModifier.STATIC -> addModifiers(JPModifier.STATIC)
            IrModifier.VOLATILE -> addModifiers(JPModifier.VOLATILE)
            IrModifier.FINAL -> addModifiers(JPModifier.FINAL)
            else -> lapisError("Modifier ${it.name.quoted()} is not applicable to Java fields")
        }
    }
}

fun JPFieldBuilder.setModifiers(modifiers: List<IrModifier>) {
    setModifiers(*modifiers.toTypedArray())
}
