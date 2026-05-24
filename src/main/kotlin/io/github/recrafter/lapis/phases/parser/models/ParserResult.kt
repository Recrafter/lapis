package io.github.recrafter.lapis.phases.parser.models

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.github.recrafter.lapis.phases.parser.models.patches.ParsedPatch
import io.github.recrafter.lapis.phases.parser.models.schemas.ParsedSchema

class ParserResult(
    val schemas: List<ParsedSchema>,
    val patches: List<ParsedPatch>,
)

class ParserPrepareResult(
    val schemaClassDeclarations: List<KSClassDeclaration>,
    val patchClassDeclarations: List<KSClassDeclaration>,
) {
    val deferredSymbols: List<KSAnnotated> get() = schemaClassDeclarations + patchClassDeclarations
}
