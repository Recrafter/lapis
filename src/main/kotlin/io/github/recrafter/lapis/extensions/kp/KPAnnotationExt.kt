package io.github.recrafter.lapis.extensions.kp

import kotlin.reflect.KProperty1

fun <A : Annotation> KPAnnotationBuilder.setStringMember(property: KProperty1<A, String>, string: String) {
    addMember(buildKotlinCodeBlock("%L = %S") {
        arg(property.name)
        arg(string)
    })
}
