package io.github.recrafter.lapis.extensions.kp

import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget
import com.squareup.kotlinpoet.ContextParameter
import com.squareup.kotlinpoet.ExperimentalKotlinPoetApi
import io.github.recrafter.lapis.extensions.common.Builder
import io.github.recrafter.lapis.phases.generator.builders.GenKotlinFunctionBody
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

inline fun <reified A : Annotation> KPFunctionBuilder.addAnnotation(
    useSiteTarget: UseSiteTarget? = null,
    builder: Builder<KPAnnotationBuilder> = {}
) {
    addAnnotation(buildKotlinAnnotation<A>(useSiteTarget, builder))
}

fun KPFunctionBuilder.setBody(builder: Builder<GenKotlinFunctionBody> = {}) {
    GenKotlinFunctionBody(this).builder()
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

fun KPFunctionBuilder.setReturnType(typeName: IrTypeName?) {
    returns(typeName?.kotlin.orUnit())
}

fun KPFunctionBuilder.setReceiverType(typeName: IrTypeName) {
    receiver(typeName.kotlin)
}

fun KPFunctionBuilder.addParameter(parameter: IrParameter) {
    KPParameter
        .builder(parameter.name, parameter.typeName.kotlin)
        .build()
        .also(::addParameter)
}

fun KPFunctionBuilder.setParameters(parameters: List<IrParameter>) {
    parameters.forEach(::addParameter)
}

@OptIn(ExperimentalKotlinPoetApi::class)
fun KPFunctionBuilder.setContextParameters(parameters: List<IrParameter>) {
    contextParameters(parameters.map { ContextParameter(it.name, it.typeName.kotlin) })
}
