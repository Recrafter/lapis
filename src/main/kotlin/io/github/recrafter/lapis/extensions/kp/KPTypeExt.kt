package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import io.github.recrafter.lapis.layers.lowering.types.IrVariableTypeName

fun KPClassBuilder.setConstructor(parameters: List<IrParameter>, vararg modifiers: IrModifier): List<KPProperty> {
    primaryConstructor(buildKotlinConstructor {
        setParameters(parameters)
    })
    return parameters.map { parameter ->
        buildKotlinProperty(parameter.name, parameter.typeName) {
            initializer(parameter.name)
            setModifiers(*modifiers)
        }.also { addProperty(it) }
    }
}

fun KPClassBuilder.setSuperClass(typeName: IrTypeName, vararg constructorParameters: KPCodeBlock) {
    superclass(typeName.kotlin)
    constructorParameters.forEach {
        addSuperclassConstructorParameter(it)
    }
}

fun KPClassBuilder.addSuperInterface(typeName: IrTypeName) {
    addSuperinterface(typeName.kotlin)
}

fun KPClassBuilder.setVariableTypes(vararg types: IrVariableTypeName) {
    addTypeVariables(types.map { it.kotlin })
}

fun KPClassBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(KPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(KPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(KPModifier.ABSTRACT)
            IrModifier.OVERRIDE -> addModifiers(KPModifier.OVERRIDE)
            else -> lapisError("Modifier ${it.name.quoted()} is not applicable to Kotlin classes")
        }
    }
}
