package io.github.recrafter.lapis.extensions.ks

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.common.KSBaseTypes
import io.github.recrafter.lapis.extensions.common.requireQualifiedName
import io.github.recrafter.lapis.phases.parser.helpers.AnnotationArgumentValue
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

fun KSAnnotation.findTypeArgument(name: String = "T"): KSType? =
    annotationType.resolve().findTypeArgument(name)

inline fun <reified A : Annotation> KSAnnotation.isType(): Boolean {
    val qualifiedName = A::class.requireQualifiedName()
    return qualifiedName.endsWith(shortName.asString()) &&
        annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
}

inline fun <reified A : Annotation> KSAnnotation.findArgumentValue(
    property: KProperty1<A, *>,
    explicit: Boolean = false,
): AnnotationArgumentValue? =
    (if (explicit) arguments.filter { it.isExplicit } else arguments)
        .find { it.name?.asString() == property.name }
        ?.value
        ?.let { AnnotationArgumentValue(it, keepDefault = explicit) }

inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
    property: KProperty1<A, Boolean>,
    explicit: Boolean = false,
): Boolean? =
    findArgumentValue(property, explicit)?.asBoolean()

inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
    property: KProperty1<A, Int>,
    explicit: Boolean = false,
): Int? =
    findArgumentValue(property, explicit)?.asInt()

inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
    property: KProperty1<A, Long>,
    explicit: Boolean = false,
): Long? =
    findArgumentValue(property, explicit)?.asLong()

inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
    property: KProperty1<A, Float>,
    explicit: Boolean = false,
): Float? =
    findArgumentValue(property, explicit)?.asFloat()

inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
    property: KProperty1<A, Double>,
    explicit: Boolean = false,
): Double? =
    findArgumentValue(property, explicit)?.asDouble()

inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
    property: KProperty1<A, String>,
    explicit: Boolean = false,
): String? =
    findArgumentValue(property, explicit)?.asString()

inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
    property: KProperty1<A, KClass<*>>,
    baseTypes: KSBaseTypes,
    explicit: Boolean = false,
): KSType? =
    findArgumentValue(property, explicit)?.asClassType(baseTypes)

inline fun <reified A : Annotation, reified E : Enum<E>> KSAnnotation.getArgumentValue(
    property: KProperty1<A, E>,
    explicit: Boolean = false,
): E? =
    findArgumentValue(property, explicit)?.asEnum()

inline fun <reified A : Annotation, reified EA : Annotation> KSAnnotation.getArgumentValue(
    property: KProperty1<A, EA>,
    explicit: Boolean = false,
): KSAnnotation? =
    findArgumentValue(property, explicit)?.asAnnotation()

inline fun <reified A : Annotation> KSAnnotation.getArrayArgumentValue(
    property: KProperty1<A, *>,
    explicit: Boolean = false,
): Iterable<AnnotationArgumentValue>? =
    findArgumentValue(property, explicit)?.asArray()

@JvmName("getIntArrayArgumentValue")
inline fun <reified A : Annotation> KSAnnotation.getArgumentValue(
    property: KProperty1<A, IntArray>,
    explicit: Boolean = false,
): List<Int>? =
    getArrayArgumentValue(property, explicit)?.mapNotNull { it.asInt() }

@JvmName("getEnumArrayArgumentValue")
inline fun <reified A : Annotation, reified E : Enum<E>> KSAnnotation.getArgumentValue(
    property: KProperty1<A, Array<out E>>,
    explicit: Boolean = false,
): List<E>? =
    getArrayArgumentValue(property, explicit)?.mapNotNull { it.asEnum() }
