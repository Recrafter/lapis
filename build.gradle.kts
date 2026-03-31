import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.projektor)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.bundles.ksp)
    implementation(libs.bundles.mixins)
    implementation(libs.bundles.asm)
    implementation(libs.bundles.poets)

    implementation(libs.lapis.annotations)
    implementation(libs.kotlin.serialization.json)

    ksp(libs.auto.service)
    compileOnly(libs.auto.service.annotations)
}

projekt {
    kotlinLibrary {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks {
    withType<KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
    }
}
