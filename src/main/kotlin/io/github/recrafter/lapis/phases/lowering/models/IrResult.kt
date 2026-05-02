package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.annotations.InitStrategy
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.phases.common.JvmClassName
import io.github.recrafter.lapis.phases.common.JvmDescriptor
import io.github.recrafter.lapis.phases.common.jvmDescriptor
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class IrResult(
    val schemas: List<IrSchema>,
    val patches: List<IrPatch>,
)

class IrSchema(
    val originatingFile: KSFile?,

    val className: IrClassName,
    val descriptors: List<IrDescriptor>,
    val tweakerAccessor: IrTweakerAccessor?,
)

sealed interface IrAccessor
class IrTweakerAccessor(
    val ownerJvmClassName: JvmClassName,
    val entries: List<IrTweakerAccessorEntry>,
) : IrAccessor

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

class IrPatch(
    val originatingFile: KSFile?,

    val side: Side,
    val isObject: Boolean,
    val className: IrClassName,
    val constructorArguments: List<IrPatchConstructorArgument>,
    val impl: IrPatchImpl?,
    val mixin: IrMixin,
)

class IrMixin(
    val className: IrClassName,
    val targetInstanceTypeName: IrTypeName,
    val isInterfaceTarget: Boolean,
    val targetInternalName: String,
    val injections: List<IrInjection>,
    val bridge: IrBridge?,
)

sealed interface IrPatchConstructorArgument
object IrPatchConstructorOriginArgument : IrPatchConstructorArgument

class IrPatchImpl(
    val className: IrClassName,
    val constructorParameters: List<IrPatchImplConstructorParameter>,
    val initStrategy: InitStrategy,
)

sealed interface IrPatchImplConstructorParameter
object IrPatchImplConstructorInstanceParameter : IrPatchImplConstructorParameter
