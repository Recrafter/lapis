package io.github.recrafter.lapis.phases.builtins

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.recrafter.lapis.Lapis
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.kp.KPClass
import io.github.recrafter.lapis.extensions.kp.KPTypeAlias
import io.github.recrafter.lapis.extensions.kp.buildKotlinFile
import io.github.recrafter.lapis.extensions.kp.buildKotlinObject
import io.github.recrafter.lapis.phases.generator.models.GenDescriptorWrapperImplResult
import io.github.recrafter.lapis.phases.lowering.models.IrDescriptorWrapperImpl
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrParameterizedTypeName

class Builtins(
    generatedModPackageName: String,
    private val codeGenerator: CodeGenerator,
) {
    var isExternalGenerated: Boolean = false
        private set

    private var isInternalGenerated: Boolean = false

    private val externalClassName: IrClassName =
        IrClassName.of(generatedModPackageName, Lapis.NAME)

    private val internalClassName: IrClassName =
        IrClassName.of(externalClassName.packageName, externalClassName.simpleName + "Internal")

    private val requestedInternalBuiltins: MutableMap<String, Builtin<*>> = mutableMapOf()

    fun generateExternal() {
        if (isExternalGenerated) {
            lapisError("External builtins already generated")
        }
        buildKotlinFile(externalClassName) {
            val externalBuiltins = Builtin.entries.filter { !it.isInternal }.map { it.generate(::get) }
            externalBuiltins.filterIsInstance<KPTypeAlias>().forEach {
                addTypeAlias(it)
            }
            addType(buildKotlinObject(externalClassName.simpleName) {
                addTypes(externalBuiltins.filterIsInstance<KPClass>())
            })
        }.writeTo(codeGenerator, Dependencies.ALL_FILES)
        isExternalGenerated = true
    }

    fun generateInternal() {
        if (isInternalGenerated) {
            lapisError("Internal builtins already generated")
        }
        if (requestedInternalBuiltins.isEmpty()) {
            return
        }
        buildKotlinFile(internalClassName) {
            val builtins = requestedInternalBuiltins.values.map { it.generate(::get) }
            builtins.filterIsInstance<KPTypeAlias>().forEach(::addTypeAlias)
            addType(buildKotlinObject(internalClassName.simpleName) {
                addTypes(builtins.filterIsInstance<KPClass>())
            })
        }.writeTo(codeGenerator, Dependencies.ALL_FILES)
        isInternalGenerated = true
    }

    operator fun get(builtin: Builtin<*>): IrClassName {
        if (builtin.isInternal) {
            requestedInternalBuiltins.getOrPut(builtin.name) { builtin }
        }
        return (if (builtin.isInternal) internalClassName else externalClassName).inner(builtin.name)
    }

    fun <T : IrDescriptorWrapperImpl<T>> generateDescriptorWrapperImpl(
        impl: T,
        superClassTypeName: IrParameterizedTypeName,
    ): GenDescriptorWrapperImplResult =
        impl.wrapperBuiltin.generateImpl(impl, superClassTypeName, ::get)
}
