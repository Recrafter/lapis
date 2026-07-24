package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.diskria.poetesse.java.JPTypeKind
import io.github.diskria.poetesse.kotlin.KPTypeKind
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

interface IrBlueprint {
    val originatingFiles: List<KSFile>
}

abstract class IrKotlinClassBlueprint(val typeKind: KPTypeKind) : IrBlueprint {
    abstract val className: IrClassName
}

abstract class IrKotlinFileBlueprint(val packageName: String?, val fileName: String) : IrBlueprint

abstract class IrJavaFileBlueprint(val typeKind: JPTypeKind) : IrBlueprint {
    abstract val className: IrClassName
}

abstract class IrResourceBlueprint(val path: String) : IrBlueprint
