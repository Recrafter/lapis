package io.github.recrafter.lapis.layers.builtins

import com.llamalad7.mixinextras.sugar.ref.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.builtins.SimpleBuiltin.LocalVar
import io.github.recrafter.lapis.layers.lowering.IrModifier
import io.github.recrafter.lapis.layers.lowering.asIr
import io.github.recrafter.lapis.layers.lowering.models.IrParameter
import io.github.recrafter.lapis.layers.lowering.models.IrSetterParameter
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeVariableName
import kotlin.reflect.KCallable

enum class LocalVarBuiltin : Builtin<KPClass> {
    ObjectLocalVar {
        override val valueTypeName = IrTypeVariableName.of("T")
        override val referenceTypeName = LocalRef::class.asIr().parameterizedBy(valueTypeName)
        override val getterCallable = LocalRef<*>::get
        override val setterCallable = LocalRef<*>::set
    },
    BooleanLocalVar {
        override val valueTypeName = KPBoolean.asIr()
        override val referenceTypeName = LocalBooleanRef::class.asIr()
        override val getterCallable = LocalBooleanRef::get
        override val setterCallable = LocalBooleanRef::set
    },
    ByteLocalVar {
        override val valueTypeName = KPByte.asIr()
        override val referenceTypeName = LocalByteRef::class.asIr()
        override val getterCallable = LocalByteRef::get
        override val setterCallable = LocalByteRef::set
    },
    ShortLocalVar {
        override val valueTypeName = KPShort.asIr()
        override val referenceTypeName = LocalShortRef::class.asIr()
        override val getterCallable = LocalShortRef::get
        override val setterCallable = LocalShortRef::set
    },
    IntLocalVar {
        override val valueTypeName = KPInt.asIr()
        override val referenceTypeName = LocalIntRef::class.asIr()
        override val getterCallable = LocalIntRef::get
        override val setterCallable = LocalIntRef::set
    },
    LongLocalVar {
        override val valueTypeName = KPLong.asIr()
        override val referenceTypeName = LocalLongRef::class.asIr()
        override val getterCallable = LocalLongRef::get
        override val setterCallable = LocalLongRef::set
    },
    CharLocalVar {
        override val valueTypeName = KPChar.asIr()
        override val referenceTypeName = LocalCharRef::class.asIr()
        override val getterCallable = LocalCharRef::get
        override val setterCallable = LocalCharRef::set
    },
    FloatLocalVar {
        override val valueTypeName = KPFloat.asIr()
        override val referenceTypeName = LocalFloatRef::class.asIr()
        override val getterCallable = LocalFloatRef::get
        override val setterCallable = LocalFloatRef::set
    },
    DoubleLocalVar {
        override val valueTypeName = KPDouble.asIr()
        override val referenceTypeName = LocalDoubleRef::class.asIr()
        override val getterCallable = LocalDoubleRef::get
        override val setterCallable = LocalDoubleRef::set
    };

    abstract val valueTypeName: IrTypeName
    abstract val referenceTypeName: IrTypeName
    abstract val getterCallable: KCallable<*>
    abstract val setterCallable: KCallable<*>

    override val isInternal: Boolean = true

    override fun generate(typer: BuiltinTyper): KPClass =
        buildKotlinClass(name) {
            setModifiers(IrModifier.PRIVATE)
            (valueTypeName as? IrTypeVariableName)?.let { setVariableTypes(it) }
            val referenceParameter = IrParameter(
                "reference",
                referenceTypeName,
                listOf(IrModifier.PRIVATE)
            )
            setConstructor(referenceParameter)
            addSuperInterface(typer(LocalVar).parameterizedBy(valueTypeName))
            addProperty(buildKotlinProperty("value", valueTypeName) {
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
                    val newValueParameter = IrSetterParameter(valueTypeName)
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
}
