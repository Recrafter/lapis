package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.extensions.singleQuoted
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeVariable

fun KPTypeBuilder.setConstructor(parameters: List<IrParameter>, vararg modifiers: IrModifier): List<KPProperty> {
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

fun KPTypeBuilder.setSuperClass(type: IrTypeName, vararg constructorParameters: KPCodeBlock) {
    superclass(type.kotlin)
    constructorParameters.forEach {
        addSuperclassConstructorParameter(it)
    }
}

fun KPTypeBuilder.addSuperInterface(type: IrTypeName) {
    addSuperinterface(type.kotlin)
}

fun KPTypeBuilder.setTypeVariables(vararg typeVariables: IrTypeVariable) {
    addTypeVariables(typeVariables.map { it.kotlin })
}

fun KPTypeBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(KPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(KPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(KPModifier.ABSTRACT)
            IrModifier.STATIC -> error("Modifier ${it.name.singleQuoted()} is not applicable to Kotlin types")
            IrModifier.OVERRIDE -> addModifiers(KPModifier.OVERRIDE)
        }
    }
}
