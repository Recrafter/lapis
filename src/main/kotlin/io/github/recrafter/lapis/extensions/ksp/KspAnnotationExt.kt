package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.extensions.common.castOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

fun <A : Annotation> KSPAnnotation.findClassArgument(property: KProperty1<A, KClass<*>>): KSPClass? =
    arguments
        .find { it.name?.asString() == property.name }
        ?.value
        ?.castOrNull<KSPType>()
        ?.declaration
        ?.castOrNull<KSPClass>()

inline fun <reified A : Annotation> KSPAnnotation.isInstance(): Boolean =
    shortName.getShortName() == A::class.simpleName && annotationType.resolve().declaration.isInstance<A>()
