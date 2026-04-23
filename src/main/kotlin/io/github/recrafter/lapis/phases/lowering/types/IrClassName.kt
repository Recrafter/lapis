package io.github.recrafter.lapis.phases.lowering.types

import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.lastIndexOfOrNull
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.asIrWildcardTypeName

class IrClassName(override val kotlin: KPClassName) : IrTypeName(kotlin) {

    val packageName: String = kotlin.packageName
    val simpleName: String = kotlin.simpleName
    val nestedName: String = kotlin.simpleNames.joinToString(".")
    val qualifiedName: String = "$packageName.$nestedName"
    val binaryName: String get() = java.binaryName
    val internalName: String get() = java.internalName

    override val java: JPClassName by lazy {
        box().javaPrimitiveType as? JPClassName ?: when (kotlin) {
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

    fun nested(name: String): IrClassName =
        kotlin.nestedClass(name).asIrClassName()

    fun parameterizedBy(vararg argumentTypeNames: IrTypeName): IrParameterizedTypeName =
        kotlin.parameterizedBy(argumentTypeNames.map { it.kotlin }).asIrParameterizedTypeName()

    fun parameterizedByStar(): IrParameterizedTypeName =
        parameterizedBy(KPStar.asIrWildcardTypeName())

    companion object {
        fun of(packageName: String, vararg names: String): IrClassName =
            KPClassName(packageName, *names).asIrClassName()

        fun fromBinaryName(binaryName: String): IrClassName {
            val mainPart = binaryName.substringBefore('$')
            val nestedParts = if (binaryName.contains('$')) {
                binaryName.substringAfter('$').split('$')
            } else {
                emptyList()
            }

            val lastDotIndex = mainPart.lastIndexOfOrNull('.')

            val packageName = if (lastDotIndex != null) {
                mainPart.substring(0, lastDotIndex)
            } else {
                ""
            }

            val topLevelClassName = if (lastDotIndex != null) {
                mainPart.substring(lastDotIndex + 1)
            } else {
                mainPart
            }

            val allNames = listOf(topLevelClassName) + nestedParts
            return of(packageName, *allNames.toTypedArray())
        }
    }
}
