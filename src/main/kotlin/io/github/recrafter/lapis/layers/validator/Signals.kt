@file:Suppress("ObjectInheritsException", "JavaIoSerializableObjectMustHaveReadResolve")

package io.github.recrafter.lapis.layers.validator

sealed class Signal : RuntimeException(null, null, false, false)

object ValidationErrorSignal : Signal()
