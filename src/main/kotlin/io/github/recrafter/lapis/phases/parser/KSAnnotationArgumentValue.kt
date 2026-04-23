package io.github.recrafter.lapis.phases.parser

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.extensions.common.castOrNull
import io.github.recrafter.lapis.extensions.ks.name
import kotlin.enums.enumEntries

class KSAnnotationArgumentValue(
    val raw: Any,
    private val keepDefault: Boolean = false,
) {
    fun asBoolean(): Boolean? =
        raw.castOrNull<Boolean>()?.filter { !it }

    fun asInt(): Int? =
        raw.castOrNull<Int>()?.filter { it == -1 }

    fun asLong(): Long? =
        raw.castOrNull<Long>()?.filter { it == -1L }

    fun asFloat(): Float? =
        raw.castOrNull<Float>()?.filter { it == -1f }

    fun asDouble(): Double? =
        raw.castOrNull<Double>()?.filter { it == -1.0 }

    fun asString(): String? =
        raw.castOrNull<String>()?.filter { it.isEmpty() }

    fun asKClass(types: KSTypes): KSType? =
        raw.castOrNull<KSType>()?.filter { it.isNothing(types) }

    inline fun <reified E : Enum<E>> asEnum(default: E? = null): E? {
        val entryName = raw.castOrNull<KSClassDeclaration>()?.name?.filter { it == default?.name }
        return enumEntries<E>().find { it.name == entryName }
    }

    fun asAnnotation(): KSAnnotation? =
        raw.castOrNull<KSAnnotation>()

    fun asArray(): Iterable<KSAnnotationArgumentValue>? =
        raw.castOrNull<Iterable<Any>>()
            ?.map { KSAnnotationArgumentValue(it, keepDefault) }
            ?.filter { it.isEmpty() }

    fun <T> T.filter(isDefault: (T) -> Boolean): T? =
        if (keepDefault) this
        else takeUnless { isDefault(it) }
}
