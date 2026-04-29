package io.github.recrafter.lapis.phases.parser

data class ClassNameBuilder(private val parts: List<String>) {

    val qualifiedName: String get() = parts.joinToString(".")
    val binaryName: String get() = parts.joinToString("$")
    val internalName: String get() = binaryName.replace('.', '/')

    fun inner(name: String): ClassNameBuilder =
        copy(parts = parts + name)

    fun local(index: Int, name: String): ClassNameBuilder =
        copy(parts = parts + "$index$name")

    fun anonymous(index: Int): ClassNameBuilder =
        copy(parts = parts + index.toString())

    companion object {
        fun of(qualifiedName: String): ClassNameBuilder =
            ClassNameBuilder(listOf(qualifiedName.replace('/', '.')))
    }
}
