package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.extensions.ksp.KSPCodeGenerator
import io.github.recrafter.lapis.extensions.ksp.KSPDependencies

inline fun <reified A : Annotation> KPFileBuilder.addAnnotation(builder: KPAnnotationBuilder.() -> Unit = {}) {
    addAnnotation(buildKotlinAnnotation<A>(builder))
}

fun KPFile.writeTo(codeGenerator: KSPCodeGenerator, dependencies: KSPDependencies) {
    codeGenerator.createNewFile(dependencies, packageName, name).writer().use { writeTo(it) }
}
