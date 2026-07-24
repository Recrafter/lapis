package io.github.recrafter.lapis.extensions.kp

import io.github.diskria.poetesse.kotlin.KPCodeBlock

val List<KPCodeBlock>.format: String
    get() = joinToString { "%L" }
