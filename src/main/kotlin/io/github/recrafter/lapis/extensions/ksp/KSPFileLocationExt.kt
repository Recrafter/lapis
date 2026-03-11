package io.github.recrafter.lapis.extensions.ksp

import java.io.File

val KSPFileLocation.file: File
    get() = File(filePath)
