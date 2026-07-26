package io.github.recrafter.lapis.phases.parser.models.patches

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.annotations.Ats
import io.github.recrafter.lapis.annotations.ConstructorHeadPhase
import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.annotations.ZeroCondition
import io.github.recrafter.lapis.phases.parser.models.common.ParsedAnnotation
import io.github.recrafter.lapis.phases.parser.models.common.SymbolSource
import javax.lang.model.element.Modifier

class ParsedPatchFunction(
    override val symbol: KSNode,

    val name: String,
    val jvmName: String?,
    val parameters: List<ParsedPatchFunctionParameter>,
    val returnType: KSType?,
    val hasTypeParameters: Boolean,

    val isPublic: Boolean,
    val isOpen: Boolean,
    val isAbstract: Boolean,
    val extensionReceiverClassDeclaration: KSClassDeclaration?,

    val hasExtensionAnnotation: Boolean,
    val hasShadowAnnotation: Boolean,
    val explicitMappingName: String?,
    val shadowModifiers: List<Modifier>,

    val hasHookAnnotation: Boolean,
    val hookDescClassDeclaration: KSClassDeclaration?,
    val hookAt: Ats?,

    val hasAtConstructorHeadAnnotation: Boolean,
    val atConstructorHeadPhase: ConstructorHeadPhase?,

    val hasAtLocalAnnotation: Boolean,
    val atLocalOp: Op?,
    val atLocalType: KSType?,
    val explicitAtLocalName: String?,
    val explicitAtLocalOrdinal: Int?,
    val atLocalOpOrdinals: List<Int>,

    val hasAtInstanceofAnnotation: Boolean,
    val atInstanceofTypeClassDeclaration: KSClassDeclaration?,
    val atInstanceofOrdinals: List<Int>,

    val hasAtReturnAnnotation: Boolean,
    val atReturnOrdinals: List<Int>,

    val hasAtLiteralAnnotation: Boolean,
    val explicitAtLiteralZero: KSAnnotation?,
    val atLiteralZeroConditions: List<ZeroCondition>,
    val explicitAtLiteralInt: Int?,
    val explicitAtLiteralLong: Long?,
    val explicitAtLiteralFloat: Float?,
    val explicitAtLiteralDouble: Double?,
    val explicitAtLiteralString: String?,
    val explicitAtLiteralType: KSType?,
    val explicitAtLiteralTypeClassDeclaration: KSClassDeclaration?,
    val isExplicitAtLiteralNull: Boolean,
    val atLiteralOrdinals: List<Int>,

    val hasAtFieldAnnotation: Boolean,
    val atFieldOp: Op?,
    val atFieldDescClassDeclaration: KSClassDeclaration?,
    val atFieldOrdinals: List<Int>,

    val hasAtArrayAnnotation: Boolean,
    val atArrayOp: Op?,
    val atArrayDescClassDeclaration: KSClassDeclaration?,
    val atArrayOrdinals: List<Int>,

    val hasAtCallAnnotation: Boolean,
    val atCallDescClassDeclaration: KSClassDeclaration?,
    val atCallOrdinals: List<Int>,

    val annotations: List<ParsedAnnotation>,
) : SymbolSource {

    fun hasOrdinals(): Boolean {
        val allOrdinals = atLocalOpOrdinals + atInstanceofOrdinals + atReturnOrdinals + atLiteralOrdinals +
            atFieldOrdinals + atArrayOrdinals + atCallOrdinals
        return allOrdinals.isNotEmpty()
    }
}
