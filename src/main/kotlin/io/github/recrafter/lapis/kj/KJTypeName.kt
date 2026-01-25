package io.github.recrafter.lapis.kj

import io.github.recrafter.lapis.extensions.common.unsafeLazy
import io.github.recrafter.lapis.extensions.jp.JPClassName
import io.github.recrafter.lapis.extensions.jp.JPParameterizedTypeName
import io.github.recrafter.lapis.extensions.jp.JPTypeName
import io.github.recrafter.lapis.extensions.kp.*

class KJTypeName(val kotlinVersion: KPTypeName, val shouldBox: Boolean = false) {

    val className: KJClassName? by lazy {
        when (kotlinVersion) {
            is KPClassName -> KJClassName(kotlinVersion.packageName, kotlinVersion.simpleNames.joinToString("."))
            else -> null
        }
    }

    val name: String by unsafeLazy {
        when (kotlinVersion) {
            is KPClassName -> kotlinVersion.simpleName
            is KPParameterizedTypeName -> kotlinVersion.rawType.asKJTypeName().name
            else -> error("Unsupported type: $kotlinVersion")
        }
    }

    val javaVersion: JPTypeName by unsafeLazy {
        when (kotlinVersion) {
            is KPClassName -> kotlinVersion.asJP(shouldBox)
            is KPParameterizedTypeName -> JPParameterizedTypeName.get(
                kotlinVersion.rawType.asJP(true) as JPClassName,
                *kotlinVersion.typeArguments.map { it.asKJTypeName().javaVersion }.toTypedArray()
            )

            else -> error("Unsupported type: $kotlinVersion")
        }
    }

    val boxed: KJTypeName by unsafeLazy {
        if (shouldBox) this
        else KJTypeName(kotlinVersion, true)
    }

    val unboxed: KJTypeName by unsafeLazy {
        if (shouldBox) KJTypeName(kotlinVersion, false)
        else this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is KJTypeName) {
            return false
        }
        return kotlinVersion == other.kotlinVersion
    }

    override fun hashCode(): Int =
        kotlinVersion.hashCode()
}
