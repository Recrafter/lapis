package io.github.recrafter.lapis.extensions.jp

val JPClassType.binaryName: String get() = reflectionName()
val JPClassType.internalName: String get() = binaryName.replace('.', '/')
