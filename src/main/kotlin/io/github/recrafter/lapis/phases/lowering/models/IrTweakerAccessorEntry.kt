package io.github.recrafter.lapis.phases.lowering.models

import io.github.recrafter.lapis.phases.common.JvmClassName
import io.github.recrafter.lapis.phases.common.JvmDescriptor
import io.github.recrafter.lapis.phases.common.jvmDescriptor
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

sealed interface IrTweakerAccessorEntry {
    fun buildWidenerTweak(ownerJvmClassName: JvmClassName): String
    fun buildTransformerTweak(ownerJvmClassName: JvmClassName): String
}

class IrTweakerAccessorClassEntry(val removeFinal: Boolean) : IrTweakerAccessorEntry {

    override fun buildWidenerTweak(ownerJvmClassName: JvmClassName): String = buildString {
        append(if (removeFinal) "extendable" else "accessible")
        append(" class ")
        append(ownerJvmClassName.internalName)
    }

    override fun buildTransformerTweak(ownerJvmClassName: JvmClassName): String = buildString {
        append(if (removeFinal) "public-f" else "public")
        append(" ")
        append(ownerJvmClassName.binaryName)
    }
}

class IrTweakerAccessorFieldEntry(
    val name: String,
    val typeName: IrTypeName,
    val removeFinal: Boolean,
) : IrTweakerAccessorEntry {

    override fun buildWidenerTweak(ownerJvmClassName: JvmClassName): String = buildString {
        val fieldPart = buildString {
            append("field ")
            append(ownerJvmClassName.internalName)
            append(" ")
            append(name)
            append(" ")
            append(typeName.jvmDescriptor)
        }
        append("accessible $fieldPart")
        if (removeFinal) {
            appendLine()
            append("mutable $fieldPart")
        }
    }

    override fun buildTransformerTweak(ownerJvmClassName: JvmClassName): String = buildString {
        append(if (removeFinal) "public-f" else "public")
        append(" ")
        append(ownerJvmClassName.binaryName)
        append(" ")
        append(name)
    }
}

class IrTweakerAccessorMethodEntry(
    val name: String,
    val parameterTypes: List<IrTypeName>,
    val returnTypeName: IrTypeName?,
    val removeFinal: Boolean,
) : IrTweakerAccessorEntry {

    override fun buildWidenerTweak(ownerJvmClassName: JvmClassName): String = buildString {
        append(if (removeFinal) "extendable" else "accessible")
        append(" method ")
        append(ownerJvmClassName.internalName)
        append(" ")
        append(name)
        append(" ")
        append(JvmDescriptor.buildSignature(parameterTypes, returnTypeName))
    }

    override fun buildTransformerTweak(ownerJvmClassName: JvmClassName): String = buildString {
        append(if (removeFinal) "public-f" else "public")
        append(" ")
        append(ownerJvmClassName.binaryName)
        append(" ")
        append(name)
        append(JvmDescriptor.buildSignature(parameterTypes, returnTypeName))
    }
}
