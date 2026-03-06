package io.github.recrafter.lapis.utils

import io.github.recrafter.lapis.annotations.LaConstructor
import io.github.recrafter.lapis.annotations.LaField
import io.github.recrafter.lapis.annotations.LaMethod
import kotlin.reflect.KClass

enum class MemberKind(val annotationClass: KClass<out Annotation>) {
    CONSTRUCTOR(LaConstructor::class),
    METHOD(LaMethod::class),
    FIELD(LaField::class);
}
