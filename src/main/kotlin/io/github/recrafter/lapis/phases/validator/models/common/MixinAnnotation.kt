package io.github.recrafter.lapis.phases.validator.models.common

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName

class MixinAnnotation(
    typeClassDeclaration: KSClassDeclaration,
    val arguments: List<MixinAnnotationArgument>,
) {
    val className: IrClassName = typeClassDeclaration.asIrClassName()
}

sealed interface MixinAnnotationArgument {
    val name: String
}

class MixinAnnotationSingleArgument(
    override val name: String,
    val value: MixinAnnotationArgumentValue,
) : MixinAnnotationArgument

class MixinAnnotationArrayArgument(
    override val name: String,
    val values: List<MixinAnnotationArgumentValue>,
) : MixinAnnotationArgument

sealed interface MixinAnnotationArgumentValue
class MixinAnnotationBooleanArgumentValue(
    val boolean: Boolean
) : MixinAnnotationArgumentValue

class MixinAnnotationByteArgumentValue(
    val byte: Byte
) : MixinAnnotationArgumentValue

class MixinAnnotationShortArgumentValue(
    val short: Short
) : MixinAnnotationArgumentValue

class MixinAnnotationIntArgumentValue(
    val int: Int
) : MixinAnnotationArgumentValue

class MixinAnnotationLongArgumentValue(
    val long: Long
) : MixinAnnotationArgumentValue

class MixinAnnotationCharArgumentValue(
    val char: Char
) : MixinAnnotationArgumentValue

class MixinAnnotationFloatArgumentValue(
    val float: Float
) : MixinAnnotationArgumentValue

class MixinAnnotationDoubleArgumentValue(
    val double: Double
) : MixinAnnotationArgumentValue

class MixinAnnotationStringArgumentValue(
    val string: String
) : MixinAnnotationArgumentValue

class MixinAnnotationClassTypeArgumentValue(type: KSType) : MixinAnnotationArgumentValue {
    val typeName: IrTypeName = type.asIrTypeName()
}

class MixinAnnotationEnumArgumentValue(
    entryClassDeclaration: KSClassDeclaration,
) : MixinAnnotationArgumentValue {
    val entryClassName: IrClassName = entryClassDeclaration.asIrClassName()
}

class MixinAnnotationEmbeddedAnnotationArgumentValue(
    val embeddedAnnotation: MixinAnnotation
) : MixinAnnotationArgumentValue
