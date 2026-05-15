package io.github.recrafter.lapis.phases.builtins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.generator.builders.nullKotlinCodeBlock
import io.github.recrafter.lapis.phases.generator.builders.toKotlinCodeBlock
import io.github.recrafter.lapis.phases.lowering.IrVisibilityModifier
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.models.toKotlinConstructorProperty
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName

enum class SimpleBuiltin(override val isInternal: Boolean = false) : Builtin<KPClass> {
    Field {
        override fun generate(resolveBuiltin: BuiltinResolver): KPClass =
            buildKotlinInterface(name) {
                setVariableTypes(IrTypeVariableName.of("T"))
            }
    },
    Callable {
        override fun generate(resolveBuiltin: BuiltinResolver): KPClass =
            buildKotlinInterface(name) {
                addModifiers(KPModifier.SEALED)
                setVariableTypes(IrTypeVariableName.of("F", Function::class.asIrParameterizedTypeName()))
            }
    },
    Method {
        override fun generate(resolveBuiltin: BuiltinResolver): KPClass =
            buildKotlinInterface(name) {
                val functionTypeVariableName = IrTypeVariableName.of(
                    "F",
                    Function::class.asIrParameterizedTypeName()
                )
                setVariableTypes(functionTypeVariableName)
                addSuperInterface(resolveBuiltin(Callable).parameterizedBy(functionTypeVariableName))
            }
    },
    Constructor {
        override fun generate(resolveBuiltin: BuiltinResolver): KPClass =
            buildKotlinInterface(name) {
                val functionTypeVariableName = IrTypeVariableName.of(
                    "F",
                    Function::class.asIrParameterizedTypeName()
                )
                setVariableTypes(functionTypeVariableName)
                addSuperInterface(resolveBuiltin(Callable).parameterizedBy(functionTypeVariableName))
            }
    },
    LocalVar {
        override fun generate(resolveBuiltin: BuiltinResolver): KPClass =
            buildKotlinInterface(name) {
                addModifiers(KPModifier.SEALED)
                val localTypeVariableName = IrTypeVariableName.of("T")
                setVariableTypes(localTypeVariableName)
                addProperty(buildKotlinProperty("value", localTypeVariableName) {
                    addModifiers(KPModifier.ABSTRACT)
                    mutable(true)
                })
            }
    },
    Instanceof {
        override fun generate(resolveBuiltin: BuiltinResolver): KPClass =
            buildKotlinClass(name) {
                val valueParameter = IrParameter("value", KPAny.asIrClassName())
                val operationParameter = IrParameter(
                    "operation",
                    Operation::class.asIrParameterizedTypeName(KPBoolean.asIrClassName()),
                )
                setConstructor(valueParameter, operationParameter)
                addProperties(
                    listOf(
                        valueParameter.toKotlinConstructorProperty(),
                        operationParameter.toKotlinConstructorProperty(IrVisibilityModifier.PRIVATE),
                    )
                )
                addFunction(buildKotlinFunction("invoke") {
                    addModifiers(KPModifier.OPERATOR)
                    val valueParameter = buildKotlinParameter("value", valueParameter.typeName) {
                        setDefaultValue("this.%N") { +valueParameter }
                    }
                    addParameter(valueParameter)
                    setReturnType(KPBoolean.asIrClassName())
                    setBody {
                        return_("%N.%L(%N)") { +operationParameter; +Operation<*>::call; +valueParameter }
                    }
                })
            }
    },
    CancelSignal(isInternal = true) {
        override fun generate(resolveBuiltin: BuiltinResolver): KPClass =
            buildKotlinObject(name) {
                setSuperClass(
                    RuntimeException::class.asIrTypeName(),
                    constructorArguments = listOf(
                        nullKotlinCodeBlock,
                        nullKotlinCodeBlock,
                        false.toKotlinCodeBlock(),
                        false.toKotlinCodeBlock(),
                    )
                )
                addFunction(buildKotlinFunction(RuntimeException::fillInStackTrace.name) {
                    addModifiers(KPModifier.OVERRIDE)
                    setReturnType(Throwable::class.asIrTypeName())
                    setBody { return_("this") }
                })
            }
    };

    abstract override fun generate(resolveBuiltin: BuiltinResolver): KPClass
}
