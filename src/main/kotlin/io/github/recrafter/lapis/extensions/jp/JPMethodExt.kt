package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.layers.lowering.IrJavaCodeBlockBuilder
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.IrTypeName

inline fun <reified A : Annotation> JPMethodBuilder.addAnnotation(builder: JPAnnotationBuilder.() -> Unit = {}) {
    addAnnotation(buildJavaAnnotation<A>(builder))
}

fun JPMethodBuilder.addIfStatement(condition: JPCodeBlock, body: IrJavaCodeBlockBuilder.() -> Unit) {
    withControlFlow(buildJavaCodeBlock("if (%L)") { arg(condition) }, body)
}

fun JPMethodBuilder.withControlFlow(controlFlow: JPCodeBlock, body: IrJavaCodeBlockBuilder.() -> Unit) {
    beginControlFlow(controlFlow)
    addStatement(buildJavaCodeBlock(body))
    endControlFlow()
}

fun JPMethodBuilder.setReturnType(type: IrTypeName?) {
    returns(type?.java ?: JPTypeName.VOID)
}

fun JPMethodBuilder.addParameters(parameters: List<IrParameter>) {
    parameters.forEach { parameter ->
        addParameter(parameter.type.java, parameter.name)
    }
}
