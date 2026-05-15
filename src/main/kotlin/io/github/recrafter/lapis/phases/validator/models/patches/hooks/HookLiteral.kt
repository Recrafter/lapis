package io.github.recrafter.lapis.phases.validator.models.patches.hooks

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.annotations.ZeroCondition
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import io.github.recrafter.lapis.phases.parser.KSTypes

sealed interface HookLiteral {
    fun getType(types: KSTypes): KSType
}

class ZeroHookLiteral(val conditions: List<ZeroCondition>) : IntHookLiteral(0)
open class IntHookLiteral(val value: Int) : HookLiteral {
    override fun getType(types: KSTypes): KSType = types.int
}

class FloatHookLiteral(val value: Float) : HookLiteral {
    override fun getType(types: KSTypes): KSType = types.float
}

class LongHookLiteral(val value: Long) : HookLiteral {
    override fun getType(types: KSTypes): KSType = types.long
}

class DoubleHookLiteral(val value: Double) : HookLiteral {
    override fun getType(types: KSTypes): KSType = types.double
}

class StringHookLiteral(val value: String) : HookLiteral {
    override fun getType(types: KSTypes): KSType = types.string
}

class ClassHookLiteral(typeClassDeclaration: KSClassDeclaration) : HookLiteral {
    override fun getType(types: KSTypes): KSType = types.any
    val typeClassName: IrClassName = typeClassDeclaration.asIrClassName()
}

object NullHookLiteral : HookLiteral {
    override fun getType(types: KSTypes): KSType = types.nothing
}
