package io.github.recrafter.lapis.extensions.common

import io.github.recrafter.lapis.LapisMeta
import io.github.recrafter.lapis.extensions.quoted

fun <T> unsafeLazy(initializer: () -> T): Lazy<T> =
    lazy(LazyThreadSafetyMode.NONE, initializer)

fun lapisError(message: String): Nothing =
    error(
        "Internal error: ${message.quoted()}. " +
            "This is a ${LapisMeta.NAME} bug. " +
            "Please report it to the issue tracker: " +
            "https://github.com/Recrafter/${LapisMeta.NAME}/issues"
    )
