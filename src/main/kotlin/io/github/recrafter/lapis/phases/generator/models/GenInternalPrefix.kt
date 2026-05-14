package io.github.recrafter.lapis.phases.generator.models

import io.github.recrafter.lapis.LapisMeta

enum class GenInternalPrefix(val value: String) {
    BUILTIN(LapisMeta.NAME.lowercase()),
    PARAM("param"),
    LOCAL("local"),
    SHARE("share"),
    ARGUMENT("argument"),
    ACCESS("access"),
}
