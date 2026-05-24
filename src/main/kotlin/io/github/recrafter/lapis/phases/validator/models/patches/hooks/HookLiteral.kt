package io.github.recrafter.lapis.phases.validator.models.patches.hooks

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.recrafter.lapis.annotations.ZeroCondition
import io.github.recrafter.lapis.common.KSBaseTypes
import io.github.recrafter.lapis.phases.lowering.asIrClassName
import io.github.recrafter.lapis.phases.lowering.types.IrClassName

sealed interface HookLiteral {
    fun getType(baseTypes: KSBaseTypes): KSType
}

class ZeroHookLiteral(val conditions: List<ZeroCondition>) : IntHookLiteral(0)
open class IntHookLiteral(val value: Int) : HookLiteral {
    override fun getType(baseTypes: KSBaseTypes): KSType = baseTypes.int
}

class LongHookLiteral(val value: Long) : HookLiteral {
    override fun getType(baseTypes: KSBaseTypes): KSType = baseTypes.long
}

class FloatHookLiteral(val value: Float) : HookLiteral {
    override fun getType(baseTypes: KSBaseTypes): KSType = baseTypes.float
}

class DoubleHookLiteral(val value: Double) : HookLiteral {
    override fun getType(baseTypes: KSBaseTypes): KSType = baseTypes.double
}

class StringHookLiteral(val value: String) : HookLiteral {
    override fun getType(baseTypes: KSBaseTypes): KSType = baseTypes.string
}

class ClassHookLiteral(typeClassDeclaration: KSClassDeclaration) : HookLiteral {
    override fun getType(baseTypes: KSBaseTypes): KSType = baseTypes.any
    val typeClassName: IrClassName = typeClassDeclaration.asIrClassName()
}

object NullHookLiteral : HookLiteral {
    override fun getType(baseTypes: KSBaseTypes): KSType = baseTypes.nothing
}
