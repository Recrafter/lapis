package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.extensions.common.asIr
import io.github.recrafter.lapis.layers.generator.IrJavaCodeBlockBuilder
import io.github.recrafter.lapis.layers.generator.IrJavaMethodBodyBuilder
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.IrTypeName
import kotlin.reflect.KClass

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
    exceptionType: KClass<out Exception>,
    catchBody: (IrJavaCodeBlockBuilder.() -> Unit)? = null,
    exceptedName: String = if (catchBody == null) "ignored" else "e",
    finallyBody: (IrJavaCodeBlockBuilder.() -> Unit)? = null,
    tryBody: IrJavaCodeBlockBuilder.() -> Unit,
) {
    beginControlFlow("try")
    buildJavaCodeBlock(tryBody)
    nextControlFlow(buildJavaCodeBlock("catch (%T %L)") {
        arg(exceptionType.asIr())
        arg(exceptedName)
    })
    catchBody?.let { buildJavaCodeBlock(it) }
    finallyBody?.let {
        nextControlFlow("finally")
        buildJavaCodeBlock(it)
    }
    endControlFlow()
}

fun JPMethodBuilder.withControlFlow(controlFlow: JPCodeBlock, body: IrJavaCodeBlockBuilder.() -> Unit) {
    beginControlFlow(controlFlow)
    buildJavaCodeBlock(body)
    endControlFlow()
}

fun JPMethodBuilder.setReturnType(type: IrTypeName?) {
    returns(type?.java.orVoid())
}

fun JPMethodBuilder.addParameter(parameter: IrParameter): JPParameter =
    JPParameter
        .builder(parameter.type.java, parameter.name)
        .build()
        .also { addParameter(it) }

fun JPMethodBuilder.setParameters(parameters: List<IrParameter>): List<JPParameter> =
    parameters.map { addParameter(it) }
