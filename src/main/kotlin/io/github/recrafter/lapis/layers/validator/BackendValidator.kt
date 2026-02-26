package io.github.recrafter.lapis.layers.validator

import io.github.recrafter.lapis.extensions.ksp.KspLogger
import io.github.recrafter.lapis.layers.lowering.IrDescriptor
import io.github.recrafter.lapis.layers.lowering.IrMixin
import java.io.File

class BackendValidator(private val minecraftJars: List<File>, private val logger: KspLogger) {

    fun validate(descriptors: List<IrDescriptor>, mixins: List<IrMixin>) {
        // TODO ASM analysis
    }
}
