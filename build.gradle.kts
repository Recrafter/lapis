import io.github.diskria.gradle.utils.extensions.getCatalogVersion
import io.github.diskria.gradle.utils.extensions.implementation
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.projektor)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.ksp.api)

    implementation(libs.mixin)

    implementation(libs.java.poet)
    implementation(libs.kotlin.poet.ksp)

    ksp(libs.auto.service)
    implementation(libs.auto.service.annotations)
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
