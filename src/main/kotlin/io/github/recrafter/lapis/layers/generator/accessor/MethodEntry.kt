package io.github.recrafter.lapis.layers.generator.accessor

import io.github.recrafter.lapis.extensions.jp.binaryName
import io.github.recrafter.lapis.extensions.jp.internalName
import io.github.recrafter.lapis.layers.lowering.JvmDesc
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName

class MethodEntry(
    override val ownerClassName: IrClassName,
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
        append(ownerClassName.java.internalName)
        append(" ")
        append(name)
        append(" ")
        append(JvmDesc.buildSignature(parameterTypes, returnTypeName))
    }

    override val atEntry: String = buildString {
        append(
            if (removeFinal) "public-f"
            else "public"
        )
        append(" ")
        append(ownerClassName.java.binaryName)
        append(" ")
        append(name)
        append(JvmDesc.buildSignature(parameterTypes, returnTypeName))
    }

    override val sectionIndex: Int = if (isConstructor) 2 else 3
    override val sortingName: String = if (isConstructor) "" else name
    override val parametersCount: Int = parameterTypes.size
}
