package io.github.recrafter.lapis.phases.lowering.types

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.lowering.asIrClassName

class IrClassName(override val kotlin: KPClassName) : IrTypeName(kotlin) {

    val packageName: String = kotlin.packageName
    val simpleName: String = kotlin.simpleName
    val nestedName: String = kotlin.simpleNames.joinToString(".")
    val qualifiedName: String = "$packageName.$nestedName"
    val binaryName: String get() = java.binaryName
    val internalName: String get() = java.internalName

    override val java: JPClassName by lazy {
        box().getJavaPrimitiveType(allowVoid = false) as? JPClassName ?: when (kotlin) {
            KPAny -> JPObject
            KPString -> JPString
            KPList -> JPList
            KPSet -> JPSet
            KPMap -> JPMap
            else -> JPClassName.get(
                packageName,
                kotlin.simpleNames.first(),
                *kotlin.simpleNames.drop(1).toTypedArray()
            )
        }
    }

    fun inner(name: String): IrClassName =
        kotlin.nestedClass(name).asIrClassName()

    fun derived(suffix: String): IrClassName =
        of(packageName, (kotlin.simpleNames + suffix).joinToString("_"))

    companion object {
        fun of(packageName: String, vararg names: String): IrClassName =
            KPClassName(packageName, *names).asIrClassName()
    }
}
