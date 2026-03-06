package io.github.recrafter.lapis.layers.lowering

open class IrParameter(val name: String, val type: IrTypeName)
class IrSetterParameter(type: IrTypeName) : IrParameter("newValue", type)
