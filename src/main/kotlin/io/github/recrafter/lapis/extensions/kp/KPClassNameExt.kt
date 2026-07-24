package io.github.recrafter.lapis.extensions.kp

import io.github.diskria.poetesse.kotlin.KPClassName

val KPClassName.qualifiedName: String
    get() = canonicalName
