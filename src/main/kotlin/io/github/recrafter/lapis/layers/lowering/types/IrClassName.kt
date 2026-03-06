package io.github.recrafter.lapis.layers.lowering.types

import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.lowering.asIr

class IrClassName(override val kotlin: KPClassName) : IrTypeName(kotlin) {

    val packageName: String = kotlin.packageName
    val simpleName: String = kotlin.simpleName
    val name: String = kotlin.simpleNames.joinToString(".")
    val qualifiedName: String = "$packageName.$name"

    override val java: JPClassName by lazy {
        box().javaPrimitiveType as? JPClassName
            ?: when (kotlin) {
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

    fun parameterizedBy(vararg genericTypes: IrTypeName?): IrParameterizedTypeName =
        IrParameterizedTypeName(kotlin.parameterizedBy(genericTypes.map { it?.kotlin.orUnit() }))

    companion object {
        fun of(packageName: String, vararg names: String): IrClassName =
            KPClassName(packageName, *names).asIr()
    }
}
