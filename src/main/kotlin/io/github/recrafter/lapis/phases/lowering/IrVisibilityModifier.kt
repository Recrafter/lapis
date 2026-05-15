package io.github.recrafter.lapis.phases.lowering

import io.github.recrafter.lapis.extensions.jp.JPModifier
import io.github.recrafter.lapis.extensions.kp.KPModifier

enum class IrVisibilityModifier(val kotlin: KPModifier, val java: JPModifier) {
    PUBLIC(KPModifier.PUBLIC, JPModifier.PUBLIC),
    PRIVATE(KPModifier.PRIVATE, JPModifier.PRIVATE),
}
