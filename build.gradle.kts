import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.projektor)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlin.poet.ksp)

    implementation(libs.lapis.annotations)
    implementation(libs.poetesse)

    implementation(libs.mixin)
    implementation(libs.mixin.extras)
    implementation(libs.asm)

    implementation(libs.kotlin.serialization.json)

    ksp(libs.auto.service)
    implementation(libs.auto.service.annotations)
}

projekt {
    kotlinLibrary {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks {
    withType<KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
    }
}
