package io.github.recrafter.lapis.extensions.jp

import io.github.diskria.poetesse.java.JPCodeBlock

val List<JPCodeBlock>.format: String
    get() = joinToString { "%L" }
