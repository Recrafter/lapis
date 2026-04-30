package io.github.recrafter.lapis.phases.builtins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.generator.builders.toKotlinCodeBlock
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName

enum class SimpleBuiltin(override val isInternal: Boolean = false) : Builtin<KPClass> {
    Field {
        override fun generate(resolve: BuiltinResolver): KPClass =
            buildKotlinInterface(name) {
                setModifiers(IrModifier.PUBLIC)
                setVariableTypes(IrTypeVariableName.of("T"))
            }
    },
    Callable {
        override fun generate(resolve: BuiltinResolver): KPClass =
            buildKotlinInterface(name) {
                setModifiers(IrModifier.PUBLIC, IrModifier.SEALED)
                setVariableTypes(IrTypeVariableName.of("F", Function::class.asIrParameterizedTypeName()))
            }
    },
    Method {
        override fun generate(resolve: BuiltinResolver): KPClass =
            buildKotlinInterface(name) {
                setModifiers(IrModifier.PUBLIC)
                val functionTypeVariableName = IrTypeVariableName.of(
                    "F",
                    Function::class.asIrParameterizedTypeName()
                )
                setVariableTypes(functionTypeVariableName)
                addSuperInterface(resolve(Callable).parameterizedBy(functionTypeVariableName))
            }
    },
    Constructor {
        override fun generate(resolve: BuiltinResolver): KPClass =
            buildKotlinInterface(name) {
                setModifiers(IrModifier.PUBLIC)
                val functionTypeVariableName = IrTypeVariableName.of(
                    "F",
                    Function::class.asIrParameterizedTypeName()
                )
                setVariableTypes(functionTypeVariableName)
                addSuperInterface(resolve(Callable).parameterizedBy(functionTypeVariableName))
            }
    },
    LocalVar {
        override fun generate(resolve: BuiltinResolver): KPClass =
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
        override fun generate(resolve: BuiltinResolver): KPClass =
            buildKotlinClass(name) {
                setModifiers(IrModifier.PUBLIC)
                val valueParameter = IrParameter(
                    "value",
                    KPAny.asIrClassName(),
                    listOf(IrModifier.PUBLIC)
                )
                val operationParameter = IrParameter(
                    "operation",
                    Operation::class.asIrParameterizedTypeName(KPBoolean.asIrClassName()),
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
        override fun generate(resolve: BuiltinResolver): KPClass =
            buildKotlinObject(name) {
                setModifiers(IrModifier.PUBLIC)
                setSuperClass(
                    RuntimeException::class.asIrTypeName(),
                    constructorArguments = listOf(
                        buildKotlinCodeBlock("null"),
                        buildKotlinCodeBlock("null"),
                        false.toKotlinCodeBlock(),
                        false.toKotlinCodeBlock(),
                    )
                )
                addFunction(buildKotlinFunction(RuntimeException::fillInStackTrace.name) {
                    setModifiers(IrModifier.OVERRIDE)
                    setReturnType(Throwable::class.asIrTypeName())
                    setBody { return_("this") }
                })
            }
    };

    abstract override fun generate(resolve: BuiltinResolver): KPClass
}
