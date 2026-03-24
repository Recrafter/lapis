package io.github.recrafter.lapis.layers.lowering.types

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.JPObject
import io.github.recrafter.lapis.extensions.jp.JPStar
import io.github.recrafter.lapis.extensions.jp.JPWildcardType
import io.github.recrafter.lapis.extensions.kp.KPStar
import io.github.recrafter.lapis.extensions.kp.KPWildcardType
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.lowering.asIr

class IrWildcardTypeName(override val kotlin: KPWildcardType) : IrTypeName(kotlin) {

    override val java: JPWildcardType by lazy {
        if (kotlin.inTypes.size > 1 || kotlin.outTypes.size > 1) {
            lapisError(
                "Wildcard type ${kotlin.toString().quoted()} with multiple bounds is not supported, " +
                    "but was leaked into IR"
            )
        }
        if (kotlin == KPStar) {
            return@lazy JPStar
        }
        val inBound = kotlin.inTypes.singleOrNull()?.asIr()?.java
        return@lazy if (inBound != null) {
            JPWildcardType.supertypeOf(inBound)
        } else {
            val outBound = kotlin.outTypes.singleOrNull()?.asIr()?.java
            JPWildcardType.subtypeOf(outBound ?: JPObject)
        }
    }
}
