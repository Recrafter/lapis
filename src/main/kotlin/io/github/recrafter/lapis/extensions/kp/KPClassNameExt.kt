package io.github.recrafter.lapis.extensions.kp

import io.github.recrafter.lapis.extensions.jp.*
import io.github.recrafter.lapis.kj.KJClassName

fun KPClassName.asKJClassName(): KJClassName =
    KJClassName(packageName, *simpleNames.toTypedArray())

fun KPClassName.asJP(shouldBox: Boolean): JPTypeName =
    when (this) {
        KPAny -> JPObject
        KPUnit -> JPVoid.boxIfPrimitive(shouldBox || isNullable)
        KPBoolean -> JPBoolean.boxIfPrimitive(shouldBox || isNullable)
        KPByte -> JPByte.boxIfPrimitive(shouldBox || isNullable)
        KPShort -> JPShort.boxIfPrimitive(shouldBox || isNullable)
        KPInt -> JPInt.boxIfPrimitive(shouldBox || isNullable)
        KPLong -> JPLong.boxIfPrimitive(shouldBox || isNullable)
        KPChar -> JPChar.boxIfPrimitive(shouldBox || isNullable)
        KPFloat -> JPFloat.boxIfPrimitive(shouldBox || isNullable)
        KPDouble -> JPDouble.boxIfPrimitive(shouldBox || isNullable)
        KPString -> JPString
        KPList -> JPList
        KPSet -> JPSet
        KPMap -> JPMap
        else -> asKJClassName().javaVersion
    }
