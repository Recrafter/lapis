package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.layers.lowering.IrClassName
import io.github.recrafter.lapis.layers.lowering.IrParameter

fun KPType.toKotlinFile(packageName: String, builder: KPFileBuilder.() -> Unit = {}): KPFile =
    buildKotlinFile(
        packageName,
        requireNotNull(name) { "Cannot generate Kotlin file for anonymous TypeSpec." }
    ) {
        builder()
        addType(this@toKotlinFile)
    }

fun KPTypeBuilder.setConstructor(parameters: List<IrParameter>) {
    primaryConstructor(buildKotlinConstructor {
        addParameters(parameters)
    })
    parameters.forEach { parameter ->
        addProperty(buildKotlinProperty(parameter.name, parameter.type) {
          initializer(parameter.name)
        })
    }
}

fun KPTypeBuilder.setSuperClassType(type: IrClassName) {
    superclass(type.kotlin)
}
