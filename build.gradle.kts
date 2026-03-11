import io.github.diskria.gradle.utils.extensions.getCatalogVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.projektor)
    alias(libs.plugins.ksp)
    `java-library`
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.psi.api)

    implementation(libs.mixin)
    implementation(libs.mixin.extras)

    implementation(libs.bundles.asm)

    implementation(libs.lapis.annotations)

    implementation(libs.kotlin.poet)
    implementation(libs.java.poet)

    implementation(libs.kotlin.serialization.json)

    ksp(libs.auto.service)
    compileOnly(libs.auto.service.annotations)
}

projekt {
    kotlinLibrary {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

val kotlinVersion = getCatalogVersion("kotlin")
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(kotlinVersion)
        }
    }
}
