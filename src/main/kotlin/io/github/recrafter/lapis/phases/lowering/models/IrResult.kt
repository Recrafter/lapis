package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

class IrResult(
    val schemas: List<IrSchema>,
    val mixins: List<IrMixin>,
)

class IrSchema(
    val containingFile: KSFile?,

    val makePublic: Boolean,
    val removeFinal: Boolean,
    val className: IrClassName,
    val originClassName: IrClassName,
    val descriptors: List<IrDescriptor>,
)

class IrMixin(
    val containingFile: KSFile?,

    val className: IrClassName,
    val patchClassName: IrClassName,
    val patchImpl: IrPatchImpl,
    val instanceClassName: IrClassName,
    val bytecodeTargetName: String,

    val side: Side,
    val extension: IrExtension?,
    val injections: List<IrInjection>,
)

class IrPatchImpl(
    val className: IrClassName,

    val constructorArguments: List<IrPatchImplConstructorArgument>,
    val patchConstructorArguments: List<IrPatchConstructorArgument>,
)

sealed interface IrPatchImplConstructorArgument
object IrPatchImplConstructorInstanceArgument : IrPatchImplConstructorArgument

sealed interface IrPatchConstructorArgument
object IrPatchConstructorOriginArgument : IrPatchConstructorArgument
