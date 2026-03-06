package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.IrTypeName

fun KPTypeBuilder.setConstructor(parameters: List<IrParameter>, vararg modifiers: KPModifier): List<KPProperty> {
    primaryConstructor(buildKotlinConstructor {
        setParameters(parameters)
    })
    return parameters.map { parameter ->
        buildKotlinProperty(parameter.name, parameter.type) {
            initializer(parameter.name)
            addModifiers(*modifiers)
        }.also { addProperty(it) }
    }
}

fun KPTypeBuilder.setSuperClass(type: IrTypeName) {
    superclass(type.kotlin)
}

fun KPTypeBuilder.addSuperInterface(type: IrTypeName) {
    addSuperinterface(type.kotlin)
}

fun KPTypeBuilder.setModifiers(vararg modifiers: IrModifier) {
    modifiers.forEach {
        when (it) {
            IrModifier.PUBLIC -> addModifiers(KPModifier.PUBLIC)
            IrModifier.PRIVATE -> addModifiers(KPModifier.PRIVATE)
            IrModifier.ABSTRACT -> addModifiers(KPModifier.PRIVATE)
            IrModifier.STATIC -> error("Kotlin types can't be overridden.")
            IrModifier.OVERRIDE -> addModifiers(KPModifier.OVERRIDE)
        }
    }
}
