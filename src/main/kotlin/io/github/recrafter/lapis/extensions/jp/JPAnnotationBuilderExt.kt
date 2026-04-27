package io.github.recrafter.lapis.extensions.jp

import io.github.recrafter.lapis.phases.generator.builders.Builder
import io.github.recrafter.lapis.phases.generator.builders.IrJavaCodeBlock
import io.github.recrafter.lapis.phases.lowering.types.IrClassName
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

fun <A : Annotation> JPAnnotationBuilder.setArgumentValue(property: KProperty1<A, Boolean>, boolean: Boolean) {
    addMember(property.name, buildJavaCodeBlock("%L") { arg(boolean) })
}

fun <A : Annotation> JPAnnotationBuilder.setArgumentValue(property: KProperty1<A, Int>, int: Int) {
    addMember(property.name, buildJavaCodeBlock("%L") { arg(int) })
}

fun <A : Annotation> JPAnnotationBuilder.setArgumentValue(property: KProperty1<A, String>, string: String) {
    addMember(property.name, buildJavaCodeBlock("%S") { arg(string) })
}

fun <A : Annotation> JPAnnotationBuilder.setArgumentValue(property: KProperty1<A, KClass<*>>, className: IrClassName) {
    addMember(property.name, buildJavaCodeBlock("%T.class") { arg(className) })
}

inline fun <reified A : Annotation, reified Embedded : Annotation> JPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Embedded>,
    crossinline builder: Builder<JPAnnotationBuilder> = {},
) {
    addMember(property.name, buildJavaCodeBlock("%L") { arg(buildJavaAnnotation<Embedded>(builder)) })
}

@JvmName("setStringArrayArgumentValue")
inline fun <reified A : Annotation> JPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Array<out String>>,
    vararg strings: String
) {
    setArrayArgumentValue(property, strings, "%S") {
        strings.forEach { arg(it) }
    }
}

@JvmName("setClassArrayArgumentValue")
inline fun <reified A : Annotation> JPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Array<out KClass<*>>>,
    vararg classNames: IrClassName
) {
    setArrayArgumentValue(property, classNames, "%T.class") {
        classNames.forEach { arg(it) }
    }
}

@JvmName("setAnnotationArrayArgumentValue")
inline fun <reified A : Annotation, reified Embedded : Annotation> JPAnnotationBuilder.setArgumentValue(
    property: KProperty1<A, Array<out Embedded>>,
    crossinline builder: Builder<JPAnnotationBuilder> = {},
) {
    val annotations = arrayOf(buildJavaAnnotation<Embedded>(builder))
    setArrayArgumentValue(property, annotations, "%L") {
        annotations.forEach { arg(it) }
    }
}

inline fun <reified A : Annotation> JPAnnotationBuilder.setArrayArgumentValue(
    property: KProperty1<A, *>,
    array: Array<*>,
    placeholder: String,
    noinline arguments: Builder<IrJavaCodeBlock.Arguments> = {}
) {
    addMember(
        property.name,
        buildJavaCodeBlock(array.joinToString(prefix = "{", postfix = "}") { placeholder }, arguments)
    )
}
