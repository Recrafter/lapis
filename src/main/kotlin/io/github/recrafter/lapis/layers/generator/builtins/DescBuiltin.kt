package io.github.recrafter.lapis.layers.generator.builtins

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.github.recrafter.lapis.extensions.capitalize
import io.github.recrafter.lapis.extensions.kp.*
import io.github.recrafter.lapis.layers.generator.builders.IrKotlinCodeBlockBuilder
import io.github.recrafter.lapis.layers.generator.withInternalPrefix
import io.github.recrafter.lapis.layers.lowering.*
import io.github.recrafter.lapis.layers.lowering.types.IrClassName
import io.github.recrafter.lapis.layers.lowering.types.IrTypeName
import io.github.recrafter.lapis.layers.lowering.types.IrVariableTypeName
import io.github.recrafter.lapis.layers.lowering.types.orVoid
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

sealed class DescBuiltin<D : IrDesc, W : IrDescWrapper>(val name: String) {

    fun generate(typer: (Builtin) -> IrClassName): KPClass =
        buildKotlinInterface(name) {
            setModifiers(IrModifier.PUBLIC)
            setVariableTypes(IrVariableTypeName.of("D", typer(Builtin.Desc).generic(KPStar.asIr())))
        }

    abstract fun generateWrapper(dest: KPFileBuilder, wrapper: W, typer: (Builtin) -> IrClassName)

    data object Call : DescBuiltin<IrInvokableDesc, IrDescCallWrapper>("Call") {

        override fun generateWrapper(
            dest: KPFileBuilder, wrapper: IrDescCallWrapper, typer: (Builtin) -> IrClassName
        ) {
            val operationParameterName = "operation".withInternalPrefix()
            val receiverParameterName = "receiver".withInternalPrefix()
            val invokeWithReceiverParameterName = "invokeWithReceiver".withInternalPrefix()
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(buildList {
                    wrapper.receiverTypeName?.let {
                        add(IrParameter(receiverParameterName, it))
                        add(IrParameter(invokeWithReceiverParameterName, Boolean::class.asIr()))
                    }
                    addAll(wrapper.parameters.mapIndexed { index, parameter ->
                        val name = parameter.name ?: index.toString()
                        IrParameter(name.withInternalPrefix("argument"), parameter.typeName)
                    })
                    add(
                        IrParameter(
                            operationParameterName,
                            Operation::class.asIr().generic(wrapper.returnTypeName.orVoid())
                        )
                    )
                })
                addSuperInterface(wrapper.superClassTypeName)
            })
            dest.addProperties(wrapper.parameters.mapNotNull { parameter ->
                val name = parameter.name ?: return@mapNotNull null
                buildKotlinProperty(name, parameter.typeName) {
                    setReceiverType(wrapper.superClassTypeName)
                    setGetter {
                        setJvmName(wrapper.className.simpleName + "_get" + name.capitalize())
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") {
                                arg(wrapper.className)
                                arg(name.withInternalPrefix("argument"))
                            }
                        }
                    }
                }
            })
            wrapper.receiverTypeName?.let { returnType ->
                dest.addFunction(buildKotlinFunction("getReceiver") {
                    setJvmName(wrapper.className.simpleName + "_getReceiver")
                    setModifiers(IrModifier.INLINE)
                    setReceiverType(wrapper.superClassTypeName)
                    setReturnType(returnType)
                    setBody {
                        return_("(this as %T).%L") {
                            arg(wrapper.className)
                            arg(receiverParameterName)
                        }
                    }
                })
            }
            dest.addFunction(buildKotlinFunction("invoke") {
                setJvmName(wrapper.className.simpleName + "_invoke")
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(wrapper.superClassTypeName)
                wrapper.parameters.forEach { parameter ->
                    val name = parameter.name ?: return@forEach
                    addParameter(buildKotlinParameter(name, parameter.typeName) {
                        defaultValue(buildKotlinCodeBlock("this.%L") {
                            arg(name)
                        })
                    })
                }
                setReturnType(wrapper.returnTypeName)
                setBody {
                    code("this as %T") {
                        arg(wrapper.className)
                    }

                    fun callOperation(parameters: List<String>) {
                        val format = "%L.%L(%L)"
                        val args: IrKotlinCodeBlockBuilder.Arguments.() -> Unit = {
                            arg(operationParameterName)
                            arg(Operation<*>::call)
                            arg(parameters.joinToString())
                        }
                        if (wrapper.returnTypeName != null) {
                            return_(format, args)
                        } else {
                            code(format, args)
                        }
                    }

                    val parameterNames = wrapper.parameters.mapIndexed { index, it ->
                        it.name ?: index.toString().withInternalPrefix("argument")
                    }
                    if (wrapper.receiverTypeName != null) {
                        if_(buildKotlinCodeBlock(invokeWithReceiverParameterName)) {
                            callOperation(listOf(receiverParameterName) + parameterNames)
                            if (wrapper.returnTypeName == null) {
                                return_()
                            }
                        }
                    }
                    callOperation(parameterNames)
                }
            })
        }
    }

    data object FieldGet : DescBuiltin<IrFieldDesc, IrDescFieldGetWrapper>("FieldGet") {

        override fun generateWrapper(
            dest: KPFileBuilder, wrapper: IrDescFieldGetWrapper, typer: (Builtin) -> IrClassName
        ) {
            val receiverParameterName = "receiver".withInternalPrefix()
            val operationParameterName = "operation".withInternalPrefix()
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(buildList {
                    wrapper.receiverTypeName?.let { add(IrParameter(receiverParameterName, it)) }
                    add(IrParameter(operationParameterName, Operation::class.asIr().generic(wrapper.fieldTypeName)))
                })
                addSuperInterface(wrapper.superClassTypeName)
            })
            wrapper.receiverTypeName?.let {
                dest.addProperty(buildKotlinProperty("receiver", it) {
                    setReceiverType(wrapper.superClassTypeName)
                    setGetter {
                        setJvmName(wrapper.className.simpleName + "_receiver")
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") {
                                arg(wrapper.className)
                                arg(receiverParameterName)
                            }
                        }
                    }
                })
            }
            dest.addFunction(buildKotlinFunction("get") {
                setJvmName(wrapper.className.simpleName + "_get")
                setModifiers(IrModifier.INLINE)
                setReceiverType(wrapper.superClassTypeName)
                wrapper.receiverTypeName?.let {
                    addParameter(buildKotlinParameter("receiver", it) {
                        defaultValue(buildKotlinCodeBlock("this.%L") {
                            arg("receiver")
                        })
                    })
                }
                setReturnType(wrapper.fieldTypeName)
                setBody {
                    return_(buildString {
                        append("(this as %T).%L.%L(")
                        if (wrapper.receiverTypeName != null) {
                            append("%L")
                        }
                        append(")")
                    }) {
                        arg(wrapper.className)
                        arg(operationParameterName)
                        arg(Operation<*>::call)
                        if (wrapper.receiverTypeName != null) {
                            arg("receiver")
                        }
                    }
                }
            })
        }
    }

    data object FieldWrite : DescBuiltin<IrFieldDesc, IrDescFieldWriteWrapper>("FieldWrite") {

        override fun generateWrapper(
            dest: KPFileBuilder, wrapper: IrDescFieldWriteWrapper, typer: (Builtin) -> IrClassName
        ) {
            val receiverParameterName = "receiver".withInternalPrefix()
            val valueParameterName = "value".withInternalPrefix()
            val operationParameterName = "operation".withInternalPrefix()
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(buildList {
                    wrapper.receiverTypeName?.let { add(IrParameter(receiverParameterName, it)) }
                    add(IrParameter(valueParameterName, wrapper.fieldTypeName))
                    add(IrParameter(operationParameterName, Operation::class.asIr().generic(IrTypeName.VOID)))
                })
                addSuperInterface(wrapper.superClassTypeName)
            })
            wrapper.receiverTypeName?.let {
                dest.addProperty(buildKotlinProperty("receiver", it) {
                    setReceiverType(wrapper.superClassTypeName)
                    setGetter {
                        setJvmName(wrapper.className.simpleName + "_receiver")
                        setModifiers(IrModifier.INLINE)
                        setBody {
                            return_("(this as %T).%L") {
                                arg(wrapper.className)
                                arg(receiverParameterName)
                            }
                        }
                    }
                })
            }
            dest.addProperty(buildKotlinProperty("value", wrapper.fieldTypeName) {
                setReceiverType(wrapper.superClassTypeName)
                setGetter {
                    setJvmName(wrapper.className.simpleName + "_value")
                    setModifiers(IrModifier.INLINE)
                    setBody {
                        return_("(this as %T).%L") {
                            arg(wrapper.className)
                            arg(valueParameterName)
                        }
                    }
                }
            })
            dest.addFunction(buildKotlinFunction("set") {
                setJvmName(wrapper.className.simpleName + "_set")
                setModifiers(IrModifier.INLINE)
                setReceiverType(wrapper.superClassTypeName)
                addParameter(buildKotlinParameter("value", wrapper.fieldTypeName) {
                    defaultValue(buildKotlinCodeBlock("this.%L") {
                        arg("value")
                    })
                })
                wrapper.receiverTypeName?.let {
                    addParameter(buildKotlinParameter("receiver", it) {
                        defaultValue(buildKotlinCodeBlock("this.%L") {
                            arg("receiver")
                        })
                    })
                }
                setBody {
                    code(buildString {
                        append("(this as %T).%L.%L(")
                        if (wrapper.receiverTypeName != null) {
                            append("%L, ")
                        }
                        append("%L")
                        append(")")
                    }) {
                        arg(wrapper.className)
                        arg(operationParameterName)
                        arg(Operation<*>::call)
                        if (wrapper.receiverTypeName != null) {
                            arg("receiver")
                        }
                        arg("value")
                    }
                }
            })
        }
    }

    data object Cancel : DescBuiltin<IrInvokableDesc, IrDescCancelWrapper>("Cancel") {

        override fun generateWrapper(
            dest: KPFileBuilder, wrapper: IrDescCancelWrapper, typer: (Builtin) -> IrClassName
        ) {
            val callbackParameterName = "callback".withInternalPrefix()
            dest.addType(buildKotlinClass(wrapper.className.simpleName) {
                setConstructor(buildList {
                    add(
                        IrParameter(
                        callbackParameterName,
                        wrapper.returnTypeName
                            ?.let { CallbackInfoReturnable::class.asIr().generic(it) }
                            ?: CallbackInfo::class.asIr()
                    ))
                })
                addSuperInterface(wrapper.superClassTypeName)
            })
            dest.addFunction(buildKotlinFunction("invoke") {
                setJvmName(wrapper.className.simpleName + "_invoke")
                setModifiers(IrModifier.INLINE, IrModifier.OPERATOR)
                setReceiverType(wrapper.superClassTypeName)
                setReturnType(KPNothing.asIr())
                val returnValueParameter = wrapper.returnTypeName?.let {
                    addParameter(IrParameter("returnValue", it))
                }
                setBody {
                    code("(this as %T)") {
                        arg(wrapper.className)
                    }
                    code(buildString {
                        append("%L.%L(")
                        if (returnValueParameter != null) {
                            append("%N")
                        }
                        append(")")
                    }) {
                        arg(callbackParameterName)
                        arg(
                            if (returnValueParameter != null) CallbackInfoReturnable<*>::setReturnValue
                            else CallbackInfo::cancel
                        )
                        returnValueParameter?.let { arg(it) }
                    }
                    throw_("%T") {
                        arg(typer(Builtin.CancelSignal))
                    }
                }
            })
        }
    }

    companion object {
        val entries: Array<DescBuiltin<*, *>> get() = arrayOf(Call, FieldGet, FieldWrite, Cancel)
    }
}
