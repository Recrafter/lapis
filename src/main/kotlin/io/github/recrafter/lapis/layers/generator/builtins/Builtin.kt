package io.github.recrafter.lapis.layers.generator.builtins

import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.models.IrParameter
import io.github.recrafter.lapis.layers.lowering.types.IrTypeVariableName
import kotlin.reflect.KProperty

enum class Builtin {
    Desc {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinInterface(name) {
                setModifiers(IrModifier.PRIVATE, IrModifier.SEALED)
            }
    },
    Field {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                val fieldTypeVariableName = IrTypeVariableName.of("T")
                setVariableTypes(fieldTypeVariableName)
                setConstructor(
                    listOf(
                        IrParameter(
                            "callable",
                            KProperty::class.asIr().parameterizedBy(fieldTypeVariableName)
                        )
                    )
                )
                addSuperInterface(typer(Desc))
            }
    },
    Callable {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.SEALED)
                val functionTypeVariableName = IrTypeVariableName.of(
                    "F",
                    Function::class.asIr().parameterizedBy(KPStar.asIr())
                )
                setVariableTypes(functionTypeVariableName)
                setConstructor(listOf(IrParameter("callable", functionTypeVariableName)))
                addSuperInterface(typer(Desc))
            }
    },
    Method {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                val functionTypeVariableName = IrTypeVariableName.of(
                    "F",
                    Function::class.asIr().parameterizedBy(KPStar.asIr())
                )
                setVariableTypes(functionTypeVariableName)
                val functionParameter = IrParameter("callable", functionTypeVariableName)
                setConstructor(listOf(functionParameter))
                setSuperClass(
                    typer(Callable).parameterizedBy(functionTypeVariableName),
                    listOf(buildKotlinCodeBlock("%N") { arg(functionParameter) })
                )
            }
    },
    Constructor {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                val functionTypeVariableName = IrTypeVariableName.of(
                    "F",
                    Function::class.asIr().parameterizedBy(KPStar.asIr())
                )
                setVariableTypes(functionTypeVariableName)
                val functionParameter = IrParameter("callable", functionTypeVariableName)
                setConstructor(listOf(functionParameter))
                setSuperClass(
                    typer(Callable).parameterizedBy(functionTypeVariableName),
                    listOf(buildKotlinCodeBlock("%N") { arg(functionParameter) })
                )
            }
    },
    Patch {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                val instanceTypeVariableName = IrTypeVariableName.of("I")
                setVariableTypes(instanceTypeVariableName)
                addProperty(buildKotlinProperty("instance", instanceTypeVariableName) {
                    setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                })
            }
    },
    CancelSignal {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinObject(name) {
                setModifiers(IrModifier.PUBLIC)
                setSuperClass(
                    RuntimeException::class.asIr(),
                    listOf(
                        buildKotlinCodeBlock("null"),
                        buildKotlinCodeBlock("null"),
                        buildKotlinCodeBlock("false"),
                        buildKotlinCodeBlock("false"),
                    )
                )
                addFunction(buildKotlinFunction(RuntimeException::fillInStackTrace.name) {
                    setModifiers(IrModifier.OVERRIDE)
                    setReturnType(Throwable::class.asIr())
                    setBody { return_("this") }
                })
            }
    };

    abstract fun generate(typer: BuiltinTyper): KPClass
}
