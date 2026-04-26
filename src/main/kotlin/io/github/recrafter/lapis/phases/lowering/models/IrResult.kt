package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.annotations.Side
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

class IrResult(
    val schemas: List<IrSchema>,
    val mixins: List<IrMixin>,
)

class IrSchema(
    val containingFile: KSFile?,

    val makePublic: Boolean,
    val removeFinal: Boolean,
    val className: IrClassName,
    val originClassName: IrClassName,
    val descriptors: List<IrDescriptor>,
)

class IrMixin(
    val containingFile: KSFile?,

    val className: IrClassName,
    val patchClassName: IrClassName,
    val patchImplClassName: IrClassName,
    val instanceClassName: IrClassName,
    val bytecodeTargetName: String,

    val side: Side,
    val extension: IrExtension?,
    val injections: List<IrInjection>,
) {
    fun isNotEmpty(): Boolean =
        extension != null || injections.isNotEmpty()
}
