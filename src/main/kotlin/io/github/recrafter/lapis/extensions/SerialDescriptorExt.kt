package io.github.recrafter.lapis.extensions

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor

@OptIn(ExperimentalSerializationApi::class)
val SerialDescriptor.elements: List<DescriptorElement>
    get() = (0 until elementsCount).map { index ->
        DescriptorElement(
            name = getElementName(index),
            isOptional = isElementOptional(index),
            descriptor = getElementDescriptor(index),
        )
    }

class DescriptorElement(
    val name: String,
    val isOptional: Boolean,
    val descriptor: SerialDescriptor,
)
