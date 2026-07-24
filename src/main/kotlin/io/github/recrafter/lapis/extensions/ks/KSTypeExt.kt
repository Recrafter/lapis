package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.indexOfFirstOrNull

val KSType.isValid: Boolean
    get() = !isError

val KSType.typeArguments: List<KSType>
    get() = arguments.mapNotNull { it.type?.resolve() }

fun KSType.toClassDeclaration(): KSClassDeclaration? =
    declaration.castOrNull<KSClassDeclaration>()

inline fun <reified A : Annotation> KSType.findAnnotation(): KSAnnotation? =
    annotations.find { it.isType<A>() }

inline fun <reified A : Annotation> KSType.hasAnnotation(): Boolean =
    findAnnotation<A>() != null

fun KSType.findTypeArgument(name: String = "T"): KSType? {
    val parameterIndex = toClassDeclaration()
        ?.typeParameters
        ?.indexOfFirstOrNull { it.name.asString() == name }
        ?: return null
    return arguments.getOrNull(parameterIndex)?.type?.resolve()
}
