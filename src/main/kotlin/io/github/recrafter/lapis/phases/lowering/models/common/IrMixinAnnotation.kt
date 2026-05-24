package io.github.recrafter.lapis.phases.lowering.models.common

import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class IrMixinAnnotation(
    val className: IrClassName,
    val arguments: List<IrMixinAnnotationArgument>,
)

sealed interface IrMixinAnnotationArgument {
    val name: String
}

class IrMixinAnnotationSingleArgument(
    override val name: String,
    val value: IrMixinAnnotationArgumentValue,
) : IrMixinAnnotationArgument

class IrMixinAnnotationArrayArgument(
    override val name: String,
    val values: List<IrMixinAnnotationArgumentValue>,
) : IrMixinAnnotationArgument

sealed interface IrMixinAnnotationArgumentValue
class IrMixinAnnotationBooleanArgumentValue(
    val boolean: Boolean
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationByteArgumentValue(
    val byte: Byte
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationShortArgumentValue(
    val short: Short
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationIntArgumentValue(
    val int: Int
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationLongArgumentValue(
    val long: Long
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationCharArgumentValue(
    val char: Char
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationFloatArgumentValue(
    val float: Float
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationDoubleArgumentValue(
    val double: Double
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationStringArgumentValue(
    val string: String
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationClassTypeArgumentValue(
    val typeName: IrTypeName
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationEnumArgumentValue(
    val entryClassName: IrClassName,
) : IrMixinAnnotationArgumentValue

class IrMixinAnnotationEmbeddedAnnotationArgumentValue(
    val embeddedAnnotation: IrMixinAnnotation
) : IrMixinAnnotationArgumentValue
