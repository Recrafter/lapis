package io.github.recrafter.lapis.layers.generator.builtins

import io.github.recrafter.lapis.LapisMeta
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KSPCodeGenerator
import io.github.recrafter.lapis.extensions.ksp.KSPDependencies
import io.github.recrafter.lapis.layers.generator.KSuppressWarning
import io.github.recrafter.lapis.layers.lowering.models.IrDesc
import io.github.recrafter.lapis.layers.lowering.models.IrDescWrapper
import io.github.recrafter.lapis.layers.lowering.types.IrClassName

typealias BuiltinTyper = (Builtin) -> IrClassName

class Builtins(
    generatedPackageName: String,
    private val codeGenerator: KSPCodeGenerator,
) {
    var isGenerated: Boolean = false
        private set

    private val rootClassName: IrClassName =
        IrClassName.of(generatedPackageName, LapisMeta.NAME)

    fun generate() {
        if (isGenerated) {
            return
        }
        buildKotlinFile(rootClassName) {
            suppressWarnings(
                KSuppressWarning.RedundantVisibilityModifier,
                KSuppressWarning.ObjectInheritsException,
                KSuppressWarning.JavaIoSerializableObjectMustHaveReadResolve,
            )
            TypeAliasBuiltin.entries.forEach {
                addTypeAlias(it.generate(::get))
            }
            addType(buildKotlinObject(LapisMeta.NAME) {
                addTypes(
                    Builtin.entries.map { it.generate(::get) } + DescBuiltin.entries.map { it.generate(::get) }
                )
            })
        }.writeTo(codeGenerator, KSPDependencies.ALL_FILES)
        isGenerated = true
    }

    operator fun get(builtin: TypeAliasBuiltin): IrClassName =
        IrClassName.of(rootClassName.packageName, builtin.name)

    operator fun get(builtin: Builtin): IrClassName =
        rootClassName.nested(builtin.name)

    operator fun get(builtin: DescBuiltin<*, *>): IrClassName =
        rootClassName.nested(builtin.name)

    fun <D : IrDesc, W : IrDescWrapper> generateDescWrapper(
        dest: KPFileBuilder,
        builtin: DescBuiltin<D, W>,
        wrapper: W
    ) {
        builtin.generateWrapper(dest, wrapper, ::get)
    }
}
