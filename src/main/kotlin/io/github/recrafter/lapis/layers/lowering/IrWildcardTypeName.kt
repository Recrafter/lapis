package io.github.recrafter.lapis.layers.lowering

import io.github.recrafter.lapis.extensions.jp.JPObject
import io.github.recrafter.lapis.extensions.jp.JPWildcardTypeName
import io.github.recrafter.lapis.extensions.kp.KPWildcardTypeName

class IrWildcardTypeName(override val kotlin: KPWildcardTypeName) : IrTypeName(kotlin) {

    override val java: JPWildcardTypeName by lazy {
        if (kotlin.outTypes.size > 1 || kotlin.inTypes.size > 1) {
            error("Java wildcards do not support multiple bounds: $kotlin")
        }
        val outBound = kotlin.outTypes.singleOrNull()?.asIr()?.box()?.java
        val inBound = kotlin.inTypes.singleOrNull()?.asIr()?.box()?.java
        if (inBound != null) {
            JPWildcardTypeName.supertypeOf(inBound)
        } else {
            JPWildcardTypeName.subtypeOf(outBound ?: JPObject)
        }
    }
}
