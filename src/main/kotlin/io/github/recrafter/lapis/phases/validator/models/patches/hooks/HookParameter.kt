package io.github.recrafter.lapis.phases.validator.models.patches.hooks

import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.validator.models.schemas.Descriptor
import io.github.recrafter.lapis.phases.validator.models.schemas.FieldDescriptor
import io.github.recrafter.lapis.phases.validator.models.schemas.InvokableDescriptor
import io.github.recrafter.lapis.phases.validator.models.schemas.MethodDescriptor

sealed interface HookParameter

sealed interface HookOriginParameter : HookParameter
sealed class HookOriginDescriptorWrapperParameter(
    open val descriptor: Descriptor
) : HookOriginParameter

class HookOriginBodyDescriptorWrapperParameter(
    override val descriptor: MethodDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

object HookOriginInstanceofWrapperParameter : HookOriginParameter

class HookOriginFieldGetDescriptorWrapperParameter(
    override val descriptor: FieldDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

class HookOriginFieldSetDescriptorWrapperParameter(
    override val descriptor: FieldDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

class HookOriginArrayGetDescriptorWrapperParameter(
    override val descriptor: FieldDescriptor,
    arrayComponentType: KSType,
) : HookOriginDescriptorWrapperParameter(descriptor) {
    val arrayComponentTypeName: IrTypeName = arrayComponentType.asIrTypeName()
}

class HookOriginArraySetDescriptorWrapperParameter(
    override val descriptor: FieldDescriptor,
    arrayComponentType: KSType,
) : HookOriginDescriptorWrapperParameter(descriptor) {
    val arrayComponentTypeName: IrTypeName = arrayComponentType.asIrTypeName()
}

class HookOriginCallDescriptorWrapperParameter(
    override val descriptor: InvokableDescriptor
) : HookOriginDescriptorWrapperParameter(descriptor)

class HookCancelDescriptorWrapperParameter(val descriptor: MethodDescriptor) : HookOriginParameter

object HookOriginValueParameter : HookOriginParameter

object HookOrdinalParameter : HookParameter

sealed class HookLocalParameter(
    val name: String,
    type: KSType,
    val isLocalVar: Boolean,
) : HookParameter {
    val typeName: IrTypeName = type.asIrTypeName()
}

class HookParamLocalParameter(
    name: String,
    type: KSType,
    val index: Int,
    isLocalVar: Boolean,
) : HookLocalParameter(name, type, isLocalVar)

class HookBodyLocalParameter(
    name: String,
    type: KSType,
    val local: HookLocal,
    isLocalVar: Boolean,
) : HookLocalParameter(name, type, isLocalVar)

class HookShareLocalParameter(
    name: String,
    type: KSType,
    val key: String,
    val isExported: Boolean,
) : HookLocalParameter(name, type, true)
