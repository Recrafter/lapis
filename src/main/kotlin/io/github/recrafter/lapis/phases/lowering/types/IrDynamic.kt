package io.github.recrafter.lapis.phases.lowering.types

import io.github.diskria.poetesse.java.JPTypeName
import io.github.diskria.poetesse.kotlin.KPDynamic
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.quoted

class IrDynamic(override val kotlin: KPDynamic) : IrTypeName(kotlin) {

    override val java: JPTypeName
        get() = lapisError(
            "Dynamic type ${kotlin.toString().quoted()} is not supported in Java, " +
                "but was leaked into IR"
        )
}
