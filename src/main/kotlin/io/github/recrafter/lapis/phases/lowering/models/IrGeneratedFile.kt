package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

abstract class IrGeneratedFile private constructor(val originatingFiles: List<KSFile>) {
    constructor(originatingFile: KSFile?) : this(listOfNotNull(originatingFile))
    constructor(originatingFiles: Iterable<KSFile?>) : this(originatingFiles.filterNotNull())
}

abstract class IrGeneratedSource private constructor(originatingFiles: List<KSFile>) :
    IrGeneratedFile(originatingFiles) {

    abstract val className: IrClassName

    constructor(originatingFile: KSFile?) : this(listOfNotNull(originatingFile))
    constructor(originatingFiles: Iterable<KSFile?>) : this(originatingFiles.filterNotNull())
}
