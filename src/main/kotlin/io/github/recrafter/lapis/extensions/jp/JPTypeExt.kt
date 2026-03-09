package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.types.IrType

inline fun <reified A : Annotation> JPClassBuilder.addAnnotation(builder: JPAnnotationBuilder.() -> Unit = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun JPClassBuilder.addSuperInterface(type: IrType) {
    addSuperinterface(type.java)
}

fun JPClassBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(JPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(JPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(JPModifier.ABSTRACT)
            IrModifier.STATIC -> addModifiers(JPModifier.STATIC)
            else -> lapisError("Modifier ${it.name.quoted()} is not applicable to Java classes")
        }
    }
}
