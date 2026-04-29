package io.github.recrafter.lapis.phases.generator.accessor

import io.github.recrafter.lapis.phases.common.JvmClassName
import io.github.recrafter.lapis.phases.common.JvmDescriptor
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class MethodEntry(
    override val ownerJvmClassName: JvmClassName,
    val name: String,
    val parameterTypes: List<IrTypeName>,
    val returnTypeName: IrTypeName?,
    val removeFinal: Boolean,
    val isConstructor: Boolean,
) : AccessorConfigEntry {

    override val awEntry: String = buildString {
        append(
            if (removeFinal) "extendable"
            else "accessible"
        )
        append(" method ")
        append(ownerJvmClassName.internalName)
        append(" ")
        append(name)
        append(" ")
        append(JvmDescriptor.buildSignature(parameterTypes, returnTypeName))
    }

    override val atEntry: String = buildString {
        append(
            if (removeFinal) "public-f"
            else "public"
        )
        append(" ")
        append(ownerJvmClassName.binaryName)
        append(" ")
        append(name)
        append(JvmDescriptor.buildSignature(parameterTypes, returnTypeName))
    }

    override val sectionIndex: Int = if (isConstructor) 2 else 3
    override val sortingName: String = if (isConstructor) "" else name
    override val parametersCount: Int = parameterTypes.size
}
