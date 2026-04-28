package io.github.recrafter.lapis.phases.parser

data class ClassNameBuilder(private val parts: List<String>) {

    val qualifiedName: String get() = parts.joinToString(".")
    val binaryName: String get() = parts.joinToString("$")
    val internalName: String get() = binaryName.replace('.', '/')

    fun nested(name: String): ClassNameBuilder =
        copy(parts = parts + name)

    companion object {
        fun of(qualifiedName: String): ClassNameBuilder =
            ClassNameBuilder(listOf(qualifiedName.replace('/', '.')))
    }
}
