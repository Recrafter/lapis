package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.extensions.singleQuoted
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

inline fun <reified A : Annotation> JPTypeBuilder.addAnnotation(builder: JPAnnotationBuilder.() -> Unit = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun JPTypeBuilder.addSuperInterface(type: IrTypeName) {
    addSuperinterface(type.java)
}

fun JPTypeBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(JPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(JPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(JPModifier.ABSTRACT)
            IrModifier.STATIC -> addModifiers(JPModifier.STATIC)
            IrModifier.OVERRIDE -> error("Modifier ${it.name.singleQuoted()} is not applicable to Java types")
        }
    }
}
