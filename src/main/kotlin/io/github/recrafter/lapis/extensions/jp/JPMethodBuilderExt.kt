package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrJavaMethodBody
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

inline fun <reified A : Annotation> JPMethodBuilder.addAnnotation(builder: Builder<JPAnnotationBuilder> = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun JPMethodBuilder.setBody(builder: Builder<IrJavaMethodBody> = {}) {
    IrJavaMethodBody(this).builder()
}

fun JPMethodBuilder.setReturnType(typeName: IrTypeName?) {
    returns(typeName?.java.orVoid())
}

fun JPMethodBuilder.addParameter(parameter: IrParameter): JPParameter =
    JPParameter
        .builder(parameter.typeName.java, parameter.name)
        .build()
        .also(::addParameter)

fun JPMethodBuilder.setParameters(parameters: List<IrParameter>): List<JPParameter> =
    parameters.map(::addParameter)

fun JPMethodBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(JPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(JPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(JPModifier.ABSTRACT)
            IrModifier.STATIC -> addModifiers(JPModifier.STATIC)
            IrModifier.OVERRIDE -> addAnnotation<Override>()
            IrModifier.FINAL -> addModifiers(JPModifier.FINAL)
            else -> lapisError("Modifier ${it.name.quoted()} is not applicable to Java methods")
        }
    }
}

fun JPMethodBuilder.setModifiers(modifiers: List<IrModifier>) {
    setModifiers(*modifiers.toTypedArray())
}
