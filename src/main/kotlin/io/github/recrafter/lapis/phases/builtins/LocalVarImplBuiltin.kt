package io.github.recrafter.lapis.phases.builtins

import com.llamalad7.mixinextras.sugar.ref.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.builtins.SimpleBuiltin.LocalVar
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.models.IrSetterParameter
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName
import kotlin.reflect.KCallable

enum class LocalVarImplBuiltin(
    val valueTypeName: IrTypeName? = null,
    val referenceClassName: IrClassName,
    private val getterCallable: KCallable<*>,
    private val setterCallable: KCallable<*>,
) : Builtin<KPClass> {

    ObjectLocalVar(
        referenceClassName = LocalRef::class.asIrClassName(),
        getterCallable = LocalRef<*>::get,
        setterCallable = LocalRef<*>::set,
    ),
    BooleanLocalVar(
        valueTypeName = KPBoolean.asIrClassName(),
        referenceClassName = LocalBooleanRef::class.asIrClassName(),
        getterCallable = LocalBooleanRef::get,
        setterCallable = LocalBooleanRef::set,
    ),
    ByteLocalVar(
        valueTypeName = KPByte.asIrClassName(),
        referenceClassName = LocalByteRef::class.asIrClassName(),
        getterCallable = LocalByteRef::get,
        setterCallable = LocalByteRef::set,
    ),
    ShortLocalVar(
        valueTypeName = KPShort.asIrClassName(),
        referenceClassName = LocalShortRef::class.asIrClassName(),
        getterCallable = LocalShortRef::get,
        setterCallable = LocalShortRef::set,
    ),
    IntLocalVar(
        valueTypeName = KPInt.asIrClassName(),
        referenceClassName = LocalIntRef::class.asIrClassName(),
        getterCallable = LocalIntRef::get,
        setterCallable = LocalIntRef::set,
    ),
    LongLocalVar(
        valueTypeName = KPLong.asIrClassName(),
        referenceClassName = LocalLongRef::class.asIrClassName(),
        getterCallable = LocalLongRef::get,
        setterCallable = LocalLongRef::set,
    ),
    CharLocalVar(
        valueTypeName = KPChar.asIrClassName(),
        referenceClassName = LocalCharRef::class.asIrClassName(),
        getterCallable = LocalCharRef::get,
        setterCallable = LocalCharRef::set,
    ),
    FloatLocalVar(
        valueTypeName = KPFloat.asIrClassName(),
        referenceClassName = LocalFloatRef::class.asIrClassName(),
        getterCallable = LocalFloatRef::get,
        setterCallable = LocalFloatRef::set,
    ),
    DoubleLocalVar(
        valueTypeName = KPDouble.asIrClassName(),
        referenceClassName = LocalDoubleRef::class.asIrClassName(),
        getterCallable = LocalDoubleRef::get,
        setterCallable = LocalDoubleRef::set,
    );

    override val isInternal: Boolean = true

    override fun generate(typer: BuiltinTyper): KPClass =
        buildKotlinClass(name) {
            setModifiers(IrModifier.PUBLIC)
            val genericTypeName = valueTypeName ?: run {
                IrTypeVariableName.of("T").also { setVariableTypes(it) }
            }
            val referenceParameter = IrParameter(
                "reference",
                valueTypeName?.let { referenceClassName } ?: referenceClassName.parameterizedBy(genericTypeName),
                listOf(IrModifier.PRIVATE)
            )
            setConstructor(referenceParameter)
            addSuperInterface(typer(LocalVar).parameterizedBy(genericTypeName))
            addProperty(buildKotlinProperty("value", genericTypeName) {
                setModifiers(IrModifier.PUBLIC, IrModifier.OVERRIDE)
                setGetter {
                    setBody {
                        return_("%N.%L()") {
                            arg(referenceParameter)
                            arg(getterCallable)
                        }
                    }
                }
                setSetter {
                    val newValueParameter = IrSetterParameter(genericTypeName)
                    setParameters(listOf(newValueParameter))
                    setBody {
                        code_("%N.%L(%N)") {
                            arg(referenceParameter)
                            arg(setterCallable)
                            arg(newValueParameter)
                        }
                    }
                }
            })
        }

    companion object {
        fun of(valueTypeName: IrTypeName): LocalVarImplBuiltin =
            LocalVarImplBuiltin.entries.find { valueTypeName == it.valueTypeName } ?: ObjectLocalVar
    }
}
