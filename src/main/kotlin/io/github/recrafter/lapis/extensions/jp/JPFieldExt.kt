package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.lowering.IrModifier

inline fun <reified A : Annotation> JPFieldBuilder.addAnnotation(builder: JPAnnotationBuilder.() -> Unit = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun JPFieldBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(JPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(JPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(JPModifier.ABSTRACT)
            IrModifier.STATIC -> addModifiers(JPModifier.STATIC)
            else -> lapisError("Modifier ${it.name.quoted()} is not applicable to Java fields")
        }
    }
}
