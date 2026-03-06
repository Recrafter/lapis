package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.extensions.jvm.*
import io.github.recrafter.lapis.extensions.kp.KPClassName
import io.github.recrafter.lapis.extensions.kp.KPParameterizedTypeName
import io.github.recrafter.lapis.extensions.kp.KPWildcardTypeName

class IrJvmType(private val type: IrTypeName) {

    val mixinDescriptor: String
        get() = when (type.javaPrimitiveType) {
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
                val jpClassName = when (val kotlin = type.kotlin) {
                    is KPClassName -> kotlin.asIr().java
                    is KPParameterizedTypeName -> kotlin.asIr().java.rawType()
                    else -> error("Unsupported type: $kotlin")
                }
                "L" + jpClassName.qualifiedName.replace(".", "/") + ";"
            }
        }

    companion object {
        const val CONSTRUCTOR_NAME: String = "<init>"
    }
}
