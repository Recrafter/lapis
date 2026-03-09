package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.types.IrType
import io.github.recrafter.lapis.layers.lowering.types.IrVariableType

fun KPClassBuilder.setConstructor(parameters: List<IrParameter>, vararg modifiers: IrModifier): List<KPProperty> {
    primaryConstructor(buildKotlinConstructor {
        setParameters(parameters)
    })
    return parameters.map { parameter ->
        buildKotlinProperty(parameter.name, parameter.type) {
            initializer(parameter.name)
            setModifiers(*modifiers)
        }.also { addProperty(it) }
    }
}

fun KPClassBuilder.setSuperClass(type: IrType, vararg constructorParameters: KPCodeBlock) {
    superclass(type.kotlin)
    constructorParameters.forEach {
        addSuperclassConstructorParameter(it)
    }
}

fun KPClassBuilder.addSuperInterface(type: IrType) {
    addSuperinterface(type.kotlin)
}

fun KPClassBuilder.setVariableTypes(vararg types: IrVariableType) {
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
