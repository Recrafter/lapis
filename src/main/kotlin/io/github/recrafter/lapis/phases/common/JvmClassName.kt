package io.github.recrafter.lapis.phases.common

data class JvmClassName(
    val packageName: String,
    private val parts: List<String>,
) {
    val nestedName: String get() = parts.joinToString(".")
    val qualifiedName: String get() = nestedName.withPackageName()
    val binaryName: String get() = parts.joinToString("$").withPackageName()
    val internalName: String get() = binaryName.replace('.', '/')
    val descriptor: String get() = "L$internalName;"

    fun inner(name: String): JvmClassName =
        copy(parts = parts + name)

    fun local(index: Int, name: String): JvmClassName =
        copy(parts = parts + "$index$name")

    fun anonymous(index: Int): JvmClassName =
        copy(parts = parts + index.toString())

    private fun String.withPackageName(): String =
        if (packageName.isEmpty()) this
        else "$packageName.$this"

    companion object {
        fun of(name: String): JvmClassName {
            val normalized = name.replace('/', '.')
            val (packageName, classes) = if (normalized.contains('.')) {
                normalized.substringBeforeLast('.') to normalized.substringAfterLast('.')
            } else {
                "" to normalized
            }
            return JvmClassName(
                packageName = packageName,
                parts = classes.split('$'),
            )
        }
    }
}
