package io.github.recrafter.lapis.extensions.ksp

val KspType.genericTypes: List<KspType>
    get() = arguments.mapNotNull { it.type?.resolve() }

fun KspType.getGenericTypeOrNull(): KspType? =
    genericTypes.firstOrNull()

fun KspType.takeNotUnit(): KspType? =
    takeUnless { it.declaration.isInstance<Unit>() }
