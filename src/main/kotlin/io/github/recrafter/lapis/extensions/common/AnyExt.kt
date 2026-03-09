package io.github.recrafter.lapis.extensions.common

import io.github.recrafter.lapis.extensions.atName
import io.github.recrafter.lapis.extensions.quoted
import kotlin.reflect.KProperty1

inline fun <reified T : Any> Any.castOrNull(): T? =
    this as? T

inline val <reified A : Annotation> KProperty1<A, Int>.defaultValue: Int
    get() = A::class.java.getDeclaredMethod(name).defaultValue as? Int
        ?: lapisError(
            "Property ${name.quoted()} in annotation ${A::class.atName} " +
                "does not have a default value or it is not an Int."
        )
