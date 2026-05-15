package io.github.recrafter.lapis.phases.validator.models.schemas

import io.github.recrafter.lapis.annotations.Op
import io.github.recrafter.lapis.phases.lowering.models.IrParameter

sealed interface AccessRequest
class TweakAccessRequest(val shouldRemoveFinal: Boolean) : AccessRequest

sealed interface MixinAccessRequest : AccessRequest
class MixinFieldAccessRequest(
    val shouldRemoveFinal: Boolean,
    val ops: List<Op>,
) : MixinAccessRequest

class MixinInvokableAccessRequest(
    val parameters: List<IrParameter>,
) : MixinAccessRequest
