package io.github.recrafter.lapis.phases.lowering.models

import com.google.devtools.ksp.symbol.KSFile

abstract class IrGeneratedSource private constructor(val originatingFiles: List<KSFile>) {
    constructor(originatingFile: KSFile?) : this(listOfNotNull(originatingFile))
    constructor(originatingFiles: Iterable<KSFile?>) : this(originatingFiles.filterNotNull())
}
