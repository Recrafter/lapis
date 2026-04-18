package io.github.recrafter.lapis.phases.builtins

import com.llamalad7.mixinextras.sugar.ref.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.builtins.SimpleBuiltin.LocalVar
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.asIr
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
        referenceClassName = LocalRef::class.asIr(),
        getterCallable = LocalRef<*>::get,
        setterCallable = LocalRef<*>::set,
    ),
    BooleanLocalVar(
        valueTypeName = KPBoolean.asIr(),
        referenceClassName = LocalBooleanRef::class.asIr(),
        getterCallable = LocalBooleanRef::get,
        setterCallable = LocalBooleanRef::set,
    ),
    ByteLocalVar(
        valueTypeName = KPByte.asIr(),
        referenceClassName = LocalByteRef::class.asIr(),
        getterCallable = LocalByteRef::get,
        setterCallable = LocalByteRef::set,
    ),
    ShortLocalVar(
        valueTypeName = KPShort.asIr(),
        referenceClassName = LocalShortRef::class.asIr(),
        getterCallable = LocalShortRef::get,
        setterCallable = LocalShortRef::set,
    ),
    IntLocalVar(
        valueTypeName = KPInt.asIr(),
        referenceClassName = LocalIntRef::class.asIr(),
        getterCallable = LocalIntRef::get,
        setterCallable = LocalIntRef::set,
    ),
    LongLocalVar(
        valueTypeName = KPLong.asIr(),
        referenceClassName = LocalLongRef::class.asIr(),
        getterCallable = LocalLongRef::get,
        setterCallable = LocalLongRef::set,
    ),
    CharLocalVar(
        valueTypeName = KPChar.asIr(),
        referenceClassName = LocalCharRef::class.asIr(),
        getterCallable = LocalCharRef::get,
        setterCallable = LocalCharRef::set,
    ),
    FloatLocalVar(
        valueTypeName = KPFloat.asIr(),
        referenceClassName = LocalFloatRef::class.asIr(),
        getterCallable = LocalFloatRef::get,
        setterCallable = LocalFloatRef::set,
    ),
    DoubleLocalVar(
        valueTypeName = KPDouble.asIr(),
        referenceClassName = LocalDoubleRef::class.asIr(),
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
