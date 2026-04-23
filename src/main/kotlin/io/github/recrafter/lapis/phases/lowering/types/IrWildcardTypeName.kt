package io.github.recrafter.lapis.phases.lowering.types

import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.JPObject
import io.github.recrafter.lapis.extensions.jp.JPWildcardTypeName
import io.github.recrafter.lapis.extensions.kp.KPStar
import io.github.recrafter.lapis.extensions.kp.KPWildcardTypeName
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.phases.lowering.asIrTypeName

class IrWildcardTypeName(override val kotlin: KPWildcardTypeName) : IrTypeName(kotlin) {

    override val java: JPWildcardTypeName by lazy {
        if (kotlin.inTypes.size > 1 || kotlin.outTypes.size > 1) {
            lapisError(
                "Wildcard type ${kotlin.toString().quoted()} with multiple bounds is not supported in Java, " +
                    "but was leaked into IR"
            )
        }
        if (kotlin == KPStar) {
            return@lazy JPWildcardTypeName.subtypeOf(JPObject)
        }
        val inBound = kotlin.inTypes.singleOrNull()?.asIrTypeName()?.java
        return@lazy if (inBound != null) {
            JPWildcardTypeName.supertypeOf(inBound)
        } else {
            val outBound = kotlin.outTypes.singleOrNull()?.asIrTypeName()?.java
            JPWildcardTypeName.subtypeOf(outBound ?: JPObject)
        }
    }
}
