package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

interface IrBlueprint {
    val originatingFiles: List<KSFile>
}

abstract class IrKotlinClassBlueprint(val classKind: IrKotlinClassKind) : IrBlueprint {
    abstract val className: IrClassName
}

abstract class IrKotlinFileBlueprint(val packageName: String, val fileName: String) : IrBlueprint

enum class IrKotlinClassKind { CLASS, INTERFACE, OBJECT }

abstract class IrJavaBlueprint(val classKind: IrJavaClassKind) : IrBlueprint {
    abstract val className: IrClassName
}

enum class IrJavaClassKind { CLASS, INTERFACE }

abstract class IrResourceBlueprint(val path: String) : IrBlueprint
