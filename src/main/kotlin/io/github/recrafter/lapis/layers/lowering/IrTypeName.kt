package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.extensions.common.asIr
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.kp.*

open class IrTypeName(
    open val kotlin: KPTypeName,
    val boxed: Boolean = kotlin.isNullable,
) {
    open val java: JPTypeName by lazy {
        javaPrimitiveType ?: when (val kotlin = kotlin) {
            is KPClassName -> kotlin.asIr().java
            is KPParameterizedTypeName -> kotlin.asIr().java
            is KPWildcardTypeName -> kotlin.asIr().java
            else -> error("Unsupported type: $kotlin")
        }
    }

    val javaPrimitiveType: JPTypeName? by lazy {
        when (kotlin.copy(nullable = false)) {
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

    val is64bit: Boolean
        get() = javaPrimitiveType == JPLong || javaPrimitiveType == JPDouble

    val jvmType: IrJvmType
        get() = IrJvmType(this)

    fun box(): IrTypeName =
        if (boxed) this
        else IrTypeName(kotlin, true)

    fun unbox(): IrTypeName =
        if (boxed) IrTypeName(kotlin, false)
        else this

    override fun toString(): String =
        "[K=$kotlin,J=$java]"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is IrTypeName) {
            return false
        }
        return kotlin == other.kotlin
    }

    override fun hashCode(): Int =
        kotlin.hashCode()

    companion object {
        val VOID: IrTypeName = Void::class.asIr()
    }
}

fun IrTypeName?.orVoid(): IrTypeName =
    this ?: IrTypeName.VOID
