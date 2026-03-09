package io.github.recrafter.lapis.extensions.ksp

fun List<KSPFile>.toDependencies(aggregating: Boolean = false): KSPDependencies =
    KSPDependencies(aggregating, *toTypedArray())

fun KSPFile.warmUp(): KSPFile {
    packageName
    fileName
    filePath
    return this
}
