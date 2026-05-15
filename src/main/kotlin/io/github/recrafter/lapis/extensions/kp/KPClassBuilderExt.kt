package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName

fun KPClassBuilder.setConstructor(parameters: List<IrParameter>) {
    primaryConstructor(buildKotlinConstructor {
        setParameters(parameters)
    })
}

fun KPClassBuilder.setConstructor(vararg parameters: IrParameter) {
    setConstructor(parameters.toList())
}

fun KPClassBuilder.setSuperClass(typeName: IrTypeName, constructorArguments: List<KPCodeBlock> = emptyList()) {
    superclass(typeName.kotlin)
    constructorArguments.forEach {
        addSuperclassConstructorParameter(it)
    }
}

fun KPClassBuilder.addSuperInterface(typeName: IrTypeName) {
    addSuperinterface(typeName.kotlin)
}

fun KPClassBuilder.setVariableTypes(vararg types: IrTypeVariableName) {
    addTypeVariables(types.map { it.kotlin })
}
