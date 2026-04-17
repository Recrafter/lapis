package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName

fun KPClassBuilder.setConstructor(parameters: List<IrParameter>) {
    primaryConstructor(buildKotlinConstructor {
        setParameters(parameters)
    })
    if (parameters.any { it.modifiers.isNotEmpty() }) {
        addProperties(parameters.map { parameter ->
            buildKotlinProperty(parameter.name, parameter.typeName) {
                initializer(parameter.name)
                setModifiers(*parameter.modifiers.toTypedArray())
            }
        })
    }
}

fun KPClassBuilder.setConstructor(vararg parameters: IrParameter) {
    setConstructor(parameters.toList())
}

fun KPClassBuilder.setSuperClass(typeName: IrTypeName, constructorParameters: List<KPCodeBlock> = emptyList()) {
    superclass(typeName.kotlin)
    constructorParameters.forEach {
        addSuperclassConstructorParameter(it)
    }
}

fun KPClassBuilder.addSuperInterface(typeName: IrTypeName) {
    addSuperinterface(typeName.kotlin)
}

fun KPClassBuilder.setVariableTypes(vararg types: IrTypeVariableName) {
    addTypeVariables(types.map { it.kotlin })
}

fun KPClassBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(KPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(KPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(KPModifier.ABSTRACT)
            IrModifier.OVERRIDE -> addModifiers(KPModifier.OVERRIDE)
            IrModifier.SEALED -> addModifiers(KPModifier.SEALED)
            else -> lapisError("Modifier ${it.name.quoted()} is not applicable to Kotlin classes")
        }
    }
}
