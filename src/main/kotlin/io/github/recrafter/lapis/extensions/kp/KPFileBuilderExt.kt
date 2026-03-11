package io.github.recrafter.lapis.extensions.kp

fun KPFileBuilder.suppressWarnings(warnings: List<KWarning>) {
    addAnnotation<Suppress> {
        setStringVarargMember(
            Suppress::names,
            *warnings.map { it.suppressionKey }.toTypedArray()
        )
    }
}

fun KPFileBuilder.suppressWarnings(vararg warnings: KWarning) {
    suppressWarnings(warnings.toList())
}

enum class KWarning(private val isScreamingSnake: Boolean = false) {

    RedundantVisibilityModifier,
    UnusedReceiverParameter,
    ObjectInheritsException,
    JavaIoSerializableObjectMustHaveReadResolve,
    NothingToInline(isScreamingSnake = true);

    val suppressionKey: String
        get() = if (isScreamingSnake) {
            name.toScreamingSnake()
        } else {
            name
        }

    private fun String.toScreamingSnake(): String =
        buildString {
            this@toScreamingSnake.forEachIndexed { index, char ->
                if (char.isUpperCase() && index > 0) {
                    append('_')
                }
                append(char.uppercaseChar())
            }
        }
}
