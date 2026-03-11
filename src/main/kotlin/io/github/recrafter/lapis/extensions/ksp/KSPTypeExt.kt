package io.github.recrafter.lapis.extensions.ksp

val KSPType.genericTypes: List<KSPType>
    get() = arguments.mapNotNull { it.type?.resolve() }

fun KSPType.getGenericTypeOrNull(): KSPType? =
    genericTypes.firstOrNull()

fun KSPType.takeNotUnit(): KSPType? =
    takeUnless { it.declaration.isInstance<Unit>() }

fun KSPType?.isSame(other: KSPType?): Boolean {
    val thisName = this?.declaration?.qualifiedName?.asString()
    val otherName = other?.declaration?.qualifiedName?.asString()
    return thisName == otherName
}
