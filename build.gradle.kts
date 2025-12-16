import io.github.diskria.gradle.utils.extensions.getCatalogVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.projektor)
    alias(libs.plugins.kotlin.jvm)
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
