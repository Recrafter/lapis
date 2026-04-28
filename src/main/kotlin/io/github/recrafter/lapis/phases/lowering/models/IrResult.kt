package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class IrResult(
    val schemas: List<IrSchema>,
    val patches: List<IrPatch>,
)

class IrSchema(
    val originatingFile: KSFile?,

    val makePublic: Boolean,
    val removeFinal: Boolean,
    val className: IrClassName,
    val originTypeName: IrTypeName,
    val descriptors: List<IrDescriptor>,
)

class IrPatch(
    val originatingFile: KSFile?,

    val side: Side,
    val className: IrClassName,
    val constructorArguments: List<IrPatchConstructorArgument>,
    val impl: IrPatchImpl,
    val mixin: IrMixin,
    val extension: IrExtension?,
)

class IrMixin(
    val className: IrClassName,
    val instanceTypeName: IrTypeName,
    val isInterfaceInstance: Boolean,
    val targetBinaryName: String,
    val injections: List<IrInjection>,
)

sealed interface IrPatchConstructorArgument
object IrPatchConstructorOriginArgument : IrPatchConstructorArgument

class IrPatchImpl(
    val className: IrClassName,
    val constructorParameters: List<IrPatchImplConstructorParameter>,
)

sealed interface IrPatchImplConstructorParameter
object IrPatchImplConstructorInstanceParameter : IrPatchImplConstructorParameter
