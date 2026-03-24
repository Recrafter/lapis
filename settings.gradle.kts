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
    id("io.github.diskria.projektor.settings") version "5.+"
    id("io.github.recrafter.recipe") version "1.2.2"
}

projekt {
    version = "0.8.4"
    license = MIT
    publish = setOf(MAVEN_CENTRAL)

    kotlinLibrary()
}

recipe {
    crafter {
        mavensOnly()
    }
}
