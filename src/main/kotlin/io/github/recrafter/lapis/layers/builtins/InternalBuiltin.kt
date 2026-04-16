package io.github.recrafter.lapis.layers.builtins

import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.asIr

enum class InternalBuiltin : Builtin<KPClass> {
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

    override val isInternal: Boolean = true

    abstract override fun generate(typer: BuiltinTyper): KPClass
}
