package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.layers.generator.IrKotlinCodeBlockBuilder
import io.github.recrafter.lapis.layers.generator.IrKotlinFunctionBodyBuilder
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

inline fun <reified A : Annotation> KPFunctionBuilder.addAnnotation(builder: KPAnnotationBuilder.() -> Unit = {}) {
    addAnnotation(buildKotlinAnnotation<A>(builder))
}

fun KPFunctionBuilder.setBody(builder: IrKotlinFunctionBodyBuilder.() -> Unit = {}) {
    IrKotlinFunctionBodyBuilder(this).builder()
}

fun KPFunctionBuilder.addStatement(codeBlock: KPCodeBlock) {
    addStatement("%L", codeBlock)
}

fun KPFunctionBuilder.addReturnStatement(codeBlock: KPCodeBlock?) {
    if (codeBlock != null) {
        addStatement("return %L", codeBlock)
    } else {
        addStatement("return")
    }
}

fun KPFunctionBuilder.if_(condition: KPCodeBlock, body: IrKotlinCodeBlockBuilder.() -> Unit) {
    withControlFlow(buildKotlinCodeBlock("if (%L)") { arg(condition) }, body)
}

fun KPFunctionBuilder.withControlFlow(controlFlow: KPCodeBlock, body: IrKotlinCodeBlockBuilder.() -> Unit) {
    beginControlFlow("%L", controlFlow)
    buildKotlinCodeBlock(body)
    endControlFlow()
}

fun KPFunctionBuilder.setReturnType(type: IrTypeName?) {
    returns(type?.kotlin.orUnit())
}

fun KPFunctionBuilder.setReceiverType(type: IrTypeName) {
    receiver(type.kotlin)
}

fun KPFunctionBuilder.addParameter(parameter: IrParameter): KPParameter =
    KPParameter
        .builder(parameter.name, parameter.type.kotlin)
        .build()
        .also { addParameter(it) }

fun KPFunctionBuilder.setParameters(parameters: List<IrParameter>): List<KPParameter> =
    parameters.map { addParameter(it) }

fun KPFunctionBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(KPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(KPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(KPModifier.ABSTRACT)
            IrModifier.STATIC -> addAnnotation<JvmStatic>()
            IrModifier.OVERRIDE -> addModifiers(KPModifier.OVERRIDE)
        }
    }
}
