package io.github.recrafter.lapis.layers.lowering.types

import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.lowering.asIr

class IrClassName(override val kotlin: KPClassType) : IrTypeName(kotlin) {

    val packageName: String = kotlin.packageName
    val simpleName: String = kotlin.simpleName
    val nestedName: String = kotlin.simpleNames.joinToString(".")
    val qualifiedName: String = "$packageName.$nestedName"
    val binaryName: String get() = java.binaryName
    val internalName: String get() = java.internalName

    override val java: JPClassType by lazy {
        box().javaPrimitiveType as? JPClassType ?: when (kotlin) {
            KPAny -> JPObject
            KPString -> JPString
            KPList -> JPList
            KPSet -> JPSet
            KPMap -> JPMap
            else -> JPClassType.get(
                packageName,
                kotlin.simpleNames.first(),
                *kotlin.simpleNames.drop(1).toTypedArray()
            )
        }
    }

    fun nested(name: String): IrClassName =
        kotlin.nestedClass(name).asIr()

    fun generic(vararg argumentTypeNames: IrTypeName?): IrGenericTypeName =
        kotlin.parameterizedBy(argumentTypeNames.map { it?.kotlin.orUnit() }).asIr()

    companion object {
        fun of(packageName: String, vararg names: String): IrClassName =
            KPClassType(packageName, *names).asIr()
    }
}
