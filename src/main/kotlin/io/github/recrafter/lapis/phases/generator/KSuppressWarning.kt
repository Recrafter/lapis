package io.github.recrafter.lapis.phases.generator

enum class KSuppressWarning(private val isScreamingSnake: Boolean = false) {

    RedundantVisibilityModifier,
    UnusedReceiverParameter,
    ObjectInheritsException,
    JavaIoSerializableObjectMustHaveReadResolve,
    NothingToInline(isScreamingSnake = true),
    LocalVariableName;

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
