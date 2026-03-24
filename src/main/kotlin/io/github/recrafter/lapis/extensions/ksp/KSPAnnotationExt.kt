package io.github.recrafter.lapis.extensions.ksp

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

val KSPAnnotation.explicitArguments: List<KSPValueArgument>
    get() = arguments.filter { it.origin != KSPOrigin.SYNTHETIC }

inline fun <reified A : Annotation> KSPAnnotation.isInstance(): Boolean =
    shortName.getShortName() == A::class.simpleName && annotationType.resolve().declaration.isInstance<A>()

inline fun <reified A : Annotation> KSPAnnotation.getClassDeclValue(property: KProperty1<A, KClass<*>>): KSPClassDecl? =
    arguments.find { it.name?.asString() == property.name }?.getClassDeclValue()

fun KSPAnnotation.getArgumentType(name: String): KSPType? =
    annotationType.resolve().toClassDeclOrNull()?.propertyDeclarations.orEmpty()
        .find { it.name == name }?.type?.resolve()
