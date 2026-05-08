package io.github.recrafter.lapis.extensions.jp

val List<JPCodeBlock>.format: String
    get() = joinToString { "%L" }
