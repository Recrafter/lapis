import io.github.diskria.projektor.common.licenses.LicenseType.MIT
import io.github.diskria.projektor.common.publishing.PublishingTargetType.MAVEN_CENTRAL

pluginManagement {
    repositories {
        maven("https://diskria.github.io/projektor") {
            name = "Projektor"
        }
        gradlePluginPortal()
    }
}

plugins {
    id("io.github.diskria.projektor.settings") version "6.+"
    id("io.github.recrafter.recipe") version "1.2.6"
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
    }
}

projekt {
    version = "0.8.6"
    license = MIT
    publish = setOf(MAVEN_CENTRAL)

    kotlinLibrary()
}

recipe {
    crafter {
        mavensOnly()
    }
}
