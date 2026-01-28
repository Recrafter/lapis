package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.extensions.common.asIr
import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.jvm.*
import io.github.recrafter.lapis.extensions.kp.*

open class IrTypeName(
    open val kotlin: KPTypeName,
    val boxed: Boolean = kotlin.isNullable,
) {
    open val java: JPTypeName
        get() = javaPrimitiveType ?: when (kotlin) {
            is KPClassName -> (kotlin as KPClassName).asIr().java
            is KPParameterizedTypeName -> (kotlin as KPParameterizedTypeName).asIr().java
            else -> error("Unsupported type: $kotlin")
        }

    val javaPrimitiveType: JPTypeName?
        get() = when (kotlin.copy(nullable = false)) {
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

    val jvmDescriptor: String
        get() = when (javaPrimitiveType) {
            JPVoid -> JvmVoid
            JPBoolean -> JvmBoolean
            JPByte -> JvmByte
            JPShort -> JvmShort
            JPInt -> JvmInt
            JPLong -> JvmLong
            JPChar -> JvmChar
            JPFloat -> JvmFloat
            JPDouble -> JvmDouble
            else -> {
                val jpClassName = when (kotlin) {
                    is KPClassName -> (kotlin as KPClassName).asIr().java
                    is KPParameterizedTypeName -> (kotlin as KPParameterizedTypeName).asIr().java.rawType()
                    else -> error("Unsupported type: $java")
                }
                "L" + jpClassName.qualifiedName.replace(".", "/") + ";"
            }
        }

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
}

fun IrTypeName?.orVoid(): IrTypeName =
    this ?: Void::class.asIr()
