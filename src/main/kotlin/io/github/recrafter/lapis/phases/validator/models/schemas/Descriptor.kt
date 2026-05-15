package io.github.recrafter.lapis.phases.validator.models.schemas

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.phases.common.JvmClassName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.validator.models.common.SourceFile

sealed class Descriptor(
    symbol: KSNode,
    classDeclaration: KSClassDeclaration,

    val name: String,
    val mappingName: String,
    receiverType: KSType,
    val inaccessibleReceiverJvmClassName: JvmClassName?,
    val functionTypeParameters: List<FunctionTypeParameter>,
    val returnType: KSType?,
    val isStatic: Boolean,
    val accessRequest: AccessRequest?,
) : SourceFile(symbol, classDeclaration) {
    val receiverTypeName: IrTypeName = receiverType.asIrTypeName()
    val returnTypeName: IrTypeName? = returnType?.asIrTypeName()
}

class FieldDescriptor(
    symbol: KSNode,

    name: String,
    mappingName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    inaccessibleReceiverJvmClassName: JvmClassName?,
    val fieldType: KSType,
    val arrayComponentType: KSType?,
    isStatic: Boolean,
    accessRequest: AccessRequest?,
) : Descriptor(
    symbol,
    classDeclaration,
    name,
    mappingName,
    receiverType,
    inaccessibleReceiverJvmClassName,
    emptyList(),
    fieldType,
    isStatic,
    accessRequest,
) {
    val fieldTypeName: IrTypeName = fieldType.asIrTypeName()
}

sealed class InvokableDescriptor(
    symbol: KSNode,

    name: String,
    mappingName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    inaccessibleReceiverJvmClassName: JvmClassName?,
    parameters: List<FunctionTypeParameter>,
    returnType: KSType?,
    isStatic: Boolean,
    accessRequest: AccessRequest?,
) : Descriptor(
    symbol,
    classDeclaration,
    name,
    mappingName,
    receiverType,
    inaccessibleReceiverJvmClassName,
    parameters,
    returnType,
    isStatic,
    accessRequest,
)

open class MethodDescriptor(
    symbol: KSNode,

    name: String,
    mappingName: String,
    classDeclaration: KSClassDeclaration,
    receiverType: KSType,
    inaccessibleReceiverJvmClassName: JvmClassName?,
    returnType: KSType?,
    parameters: List<FunctionTypeParameter>,
    isStatic: Boolean,
    accessRequest: AccessRequest?,
) : InvokableDescriptor(
    symbol,
    name,
    mappingName,
    classDeclaration,
    receiverType,
    inaccessibleReceiverJvmClassName,
    parameters,
    returnType,
    isStatic,
    accessRequest,
)

class ConstructorDescriptor(
    symbol: KSNode,

    name: String,
    classDeclaration: KSClassDeclaration,
    returnType: KSType,
    parameters: List<FunctionTypeParameter>,
    accessRequest: AccessRequest?,
) : InvokableDescriptor(
    symbol, name, "", classDeclaration, returnType, null, parameters, returnType, false, accessRequest,
)

class FunctionTypeParameter(val name: String?, val type: KSType) {
    val typeName: IrTypeName = type.asIrTypeName()
}
