package io.github.recrafter.lapis.layers

import io.github.recrafter.lapis.annotations.Constructor
import io.github.recrafter.lapis.annotations.Field
import io.github.recrafter.lapis.annotations.Method
import kotlin.reflect.KClass

enum class JavaMemberKind(val annotationClass: KClass<out Annotation>) {
    CONSTRUCTOR(Constructor::class),
    METHOD(Method::class),
    FIELD(Field::class);
}
