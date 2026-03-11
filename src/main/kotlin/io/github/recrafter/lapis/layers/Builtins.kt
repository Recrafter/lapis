package io.github.recrafter.lapis.layers

import io.github.recrafter.lapis.LapisMeta
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.extensions.ksp.KSPCodeGenerator
import io.github.recrafter.lapis.extensions.ksp.KSPDependencies
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.types.IrClassType
import io.github.recrafter.lapis.layers.lowering.types.IrVariableType

class Builtins(
    generatedPackageName: String,
    private val codeGenerator: KSPCodeGenerator,
) {
    var isGenerated: Boolean = false
        private set

    private val rootClassType: IrClassType = IrClassType.of(generatedPackageName, LapisMeta.NAME)
    private val classTypes: Map<Builtin, IrClassType> = Builtin.entries.associateWith {
        rootClassType.nested(it.name)
    }

    fun generate() {
        if (isGenerated) {
            return
        }

        buildKotlinFile(rootClassType) {
            suppressWarnings(
                KWarning.RedundantVisibilityModifier,
                KWarning.ObjectInheritsException,
                KWarning.JavaIoSerializableObjectMustHaveReadResolve,
            )
            addType(buildKotlinObject(LapisMeta.NAME) {
                addTypes(Builtin.entries.map { it.generate(::get) })
            })
        }.writeTo(codeGenerator, KSPDependencies.ALL_FILES)

        isGenerated = true
    }

    operator fun get(builtin: Builtin): IrClassType =
        classTypes.getValue(builtin)
}

enum class Builtin {
    Descriptor {
        override fun generate(typer: (Builtin) -> IrClassType): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                val functionVariableType = IrVariableType.of("F", Function::class.asIr().generic(KPStar.asIr()))
                setVariableTypes(functionVariableType)
                setConstructor(listOf(IrParameter("function", functionVariableType)), IrModifier.PRIVATE)
            }
    },
    Callable {
        override fun generate(typer: (Builtin) -> IrClassType): KPClass =
            buildKotlinInterface(name) {
                setModifiers(IrModifier.PUBLIC)
                val descriptorVariableType = IrVariableType.of("D", typer(Descriptor).generic(KPStar.asIr()))
                setVariableTypes(descriptorVariableType)
            }
    },
    Getter {
        override fun generate(typer: (Builtin) -> IrClassType): KPClass =
            buildKotlinInterface(name) {
                setModifiers(IrModifier.PUBLIC)
                val descriptorVariableType = IrVariableType.of("D", typer(Descriptor).generic(KPStar.asIr()))
                setVariableTypes(descriptorVariableType)
            }
    },
    Setter {
        override fun generate(typer: (Builtin) -> IrClassType): KPClass =
            buildKotlinInterface(name) {
                setModifiers(IrModifier.PUBLIC)
                val descriptorVariableType = IrVariableType.of("D", typer(Descriptor).generic(KPStar.asIr()))
                setVariableTypes(descriptorVariableType)
            }
    },
    Context {
        override fun generate(typer: (Builtin) -> IrClassType): KPClass =
            buildKotlinInterface(name) {
                setModifiers(IrModifier.PUBLIC)
                val descriptorVariableType = IrVariableType.of("D", typer(Descriptor).generic(KPStar.asIr()))
                setVariableTypes(descriptorVariableType)
            }
    },
    YieldSignal {
        override fun generate(typer: (Builtin) -> IrClassType): KPClass =
            buildKotlinObject(name) {
                setSuperClass(
                    RuntimeException::class.asIr(),
                    buildKotlinCodeBlock("null"),
                    buildKotlinCodeBlock("null"),
                    buildKotlinCodeBlock("false"),
                    buildKotlinCodeBlock("false")
                )
                addFunction(buildKotlinFunction("fillInStackTrace") {
                    setModifiers(IrModifier.OVERRIDE)
                    setReturnType(Throwable::class.asIr())
                    setBody { return_("this") }
                })
            }
    },
    Patch {
        override fun generate(typer: (Builtin) -> IrClassType): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                val instanceVariableType = IrVariableType.of("I")
                setVariableTypes(instanceVariableType)
                addProperty(buildKotlinProperty("instance", instanceVariableType) {
                    setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                })
            }
    };

    abstract fun generate(typer: (Builtin) -> IrClassType): KPClass
}
