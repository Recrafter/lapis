package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.annotations.InitStrategy
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.phases.common.JvmClassName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class IrResult(
    val schemas: List<IrSchema>,
    val patches: List<IrPatch>,
)

class IrSchema(
    val className: IrClassName,
    val descriptors: List<IrDescriptor>,
    val tweakAccessor: IrTweakAccessor?,
    val mixinAccessor: IrMixinAccessor?,
)

sealed interface IrAccessor

abstract class IrGeneratedMixin(originatingFiles: List<KSFile>) : IrGeneratedSource(originatingFiles) {
    abstract val side: Side
}

class IrMixinAccessor(
    originatingFiles: List<KSFile>,

    override val className: IrClassName,
    override val side: Side,
    val schemaClassName: IrClassName,
    val isAccessibleSchema: Boolean,
    val targetInternalName: String,
    val receiverTypeName: IrTypeName,
    val members: List<IrMixinAccessorMember>,
) : IrGeneratedMixin(originatingFiles), IrAccessor

sealed class IrMixinAccessorMember(val isStatic: Boolean, val schemaReceiverClassName: IrClassName)

class IrMixinAccessorFieldMember(
    val name: String,
    val mappingName: String,
    val typeName: IrTypeName,
    isStatic: Boolean,
    val removeFinal: Boolean,
    val ops: List<Op>,
    schemaReceiverClassName: IrClassName,
) : IrMixinAccessorMember(isStatic, schemaReceiverClassName)

class IrMixinAccessorMethodMember(
    val name: String,
    val mappingName: String,
    val parameters: List<IrParameter>,
    val returnTypeName: IrTypeName?,
    isStatic: Boolean,
    schemaReceiverClassName: IrClassName,
) : IrMixinAccessorMember(isStatic, schemaReceiverClassName)

class IrTweakAccessor(
    originatingFile: KSFile?,

    val ownerJvmClassName: JvmClassName,
    val entries: List<IrTweakAccessorEntry>,
) : IrGeneratedFile(originatingFile), IrAccessor

class IrPatch(
    val isObject: Boolean,
    val className: IrClassName,
    val constructorArguments: List<IrPatchConstructorArgument>,
    val impl: IrPatchImpl?,
    val mixin: IrMixin,
)

class IrMixin(
    originatingFiles: List<KSFile>,

    override val className: IrClassName,
    override val side: Side,
    val targetInstanceTypeName: IrTypeName,
    val isInterfaceTarget: Boolean,
    val targetInternalName: String,
    val injections: List<IrInjection>,
    val bridge: IrBridge?,
) : IrGeneratedMixin(originatingFiles)

sealed interface IrPatchConstructorArgument
object IrPatchConstructorOriginArgument : IrPatchConstructorArgument

class IrPatchImpl(
    originatingFile: KSFile?,

    override val className: IrClassName,
    val constructorParameters: List<IrPatchImplConstructorParameter>,
    val initStrategy: InitStrategy,
) : IrGeneratedSource(originatingFile)

sealed interface IrPatchImplConstructorParameter
object IrPatchImplConstructorInstanceParameter : IrPatchImplConstructorParameter
