package io.github.recrafter.lapis.layers.validator

import io.github.recrafter.lapis.extensions.ksp.KspLogger
import io.github.recrafter.lapis.layers.lowering.IrDescriptorImpl
import io.github.recrafter.lapis.layers.lowering.IrMixin
import java.io.File

class BackendValidator(val minecraftJars: List<File>, val logger: KspLogger) {

    fun validate(descriptorImpls: List<IrDescriptorImpl>, rootMixins: List<IrMixin>) {
        // TODO ASM analysis
    }
}
