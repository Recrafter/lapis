package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.annotations.InitStrategy
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.common.JvmClassName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class IrResult(
    val schemas: List<IrSchema>,
    val patches: List<IrPatch>,
)

sealed class IrSourceFile(val className: IrClassName)

class IrSchema(
    className: IrClassName,
    val descriptors: List<IrDescriptor>,
    val tweakAccessor: IrTweakAccessor?,
    val mixinAccessor: IrMixinAccessor?,
) : IrSourceFile(className)

abstract class IrMixinRelatedBlueprint(classKind: IrJavaClassKind) : IrJavaBlueprint(classKind) {
    abstract val side: Side
}

sealed interface IrAccessor

class IrMixinAccessor(
    override val originatingFiles: List<KSFile>,
    override val className: IrClassName,
    override val side: Side,
    val isAccessibleSchema: Boolean,
    val targetInternalName: String,
    val instanceTypeName: IrTypeName,
    val members: List<IrMixinAccessorMember>,
) : IrMixinRelatedBlueprint(IrJavaClassKind.INTERFACE), IrAccessor

sealed class IrMixinAccessorMember(
    val name: String,
    val isStatic: Boolean,
    val descriptorClassName: IrClassName,
)

class IrMixinAccessorFieldMember(
    name: String,
    val mappingName: String,
    val typeName: IrTypeName,
    isStatic: Boolean,
    val removeFinal: Boolean,
    val ops: List<Op>,
    descriptorClassName: IrClassName,
) : IrMixinAccessorMember(name, isStatic, descriptorClassName)

class IrMixinAccessorMethodMember(
    name: String,
    val mappingName: String,
    val parameters: List<IrParameter>,
    override val returnTypeName: IrTypeName?,
    isStatic: Boolean,
    descriptorClassName: IrClassName,
) : IrMixinAccessorMember(name, isStatic, descriptorClassName), IrReturnable

class IrTweakAccessor(
    val originatingFiles: List<KSFile>,

    val ownerJvmClassName: JvmClassName,
    val entries: List<IrTweakAccessorEntry>,
) : IrAccessor

class IrPatch(
    className: IrClassName,
    val constructorArguments: List<IrPatchConstructorArgument>,
    val impl: IrPatchImpl?,
    val mixin: IrMixin,
) : IrSourceFile(className)

class IrMixin(
    override val originatingFiles: List<KSFile>,
    override val className: IrClassName,
    override val side: Side,
    val targetInstanceTypeName: IrTypeName,
    val isInterfaceTarget: Boolean,
    val targetInternalName: String,
    val injections: List<IrInjection>,
    val externalBridge: IrMixinExternalBridge?,
    val internalBridge: IrMixinInternalBridge?,
) : IrMixinRelatedBlueprint(IrJavaClassKind.CLASS)

sealed interface IrPatchConstructorArgument
object IrPatchConstructorOriginArgument : IrPatchConstructorArgument

class IrPatchImpl(
    override val originatingFiles: List<KSFile>,
    override val className: IrClassName,
    val constructorParameters: List<IrPatchImplConstructorParameter>,
    val initStrategy: InitStrategy,
) : IrKotlinClassBlueprint(IrKotlinClassKind.CLASS)

sealed interface IrPatchImplConstructorParameter
object IrPatchImplConstructorInstanceParameter : IrPatchImplConstructorParameter
object IrPatchImplConstructorInternalBridgeParameter : IrPatchImplConstructorParameter
