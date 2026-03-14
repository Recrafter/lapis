package io.github.recrafter.lapis.layers.lowering.types

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.lowering.asIr

open class IrType(
    open val kotlin: KPType,
    val boxed: Boolean = kotlin.isNullable,
) {
    val javaPrimitiveType: JPType? by lazy {
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

    open val java: JPType by lazy {
        javaPrimitiveType ?: javaArrayType ?: when (val kotlin = kotlin) {
            is KPClassType -> kotlin.asIr().java
            is KPGenericType -> kotlin.asIr().java
            is KPWildcardType -> kotlin.asIr().java
            is KPVariableType -> kotlin.asIr().java
            is KPFunctionType -> lapisError(
                "Function type ${kotlin.toString().quoted()} is not supported, " +
                    "but was leaked into IR"
            )

            is KPDynamicType -> lapisError(
                "Dynamic type ${kotlin.toString().quoted()} is not supported, " +
                    "but was leaked into IR"
            )
        }
    }

    val is64bit: Boolean by lazy {
        if (boxed) false
        else javaPrimitiveType == JPLong || javaPrimitiveType == JPDouble
    }

    private val javaArrayType: JPArrayType? by lazy {
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
                if (kotlin is KPGenericType && kotlin.rawType == KPArray) {
                    kotlin.typeArguments.singleOrNull()?.asIr()?.java
                } else {
                    return@lazy null
                }
            }
        }
        JPArrayType.of(arrayComponentType)
    }

    fun box(): IrType =
        if (boxed) this
        else IrType(kotlin, true)

    fun unbox(): IrType =
        if (boxed) IrType(kotlin, false)
        else this

    fun makeNullable(): IrType =
        if (kotlin.isNullable) this
        else IrType(kotlin.copy(nullable = true))

    fun makeNotNullable(): IrType =
        if (kotlin.isNullable) IrType(kotlin.copy(nullable = false))
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
        if (other !is IrType) {
            return false
        }
        return kotlin == other.kotlin && boxed == other.boxed
    }

    override fun hashCode(): Int =
        kotlin.hashCode()

    companion object {
        val VOID: IrType = Void::class.asIr()
    }
}

fun IrType?.orVoid(): IrType =
    this ?: IrType.VOID
