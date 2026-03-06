package io.github.recrafter.lapis.layers

import com.squareup.kotlinpoet.TypeSpec
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KspCodeGenerator
import io.github.recrafter.lapis.extensions.ksp.KspDependencies
import io.github.recrafter.lapis.extensions.ksp.KspFile
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeVariable

class RuntimeApi(
    private val generatedPackageName: String,
    private val codeGenerator: KspCodeGenerator,
) {
    private val classNames: MutableMap<RuntimeKind, State> = mutableMapOf()

    operator fun get(kind: RuntimeKind): IrClassName =
        classNames.getOrPut(kind) {
            State(IrClassName.of(generatedPackageName, kind.className))
        }.className

    fun generate() {
        classNames.filterValues { !it.isGenerated }.forEach { (kind, state) ->
            buildKotlinFile(state.className) {
                addType(kind.generate { get(it) })
            }.writeTo(codeGenerator, KspDependencies(false, *state.originatingFiles.toTypedArray()))
            state.isGenerated = true
        }
    }

    class State(
        val className: IrClassName,
        var isGenerated: Boolean = false,
        val originatingFiles: List<KspFile> = emptyList(),
    )
}

sealed class RuntimeKind(val className: String) {
    abstract fun generate(namer: (RuntimeKind) -> IrClassName): TypeSpec
}

data object ApiDescriptor : RuntimeKind("LapisDescriptor") {
    override fun generate(namer: (RuntimeKind) -> IrClassName): TypeSpec =
        buildKotlinClass(className) {
            setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
            val functionTypeVariable = IrTypeVariable.of(
                "F", Function::class.asIr().parameterizedBy(KPStar.asIr())
            )
            setTypeVariables(functionTypeVariable)
            setConstructor(
                listOf(IrParameter("function", functionTypeVariable)), IrModifier.PRIVATE
            )
        }
}

data object ApiPatch : RuntimeKind("LapisPatch") {
    override fun generate(namer: (RuntimeKind) -> IrClassName): TypeSpec =
        buildKotlinClass(className) {
            setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
            val instanceTypeVariable = IrTypeVariable.of("I")
            setTypeVariables(instanceTypeVariable)
            addProperty(buildKotlinProperty("instance", instanceTypeVariable) {
                setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
            })
        }
}

data object ApiContext : RuntimeKind("LapisContext") {
    override fun generate(namer: (RuntimeKind) -> IrClassName): TypeSpec =
        buildKotlinInterface(className) {
            setModifiers(IrModifier.PUBLIC)
            setTypeVariables(
                IrTypeVariable.of(
                    "D", namer(ApiDescriptor).parameterizedBy(KPStar.asIr())
                )
            )
        }
}

data object ApiYieldSignal : RuntimeKind("LapisYieldSignal") {
    override fun generate(namer: (RuntimeKind) -> IrClassName): TypeSpec =
        buildKotlinObject(className) {
            setSuperClass(
                RuntimeException::class.asIr(),
                buildKotlinCodeBlock(null.toString()),
                buildKotlinCodeBlock(null.toString()),
                buildKotlinCodeBlock(false.toString()),
                buildKotlinCodeBlock(false.toString()),
            )
            addFunction(buildKotlinFunction(RuntimeException::fillInStackTrace.name) {
                setModifiers(IrModifier.OVERRIDE)
                setReturnType(Throwable::class.asIr())
                setBody { return_("this") }
            })
        }
}
