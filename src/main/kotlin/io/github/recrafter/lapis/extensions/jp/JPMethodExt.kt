package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.generator.builders.IrJavaCodeBlockBuilder
import io.github.recrafter.lapis.layers.generator.builders.IrJavaMethodBodyBuilder
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

inline fun <reified A : Annotation> JPMethodBuilder.addAnnotation(builder: JPAnnotationBuilder.() -> Unit = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun JPMethodBuilder.setBody(builder: IrJavaMethodBodyBuilder.() -> Unit = {}) {
    IrJavaMethodBodyBuilder(this).builder()
}

fun JPMethodBuilder.if_(condition: JPCodeBlock, body: IrJavaCodeBlockBuilder.() -> Unit) {
    withControlFlow(buildJavaCodeBlock("if (%L)") { arg(condition) }, body)
}

fun JPMethodBuilder.try_(
    exceptionClassName: IrClassName,
    catchBody: (IrJavaCodeBlockBuilder.() -> Unit)? = null,
    exceptedName: String = if (catchBody == null) "ignored" else "e",
    finallyBody: (IrJavaCodeBlockBuilder.() -> Unit)? = null,
    tryBody: IrJavaCodeBlockBuilder.() -> Unit,
) {
    beginControlFlow(buildJavaCodeBlock("try"))
    buildJavaCodeBlock(tryBody)
    nextControlFlow(buildJavaCodeBlock("catch (%T %L)") {
        arg(exceptionClassName)
        arg(exceptedName)
    })
    catchBody?.let { buildJavaCodeBlock(it) }
    finallyBody?.let {
        nextControlFlow(buildJavaCodeBlock("finally"))
        buildJavaCodeBlock(it)
    }
    endControlFlow()
}

fun JPMethodBuilder.withControlFlow(controlFlow: JPCodeBlock, body: IrJavaCodeBlockBuilder.() -> Unit) {
    beginControlFlow(controlFlow)
    buildJavaCodeBlock(body)
    endControlFlow()
}

fun JPMethodBuilder.setReturnType(typeName: IrTypeName?) {
    returns(typeName?.java.orVoid())
}

fun JPMethodBuilder.addParameter(parameter: IrParameter): JPParameter =
    JPParameter
        .builder(parameter.typeName.java, parameter.name)
        .build()
        .also { addParameter(it) }

fun JPMethodBuilder.setParameters(parameters: List<IrParameter>): List<JPParameter> =
    parameters.map { addParameter(it) }

fun JPMethodBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(JPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(JPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(JPModifier.ABSTRACT)
            IrModifier.STATIC -> addModifiers(JPModifier.STATIC)
            IrModifier.OVERRIDE -> addAnnotation<Override>()
            else -> lapisError("Modifier ${it.name.quoted()} is not applicable to Java methods")
        }
    }
}
