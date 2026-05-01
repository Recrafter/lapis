package io.github.recrafter.lapis.phases.builtins

import com.llamalad7.mixinextras.sugar.ref.*
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.phases.builtins.SimpleBuiltin.LocalVar
import io.github.recrafter.lapis.phases.lowering.IrModifier
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.asIrParameterizedTypeName
import io.github.recrafter.lapis.phases.lowering.asIrTypeName
import io.github.recrafter.lapis.phases.lowering.models.IrParameter
import io.github.recrafter.lapis.phases.lowering.models.IrSetterParameter
import io.github.recrafter.lapis.phases.lowering.types.IrTypeName
import io.github.recrafter.lapis.phases.lowering.types.IrTypeVariableName
import kotlin.reflect.KCallable
import kotlin.reflect.KClass

enum class LocalVarImplBuiltin(
    private val valueKPClassName: KPClassName? = null,
    private val referenceKClass: KClass<*>,
    private val getterCallable: KCallable<*>,
    private val setterCallable: KCallable<*>,
) : Builtin<KPClass> {

    ObjectLocalVar(
        referenceKClass = LocalRef::class,
        getterCallable = LocalRef<*>::get,
        setterCallable = LocalRef<*>::set,
    ),
    BooleanLocalVar(
        valueKPClassName = KPBoolean,
        referenceKClass = LocalBooleanRef::class,
        getterCallable = LocalBooleanRef::get,
        setterCallable = LocalBooleanRef::set,
    ),
    ByteLocalVar(
        valueKPClassName = KPByte,
        referenceKClass = LocalByteRef::class,
        getterCallable = LocalByteRef::get,
        setterCallable = LocalByteRef::set,
    ),
    ShortLocalVar(
        valueKPClassName = KPShort,
        referenceKClass = LocalShortRef::class,
        getterCallable = LocalShortRef::get,
        setterCallable = LocalShortRef::set,
    ),
    IntLocalVar(
        valueKPClassName = KPInt,
        referenceKClass = LocalIntRef::class,
        getterCallable = LocalIntRef::get,
        setterCallable = LocalIntRef::set,
    ),
    LongLocalVar(
        valueKPClassName = KPLong,
        referenceKClass = LocalLongRef::class,
        getterCallable = LocalLongRef::get,
        setterCallable = LocalLongRef::set,
    ),
    CharLocalVar(
        valueKPClassName = KPChar,
        referenceKClass = LocalCharRef::class,
        getterCallable = LocalCharRef::get,
        setterCallable = LocalCharRef::set,
    ),
    FloatLocalVar(
        valueKPClassName = KPFloat,
        referenceKClass = LocalFloatRef::class,
        getterCallable = LocalFloatRef::get,
        setterCallable = LocalFloatRef::set,
    ),
    DoubleLocalVar(
        valueKPClassName = KPDouble,
        referenceKClass = LocalDoubleRef::class,
        getterCallable = LocalDoubleRef::get,
        setterCallable = LocalDoubleRef::set,
    );

    val referenceTypeName: IrTypeName = referenceKClass.asIrTypeName()

    override val isInternal: Boolean = true

    override fun generate(resolveBuiltin: BuiltinResolver): KPClass =
        buildKotlinClass(name) {
            setModifiers(IrModifier.PUBLIC)
            val (genericTypeName, referenceTypeName) = if (valueKPClassName != null) {
                valueKPClassName.asIrClassName() to referenceTypeName
            } else {
                val typeVariableName = IrTypeVariableName.of("T")
                setVariableTypes(typeVariableName)
                typeVariableName to referenceKClass.asIrParameterizedTypeName(typeVariableName)
            }
            val referenceParameter = IrParameter("reference", referenceTypeName, listOf(IrModifier.PRIVATE))
            setConstructor(referenceParameter)
            addSuperInterface(resolveBuiltin(LocalVar).parameterizedBy(genericTypeName))
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
            LocalVarImplBuiltin.entries.find { valueTypeName == it.valueKPClassName } ?: ObjectLocalVar
    }
}
