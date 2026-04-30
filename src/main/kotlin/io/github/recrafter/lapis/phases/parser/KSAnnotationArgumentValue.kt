package io.github.recrafter.lapis.phases.parser

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.ks.name
import kotlin.enums.enumEntries

class KSAnnotationArgumentValue(
    val rawValue: Any,
    private val keepDefault: Boolean = false,
) {
    fun asBoolean(): Boolean? =
        rawValue.castOrNull<Boolean>()?.filterDefault { !it }

    fun asInt(): Int? =
        rawValue.castOrNull<Int>()?.filterDefault { it == -1 }

    fun asLong(): Long? =
        rawValue.castOrNull<Long>()?.filterDefault { it == -1L }

    fun asFloat(): Float? =
        rawValue.castOrNull<Float>()?.filterDefault { it == -1f }

    fun asDouble(): Double? =
        rawValue.castOrNull<Double>()?.filterDefault { it == -1.0 }

    fun asString(): String? =
        rawValue.castOrNull<String>()?.filterDefault { it.isEmpty() }

    fun asKClass(types: KSTypes): KSType? =
        rawValue.castOrNull<KSType>()?.filterDefault { it.isNothing(types) }

    inline fun <reified E : Enum<E>> asEnum(default: E? = null): E? {
        val entryName = rawValue.castOrNull<KSClassDeclaration>()?.name?.filterDefault { it == default?.name }
        return enumEntries<E>().find { it.name == entryName }
    }

    fun asAnnotation(): KSAnnotation? =
        rawValue.castOrNull<KSAnnotation>()

    fun asArray(): Iterable<KSAnnotationArgumentValue>? =
        rawValue.castOrNull<Iterable<Any>>()
            ?.map { KSAnnotationArgumentValue(it, keepDefault) }
            ?.filterDefault { it.isEmpty() }

    fun <T> T.filterDefault(isDefault: (T) -> Boolean): T? =
        if (keepDefault) this
        else takeUnless { isDefault(it) }
}
