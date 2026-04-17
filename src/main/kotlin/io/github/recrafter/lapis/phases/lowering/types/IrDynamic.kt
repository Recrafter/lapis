package io.github.recrafter.lapis.phases.lowering.types

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.JPTypeName
import io.github.recrafter.lapis.extensions.kp.KPDynamic
import io.github.recrafter.lapis.extensions.quoted

class IrDynamic(override val kotlin: KPDynamic) : IrTypeName(kotlin) {

    override val java: JPTypeName
        get() = lapisError(
            "Dynamic type ${kotlin.toString().quoted()} is not supported in Java, " +
                "but was leaked into IR"
        )
}
