package io.github.recrafter.lapis.extensions.ks

import io.github.recrafter.lapis.extensions.ksp.KSPOrigin
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

val KSAnnotation.explicitArguments: List<KSValueArgument>
    get() = arguments.filter { it.origin != KSPOrigin.SYNTHETIC }

inline fun <reified A : Annotation> KSAnnotation.isInstance(): Boolean =
    shortName.getShortName() == A::class.simpleName && annotationType.resolve().declaration.isInstance<A>()

inline fun <reified A : Annotation> KSAnnotation.findArgument(property: KProperty1<A, KClass<*>>): KSValueArgument? =
    arguments.find { it.name?.asString() == property.name }

inline fun <reified A : Annotation> KSAnnotation.getMemberTypeClassDecl(
    property: KProperty1<A, KClass<*>>
): KSClassDecl? =
    findArgument(property)?.getTypeClassDecl()

fun KSAnnotation.getArgumentType(name: String): KSType? =
    annotationType.resolve().getClassDecl()
        ?.propertyDeclarations.orEmpty().find { it.name == name }
        ?.type?.resolve()
