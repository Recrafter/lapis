package io.github.recrafter.lapis.extensions.kp

import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget
import com.squareup.kotlinpoet.ContextParameter
import com.squareup.kotlinpoet.ExperimentalKotlinPoetApi
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrKotlinFunctionBody
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

inline fun <reified A : Annotation> KPFunctionBuilder.addAnnotation(
    useSiteTarget: UseSiteTarget? = null,
    builder: Builder<KPAnnotationBuilder> = {}
) {
    addAnnotation(buildKotlinAnnotation<A>(useSiteTarget, builder))
}

fun KPFunctionBuilder.setBody(builder: Builder<IrKotlinFunctionBody> = {}) {
    IrKotlinFunctionBody(this).builder()
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
        .also { addParameter(it) }
}

fun KPFunctionBuilder.setParameters(parameters: List<IrParameter>) {
    parameters.forEach { addParameter(it) }
}

@OptIn(ExperimentalKotlinPoetApi::class)
fun KPFunctionBuilder.setContextParameters(parameters: List<IrParameter>) {
    contextParameters(parameters.map { ContextParameter(it.name, it.typeName.kotlin) })
}

fun KPFunctionBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(KPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(KPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(KPModifier.ABSTRACT)
            IrModifier.OVERRIDE -> addModifiers(KPModifier.OVERRIDE)
            IrModifier.INLINE -> addModifiers(KPModifier.INLINE)
            IrModifier.OPERATOR -> addModifiers(KPModifier.OPERATOR)
            else -> lapisError("Modifier ${it.name.quoted()} is not applicable to Kotlin functions")
        }
    }
}
