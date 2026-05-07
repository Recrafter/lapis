package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

interface IrBlueprint {
    val originatingFiles: List<KSFile>
}

abstract class IrKotlinBlueprint : IrBlueprint {
    abstract val className: IrClassName
}

abstract class IrJavaBlueprint(val isInterface: Boolean) : IrBlueprint {
    abstract val className: IrClassName
}

abstract class IrResourceBlueprint(val path: String) : IrBlueprint
