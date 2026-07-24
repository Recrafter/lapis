package io.github.recrafter.lapis.extensions.kp

import io.github.diskria.poetesse.kotlin.KPCodeBlock
import io.github.diskria.poetesse.kotlin.KPTypeBuilder
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName

fun KPTypeBuilder.setConstructor(parameters: List<IrParameter>) {
    primaryConstructor(buildKotlinConstructor {
        setParameters(parameters)
    })
}

fun KPTypeBuilder.setConstructor(vararg parameters: IrParameter) {
    setConstructor(parameters.toList())
}

fun KPTypeBuilder.setSuperClass(
    typeName: IrTypeName,
    constructorArguments: List<KPCodeBlock> = emptyList()
) {
    superclass(typeName.kotlin)
    constructorArguments.forEach {
        addSuperclassConstructorParameter(it)
    }
}

fun KPTypeBuilder.addSuperInterface(typeName: IrTypeName) {
    addSuperinterface(typeName.kotlin)
}

fun KPTypeBuilder.setVariableTypes(vararg types: IrTypeVariableName) {
    addTypeVariables(types.map { it.kotlin })
}
