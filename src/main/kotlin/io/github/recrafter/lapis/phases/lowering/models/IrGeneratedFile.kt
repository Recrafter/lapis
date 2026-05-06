package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

interface IrGeneratedFile {
    val originatingFiles: List<KSFile>
}

interface IrGeneratedSourceFile : IrGeneratedFile {
    val className: IrClassName
}
