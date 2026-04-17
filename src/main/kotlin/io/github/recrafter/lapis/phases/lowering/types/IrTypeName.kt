package io.github.recrafter.lapis.phases.lowering.types

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.lowering.asIr
import io.github.recrafter.lapis.phases.lowering.asIrTypeName

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
            is KPClassName -> kotlin.asIr().java
            is KPParameterizedTypeName -> kotlin.asIr().java
            is KPWildcardTypeName -> kotlin.asIr().java
            is KPTypeVariableName -> kotlin.asIr().java
            is KPLambdaTypeName -> kotlin.asIr().java
            is KPDynamic -> kotlin.asIr().java
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
        val VOID: IrTypeName = Void::class.asIr()
    }
}

fun IrTypeName?.orVoid(): IrTypeName =
    this ?: IrTypeName.VOID
