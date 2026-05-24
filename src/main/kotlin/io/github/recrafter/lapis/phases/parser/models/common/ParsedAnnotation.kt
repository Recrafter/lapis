package io.github.recrafter.lapis.phases.parser.models.common

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

class ParsedAnnotation(
    val typeClassDeclaration: KSClassDeclaration?,
    val isSourceRetention: Boolean,
    val arguments: List<ParsedAnnotationArgument>,
)

sealed interface ParsedAnnotationArgument {
    val name: String
    val isExplicit: Boolean
}

class ParsedAnnotationSingleArgument(
    override val name: String,
    override val isExplicit: Boolean,
    val value: ParsedAnnotationArgumentValue,
) : ParsedAnnotationArgument

class ParsedAnnotationArrayArgument(
    override val name: String,
    override val isExplicit: Boolean,
    val values: List<ParsedAnnotationArgumentValue>,
) : ParsedAnnotationArgument

sealed interface ParsedAnnotationArgumentValue
class ParsedAnnotationBooleanArgumentValue(
    val boolean: Boolean
) : ParsedAnnotationArgumentValue

class ParsedAnnotationByteArgumentValue(
    val byte: Byte
) : ParsedAnnotationArgumentValue

class ParsedAnnotationShortArgumentValue(
    val short: Short
) : ParsedAnnotationArgumentValue

class ParsedAnnotationIntArgumentValue(
    val int: Int
) : ParsedAnnotationArgumentValue

class ParsedAnnotationLongArgumentValue(
    val long: Long
) : ParsedAnnotationArgumentValue

class ParsedAnnotationCharArgumentValue(
    val char: Char
) : ParsedAnnotationArgumentValue

class ParsedAnnotationFloatArgumentValue(
    val float: Float
) : ParsedAnnotationArgumentValue

class ParsedAnnotationDoubleArgumentValue(
    val double: Double
) : ParsedAnnotationArgumentValue

class ParsedAnnotationStringArgumentValue(
    val string: String
) : ParsedAnnotationArgumentValue

class ParsedAnnotationClassTypeArgumentValue(
    val type: KSType
) : ParsedAnnotationArgumentValue

class ParsedAnnotationEnumArgumentValue(
    val entryClassDeclaration: KSClassDeclaration,
) : ParsedAnnotationArgumentValue

class ParsedAnnotationEmbeddedAnnotationArgumentValue(
    val embeddedAnnotation: ParsedAnnotation
) : ParsedAnnotationArgumentValue
