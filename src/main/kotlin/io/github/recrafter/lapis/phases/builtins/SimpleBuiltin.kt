package io.github.recrafter.lapis.phases.builtins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.asIrWildcardTypeName
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName
import kotlin.reflect.KProperty

enum class SimpleBuiltin(override val isInternal: Boolean = false) : Builtin<KPClass> {
    Descriptor {
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
                            KProperty::class.asIrClassName().parameterizedBy(fieldTypeVariableName)
                        )
                    )
                )
                addSuperInterface(typer(Descriptor))
            }
    },
    Callable {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.SEALED)
                val functionTypeVariableName = IrTypeVariableName.of(
                    "F",
                    Function::class.asIrClassName().parameterizedBy(KPStar.asIrWildcardTypeName())
                )
                setVariableTypes(functionTypeVariableName)
                setConstructor(IrParameter("callable", functionTypeVariableName))
                addSuperInterface(typer(Descriptor))
            }
    },
    Method {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                val functionTypeVariableName = IrTypeVariableName.of(
                    "F",
                    Function::class.asIrClassName().parameterizedBy(KPStar.asIrWildcardTypeName())
                )
                setVariableTypes(functionTypeVariableName)
                val functionParameter = IrParameter("callable", functionTypeVariableName)
                setConstructor(functionParameter)
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
                    Function::class.asIrClassName().parameterizedBy(KPStar.asIrWildcardTypeName())
                )
                setVariableTypes(functionTypeVariableName)
                val functionParameter = IrParameter("callable", functionTypeVariableName)
                setConstructor(functionParameter)
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
    LocalVar {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinInterface(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.SEALED)
                val localTypeVariableName = IrTypeVariableName.of("T")
                setVariableTypes(localTypeVariableName)
                addProperty(buildKotlinProperty("value", localTypeVariableName) {
                    setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
                    mutable(true)
                })
            }
    },
    Instanceof {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC)
                val valueParameter = IrParameter(
                    "value",
                    KPAny.asIrClassName(),
                    listOf(IrModifier.PUBLIC)
                )
                val operationParameter = IrParameter(
                    "operation",
                    Operation::class.asIrClassName().parameterizedBy(KPBoolean.asIrClassName()),
                    listOf(IrModifier.PRIVATE)
                )
                setConstructor(valueParameter, operationParameter)
                addFunction(buildKotlinFunction("invoke") {
                    setModifiers(IrModifier.PUBLIC, IrModifier.OPERATOR)
                    val valueParameter = buildKotlinParameter("value", valueParameter.typeName) {
                        defaultValue(buildKotlinCodeBlock("this.%N") {
                            arg(valueParameter)
                        })
                    }
                    addParameter(valueParameter)
                    setReturnType(KPBoolean.asIrClassName())
                    setBody {
                        return_("%N.%L(%N)") {
                            arg(operationParameter)
                            arg(Operation<*>::call)
                            arg(valueParameter)
                        }
                    }
                })
            }
    },
    CancelSignal(isInternal = true) {
        override fun generate(typer: BuiltinTyper): KPClass =
            buildKotlinObject(name) {
                setModifiers(IrModifier.PUBLIC)
                setSuperClass(
                    RuntimeException::class.asIrClassName(),
                    listOf(
                        buildKotlinCodeBlock("null"),
                        buildKotlinCodeBlock("null"),
                        buildKotlinCodeBlock("false"),
                        buildKotlinCodeBlock("false"),
                    )
                )
                addFunction(buildKotlinFunction(RuntimeException::fillInStackTrace.name) {
                    setModifiers(IrModifier.OVERRIDE)
                    setReturnType(Throwable::class.asIrClassName())
                    setBody { return_("this") }
                })
            }
    };

    abstract override fun generate(typer: BuiltinTyper): KPClass
}
