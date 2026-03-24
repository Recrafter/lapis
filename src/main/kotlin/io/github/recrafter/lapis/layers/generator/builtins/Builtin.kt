package io.github.recrafter.lapis.layers.generator.builtins

import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.IrParameter
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrVariableTypeName

enum class Builtin {
    Desc {
        override fun generate(typer: (Builtin) -> IrClassName): KPClass = buildKotlinClass(name) {
            setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
            val functionVariableType = IrVariableTypeName.of("F", Function::class.asIr().generic(KPStar.asIr()))
            setVariableTypes(functionVariableType)
            setConstructor(listOf(IrParameter("function", functionVariableType)), IrModifier.PRIVATE)
        }
    },
    Patch {
        override fun generate(typer: (Builtin) -> IrClassName): KPClass = buildKotlinClass(name) {
            setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
            val instanceVariableType = IrVariableTypeName.of("I")
            setVariableTypes(instanceVariableType)
            addProperty(buildKotlinProperty("instance", instanceVariableType) {
                setModifiers(IrModifier.PUBLIC, IrModifier.ABSTRACT)
            })
        }
    },
    CancelSignal {
        override fun generate(typer: (Builtin) -> IrClassName): KPClass = buildKotlinObject(name) {
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
    };

    abstract fun generate(typer: (Builtin) -> IrClassName): KPClass
}
