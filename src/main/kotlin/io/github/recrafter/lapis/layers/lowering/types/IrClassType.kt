package io.github.recrafter.lapis.layers.lowering.types

import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.lowering.asIr

class IrClassType(override val kotlin: KPClassType) : IrType(kotlin) {

    val packageName: String = kotlin.packageName
    val simpleName: String = kotlin.simpleName
    val name: String = kotlin.simpleNames.joinToString(".")
    val qualifiedName: String = "$packageName.$name"

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

    fun nested(name: String): IrClassType =
        kotlin.nestedClass(name).asIr()

    fun generic(vararg argumentTypes: IrType?): IrGenericType =
        kotlin
            .parameterizedBy(argumentTypes.map { it?.kotlin.orUnit() })
            .asIr()

    companion object {
        fun of(packageName: String, vararg names: String): IrClassType =
            KPClassType(packageName, *names).asIr()
    }
}
