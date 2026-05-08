package io.github.recrafter.lapis.extensions.kp

val List<KPCodeBlock>.format: String
    get() = joinToString { "%L" }
