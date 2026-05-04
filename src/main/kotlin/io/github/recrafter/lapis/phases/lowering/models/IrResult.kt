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
    val tweakerAccessor: IrTweakerAccessor?,
    val mixinAccessor: IrMixinAccessor?,
)

sealed class IrAccessor(originatingFile: KSFile?) : IrGeneratedSource(originatingFile)

class IrMixinAccessor(
    originatingFile: KSFile?,

    val className: IrClassName,
    val schemaClassName: IrClassName,
    val schemaSide: Side,
    val isAccessibleSchema: Boolean,
    val targetInternalName: String,
    val receiverTypeName: IrTypeName,
    val members: List<IrMixinAccessorMember>,
) : IrAccessor(originatingFile)

sealed interface IrMixinAccessorMember

class IrMixinAccessorFieldMember(
    val name: String,
    val mappingName: String,
    val typeName: IrTypeName,
    val isStatic: Boolean,
    val removeFinal: Boolean,
    val ops: List<Op>,
    val descriptorClassName: IrClassName,
) : IrMixinAccessorMember

class IrMixinAccessorMethodMember(
    val name: String,
    val mappingName: String,
    val parameters: List<IrParameter>,
    val returnTypeName: IrTypeName?,
    val isStatic: Boolean,
) : IrMixinAccessorMember

class IrTweakerAccessor(
    originatingFile: KSFile?,

    val ownerJvmClassName: JvmClassName,
    val entries: List<IrTweakerAccessorEntry>,
) : IrAccessor(originatingFile)

class IrPatch(
    val side: Side,
    val isObject: Boolean,
    val className: IrClassName,
    val constructorArguments: List<IrPatchConstructorArgument>,
    val impl: IrPatchImpl?,
    val mixin: IrMixin,
)

class IrMixin(
    originatingFiles: List<KSFile?>,

    val className: IrClassName,
    val targetInstanceTypeName: IrTypeName,
    val isInterfaceTarget: Boolean,
    val targetInternalName: String,
    val injections: List<IrInjection>,
    val bridge: IrBridge?,
) : IrGeneratedSource(originatingFiles)

sealed interface IrPatchConstructorArgument
object IrPatchConstructorOriginArgument : IrPatchConstructorArgument

class IrPatchImpl(
    originatingFile: KSFile?,

    val className: IrClassName,
    val constructorParameters: List<IrPatchImplConstructorParameter>,
    val initStrategy: InitStrategy,
) : IrGeneratedSource(originatingFile)

sealed interface IrPatchImplConstructorParameter
object IrPatchImplConstructorInstanceParameter : IrPatchImplConstructorParameter
