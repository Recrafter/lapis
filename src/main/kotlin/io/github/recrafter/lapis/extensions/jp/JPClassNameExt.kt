package io.github.recrafter.lapis.extensions.jp

val JPClassName.binaryName: String get() = reflectionName()

val JPClassName.internalName: String get() = binaryName.replace('.', '/')

val JPClassName.qualifiedName: String get() = canonicalName()
