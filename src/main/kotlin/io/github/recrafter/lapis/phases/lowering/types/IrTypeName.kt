package io.github.recrafter.lapis.phases.lowering.types

import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.lowering.*

open class IrTypeName(
    open val kotlin: KPTypeName,
    val boxed: Boolean = kotlin.isNullable,
) {
    val javaPrimitiveType: JPTypeName? by lazy {
        when (makeNotNullable().kotlin) {
            KPUnit -> JPVoid
            KPBoolean -> JPBoolean
            KPByte -> JPByte
            KPShort -> JPShort
            KPInt -> JPInt
            KPLong -> JPLong
            KPChar -> JPChar
            KPFloat -> JPFloat
            KPDouble -> JPDouble
            else -> null
        }?.run {
            if (boxed) box()
            else this
        }
    }

    open val java: JPTypeName by lazy {
        javaPrimitiveType ?: javaArrayType ?: when (val kotlin = kotlin) {
            is KPClassName -> kotlin.asIrClassName().java
            is KPParameterizedTypeName -> kotlin.asIrParameterizedTypeName().java
            is KPWildcardTypeName -> kotlin.asIrWildcardTypeName().java
            is KPTypeVariableName -> kotlin.asIrTypeVariableName().java
            is KPLambdaTypeName -> kotlin.asIrLambdaTypeName().java
            is KPDynamic -> kotlin.asIrDynamic().java
        }
    }

    val is64bit: Boolean by lazy {
        if (boxed) false
        else javaPrimitiveType == JPLong || javaPrimitiveType == JPDouble
    }

    private val javaArrayType: JPArrayTypeName? by lazy {
        val arrayComponentType = when (val kotlin = kotlin) {
            KPBooleanArray -> JPBoolean
            KPByteArray -> JPByte
            KPShortArray -> JPShort
            KPIntArray -> JPInt
            KPLongArray -> JPLong
            KPCharArray -> JPChar
            KPFloatArray -> JPFloat
            KPDoubleArray -> JPDouble
            else -> {
                if (kotlin is KPParameterizedTypeName && kotlin.rawType == KPArray) {
                    kotlin.typeArguments.singleOrNull()?.asIrTypeName()?.java
                } else {
                    return@lazy null
                }
            }
        }
        JPArrayTypeName.of(arrayComponentType)
    }

    val rawClassName: IrClassName
        get() = when (val kotlin = this.kotlin) {
            is KPClassName -> kotlin.asIrClassName()
            is KPParameterizedTypeName -> kotlin.rawType.asIrClassName()
            else -> lapisError("Cannot get raw class")
        }

    fun parameterizedBy(vararg argumentTypeNames: IrTypeName): IrParameterizedTypeName =
        rawClassName.kotlin.parameterizedBy(argumentTypeNames.map { it.kotlin }).asIrParameterizedTypeName()

    fun parameterizedByStar(): IrParameterizedTypeName =
        parameterizedBy(KPStar.asIrWildcardTypeName())

    fun box(): IrTypeName =
        if (boxed) this
        else IrTypeName(kotlin, true)

    fun unbox(): IrTypeName =
        if (boxed) IrTypeName(kotlin, false)
        else this

    fun makeNullable(): IrTypeName =
        if (kotlin.isNullable) this
        else IrTypeName(kotlin.copy(nullable = true))

    fun makeNotNullable(): IrTypeName =
        if (kotlin.isNullable) IrTypeName(kotlin.copy(nullable = false))
        else this

    override fun toString(): String =
        "[K=$kotlin,J=${
            java.let {
                if (boxed) "Boxed($it)"
                else it
            }
        }]"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is IrTypeName) {
            return false
        }
        return kotlin == other.kotlin && boxed == other.boxed
    }

    override fun hashCode(): Int =
        kotlin.hashCode()

    companion object {
        val VOID: IrTypeName = Void::class.asIrTypeName()
    }
}

fun IrTypeName?.orVoid(): IrTypeName =
    this ?: IrTypeName.VOID
