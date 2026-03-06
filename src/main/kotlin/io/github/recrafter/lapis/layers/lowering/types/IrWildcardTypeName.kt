package io.github.recrafter.lapis.layers.lowering.types

import io.github.recrafter.lapis.extensions.jp.JPObject
import io.github.recrafter.lapis.extensions.jp.JPStar
import io.github.recrafter.lapis.extensions.jp.JPWildcardTypeName
import io.github.recrafter.lapis.extensions.kp.KPStar
import io.github.recrafter.lapis.extensions.kp.KPWildcardTypeName
import io.github.recrafter.lapis.layers.lowering.asIr

class IrWildcardTypeName(override val kotlin: KPWildcardTypeName) : IrTypeName(kotlin) {

    override val java: JPWildcardTypeName by lazy {
        if (kotlin.inTypes.size > 1 || kotlin.outTypes.size > 1) {
            error("Java wildcards support only a single bound, but multiple bounds were found: $kotlin")
        }
        if (kotlin == KPStar) {
            return@lazy JPStar
        }
        val inBound = kotlin.inTypes.singleOrNull()?.asIr()?.java
        return@lazy if (inBound != null) {
            JPWildcardTypeName.supertypeOf(inBound)
        } else {
            val outBound = kotlin.outTypes.singleOrNull()?.asIr()?.java
            JPWildcardTypeName.subtypeOf(outBound ?: JPObject)
        }
    }
}
