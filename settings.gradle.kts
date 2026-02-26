import io.github.diskria.projektor.common.licenses.LicenseType.MIT
import io.github.diskria.projektor.common.publishing.PublishingTargetType.MAVEN_CENTRAL

pluginManagement {
    repositories {
        maven("https://diskria.github.io/projektor")
        maven("https://recrafter.github.io/recipe")
        gradlePluginPortal()
    }
}

plugins {
    id("io.github.diskria.projektor.settings") version "5.+"
    id("io.github.recrafter.recipe") version "1.0.6"
}

projekt {
    version = "0.6.1"
    license = MIT
    publish = setOf(MAVEN_CENTRAL)

    kotlinLibrary()
}

recipe {
    crafter {
        craftingCrafters()
    }
}
