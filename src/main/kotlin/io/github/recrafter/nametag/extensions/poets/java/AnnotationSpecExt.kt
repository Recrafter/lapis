package io.github.recrafter.nametag.extensions.poets.java

import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.TypeName

fun AnnotationSpec.Builder.addStringMember(name: String, value: String) {
    addMember(name, "\$S", value)
}

fun AnnotationSpec.Builder.addClassMember(name: String, value: TypeName) {
    addMember(name, "\$T.class", value)
}
