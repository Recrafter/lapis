package io.github.recrafter.lapis.extensions.ksp

import io.github.recrafter.lapis.extensions.common.castOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

fun <A : Annotation> KspAnnotation.findClassArgument(property: KProperty1<A, KClass<*>>): KspClassDeclaration? =
    arguments
        .find { it.name?.asString() == property.name }
        ?.value
        ?.castOrNull<KspType>()
        ?.declaration
        ?.castOrNull<KspClassDeclaration>()

inline fun <reified A : Annotation> KspAnnotation.isInstance(): Boolean =
    shortName.getShortName() == A::class.simpleName && annotationType.resolve().declaration.isInstance<A>()
