package io.github.recrafter.lapis.phases.generator.models

import io.github.recrafter.lapis.Lapis

enum class GenInternalPrefix(val value: String) {
    BUILTIN(Lapis.NAME.lowercase()),
    PARAM("param"),
    LOCAL("local"),
    SHARE("share"),
    ARGUMENT("argument"),
    ACCESS("access"),
}
