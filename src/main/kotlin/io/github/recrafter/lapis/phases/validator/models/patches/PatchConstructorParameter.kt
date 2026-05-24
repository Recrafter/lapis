package io.github.recrafter.lapis.phases.validator.models.patches

import com.google.devtools.ksp.symbol.KSClassDeclaration

sealed interface PatchConstructorParameter
class PatchConstructorOriginParameter(val instanceClassDeclaration: KSClassDeclaration) : PatchConstructorParameter
