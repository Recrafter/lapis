package io.github.recrafter.lapis.layers.lowering

import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*

class IrClassName(override val kotlin: KPClassName) : IrTypeName(kotlin) {

    val packageName: String
        get() = kotlin.packageName

    val simpleName: String
        get() = kotlin.simpleName

    val name: String
        get() = kotlin.simpleNames.joinToString(".")

    val qualifiedName: String
        get() = "$packageName.$name"

    val uniqueJvmName: String
        get() = qualifiedName.replace(".", "_")

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

    fun parameterizedBy(vararg genericTypes: IrTypeName?): IrParameterizedTypeName =
        IrParameterizedTypeName(kotlin.parameterizedBy(genericTypes.map { it?.kotlin.orUnit() }))

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is IrClassName) {
            return false
        }
        return kotlin == other.kotlin
    }

    override fun toString(): String =
        kotlin.toString()

    override fun hashCode(): Int =
        kotlin.hashCode()

    companion object {
        fun of(packageName: String, vararg names: String): IrClassName =
            IrClassName(KPClassName(packageName, *names))
    }
}
