package io.github.recrafter.lapis.phases.builtins

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.recrafter.lapis.LapisMeta
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.generator.KSuppressWarning
import io.github.recrafter.lapis.phases.lowering.models.IrDescriptorWrapperImpl
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

class Builtins(
    generatedPackageName: String,
    private val codeGenerator: CodeGenerator,
) {
    var isExternalGenerated: Boolean = false
        private set

    private var isInternalGenerated: Boolean = false

    private val externalClassName: IrClassName =
        IrClassName.of(generatedPackageName, LapisMeta.NAME)

    private val internalClassName: IrClassName =
        IrClassName.of(externalClassName.packageName, externalClassName.simpleName + "Internal")

    private val requestedInternalBuiltins: MutableMap<String, Builtin<*>> = mutableMapOf()

    fun generateExternal() {
        if (isExternalGenerated) {
            lapisError("External builtins already generated")
        }
        buildKotlinFile(externalClassName) {
            suppressWarnings(
                KSuppressWarning.RedundantVisibilityModifier,
            )
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
            suppressWarnings(
                KSuppressWarning.RedundantVisibilityModifier,
                KSuppressWarning.ObjectInheritsException,
                KSuppressWarning.JavaIoSerializableObjectMustHaveReadResolve,
            )
            val builtins = requestedInternalBuiltins.values.map { it.generate(::get) }
            builtins.filterIsInstance<KPTypeAlias>().forEach {
                addTypeAlias(it)
            }
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
        val rootClassName = if (builtin.isInternal) internalClassName else externalClassName
        if (builtin is TypeAliasBuiltin) {
            return IrClassName.of(rootClassName.packageName, builtin.name)
        }
        return rootClassName.nested(builtin.name)
    }

    fun <T : IrDescriptorWrapperImpl> generateDescriptorWrapperImpl(
        dest: KPFileBuilder,
        builtin: DescriptorWrapperBuiltin<T>,
        impl: T,
    ) {
        builtin.generateImpl(dest, impl, ::get)
    }
}
