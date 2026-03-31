package io.github.recrafter.lapis.extensions.common

import io.github.recrafter.lapis.LapisMeta
import io.github.recrafter.lapis.ProjektBuildConfig

fun lapisError(message: String): Nothing =
    error(
        "$message. " +
            "This is a ${LapisMeta.NAME} bug. " +
            "Please report it to the issue tracker: " +
            "https://github.com/${ProjektBuildConfig.LIBRARY_DEVELOPER}/${LapisMeta.NAME.lowercase()}/issues/"
    )
